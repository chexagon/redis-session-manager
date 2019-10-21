/*-
 *  Copyright 2015 Crimson Hexagon
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.crimsonhexagon.rsm.lettuce;

import com.crimsonhexagon.rsm.RedisSessionClient;
import com.crimsonhexagon.rsm.RedisSessionManager;

import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.masterreplica.MasterReplica;
import io.lettuce.core.masterreplica.StatefulRedisMasterReplicaConnection;
import io.lettuce.core.support.ConnectionPoolSupport;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LettuceSessionManager extends RedisSessionManager {
    public static final String DEFAULT_URI = "redis://localhost:6379";
    public static final int DEFAULT_MAX_CONN_POOL_SIZE = 128;
    public static final int DEFAULT_MIN_CONN_POOL_SIZE = 4;

    protected final Log log = LogFactory.getLog(getClass());

    private final RedisClient client = RedisClient.create();
    private GenericObjectPool<StatefulRedisConnection<String, Object>> pool;
    private String nodes = DEFAULT_URI;
    private int maxConnPoolSize = DEFAULT_MAX_CONN_POOL_SIZE;
    private int minConnPoolSize = DEFAULT_MIN_CONN_POOL_SIZE;

    @Override
    protected final RedisSessionClient buildClient() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        if (nodes == null || nodes.trim().length() == 0) {
            throw new IllegalStateException("Manager must specify node string. e.g., nodes=\"redis://node1.com:6379 redis://node2.com:6379\"");
        }
        RedisCodec<String, Object> codec = new ContextClassloaderJdkSerializationCodec(getContainerClassLoader());
        List<String> nodes = Arrays.asList(getNodes().trim().split("\\s+"));
        this.pool = createPool(nodes, codec);
        return new LettuceSessionClient(pool, codec);
    }

    private GenericObjectPool<StatefulRedisConnection<String, Object>> createPool(List<String> nodes, RedisCodec<String, Object> codec) {
        GenericObjectPoolConfig<StatefulRedisConnection<String, Object>> cfg = new GenericObjectPoolConfig<>();
        cfg.setTestOnBorrow(true);
        cfg.setMinEvictableIdleTimeMillis(TimeUnit.MINUTES.toMillis(5));
        cfg.setMaxTotal(getMaxConnPoolSize());
        cfg.setMinIdle(getMinConnPoolSize());

        if (nodes.size() == 1) {
            return ConnectionPoolSupport.createGenericObjectPool(
                () -> client.connect(codec, RedisURI.create(nodes.get(0))),
                cfg
            );
        } else {
            List<RedisURI> uris = nodes.stream()
                .map(RedisURI::create)
                .collect(Collectors.toList());
            return ConnectionPoolSupport.createGenericObjectPool(
                () -> {
                    StatefulRedisMasterReplicaConnection<String, Object> connection =
                        MasterReplica.connect(client, codec, uris);
                    connection.setReadFrom(ReadFrom.MASTER_PREFERRED);
                    return connection;
                },
                cfg
            );
        }
    }

    @Override
    public void unload() throws IOException {
        if (pool != null) {
            pool.close();
        }
        client.shutdown();
    }

    public String getNodes() {
        return nodes;
    }

    public void setNodes(String nodes) {
        this.nodes = nodes;
    }

    public int getMaxConnPoolSize() {
        return maxConnPoolSize;
    }

    public void setMaxConnPoolSize(int maxConnPoolSize) {
        this.maxConnPoolSize = maxConnPoolSize;
    }

    public int getMinConnPoolSize() {
        return minConnPoolSize;
    }

    public void setMinConnPoolSize(int minConnPoolSize) {
        this.minConnPoolSize = minConnPoolSize;
    }
}
