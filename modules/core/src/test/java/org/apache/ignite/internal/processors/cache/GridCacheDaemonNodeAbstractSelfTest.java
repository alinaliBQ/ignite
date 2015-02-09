/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache;

import org.apache.ignite.*;
import org.apache.ignite.cache.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.apache.ignite.testframework.junits.common.*;
import org.apache.ignite.transactions.*;

import java.util.*;

import static org.apache.ignite.cache.CacheAtomicityMode.*;
import static org.apache.ignite.cache.CacheDistributionMode.*;
import static org.apache.ignite.cache.CacheMode.*;
import static org.apache.ignite.transactions.IgniteTxConcurrency.*;
import static org.apache.ignite.transactions.IgniteTxIsolation.*;

/**
 * Test cache operations with daemon node.
 */
public abstract class GridCacheDaemonNodeAbstractSelfTest extends GridCommonAbstractTest {
    /** */
    private static TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** Daemon flag. */
    protected boolean daemon;

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        daemon = false;
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration c = super.getConfiguration(gridName);

        c.setDaemon(daemon);

        TcpDiscoverySpi disco = new TcpDiscoverySpi();

        disco.setIpFinder(ipFinder);

        c.setDiscoverySpi(disco);

        c.setRestEnabled(false);

        CacheConfiguration cc = defaultCacheConfiguration();

        cc.setCacheMode(cacheMode());
        cc.setAtomicityMode(TRANSACTIONAL);
        cc.setDistributionMode(NEAR_PARTITIONED);

        c.setCacheConfiguration(cc);

        return c;
    }

    /**
     * Returns cache mode specific for test.
     *
     * @return Cache configuration.
     */
    protected abstract CacheMode cacheMode();

    /**
     * @throws Exception If failed.
     */
    public void testImplicit() throws Exception {
        try {
            startGridsMultiThreaded(3);

            daemon = true;

            startGrid(4);

            IgniteCache<Integer, Integer> cache = grid(0).jcache(null);

            for (int i = 0; i < 30; i++)
                cache.put(i, i);

            Map<Integer, Integer> batch = new HashMap<>();

            for (int i = 30; i < 60; i++)
                batch.put(i, i);

            cache.putAll(batch);

            for (int i = 0; i < 60; i++)
                assertEquals(i, (int)cache.get(i));
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testExplicit() throws Exception {
        try {
            startGridsMultiThreaded(3);

            daemon = true;

            startGrid(4);

            IgniteCache<Integer, Integer> cache = grid(0).jcache(null);

            for (int i = 0; i < 30; i++) {
                try (IgniteTx tx = ignite(0).transactions().txStart(PESSIMISTIC, REPEATABLE_READ)) {
                    cache.put(i, i);

                    tx.commit();
                }
            }

            Map<Integer, Integer> batch = new HashMap<>();

            for (int i = 30; i < 60; i++)
                batch.put(i, i);

            try (IgniteTx tx = ignite(0).transactions().txStart(PESSIMISTIC, REPEATABLE_READ)) {
                cache.putAll(batch);
                tx.commit();
            }

            for (int i = 0; i < 60; i++)
                assertEquals(i, (int)cache.get(i));
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * Test mapKeyToNode() method for normal and daemon nodes.
     *
     * @throws Exception If failed.
     */
    public void testMapKeyToNode() throws Exception {
        try {
            // Start normal nodes.
            Ignite g1 = startGridsMultiThreaded(3);

            // Start daemon node.
            daemon = true;

            Ignite g2 = startGrid(4);

            for (long i = 0; i < Integer.MAX_VALUE; i = (i << 1) + 1) {
                ClusterNode n;

                // Call mapKeyToNode for normal node.
                assertNotNull(n = g1.cluster().mapKeyToNode(null, i));

                // Call mapKeyToNode for daemon node.
                if (cacheMode() == PARTITIONED)
                    assertEquals(n, g2.cluster().mapKeyToNode(null, i));
                else
                    assertNotNull(g2.cluster().mapKeyToNode(null, i));
            }
        }
        finally {
            stopAllGrids();
        }
    }
}
