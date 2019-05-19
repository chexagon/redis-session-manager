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
import io.lettuce.core.masterslave.MasterSlave;
import io.lettuce.core.masterslave.StatefulRedisMasterSlaveConnection;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LettuceSessionManager extends RedisSessionManager {
    public static final String DEFAULT_URI = "redis://localhost:6379";
    
    protected final Log log = LogFactory.getLog(getClass());
    
    private final RedisClient client = RedisClient.create();
    protected RedisCodec<String, Object> codec;
    private String nodes = DEFAULT_URI;
    
    @Override
    protected final RedisSessionClient buildClient() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        if (nodes == null || nodes.trim().length() == 0) {
            throw new IllegalStateException("Manager must specify node string. e.g., nodes=\"redis://node1.com:6379 redis://node2.com:6379\"");
        }
        this.codec = new ContextClassloaderJdkSerializationCodec(getContainerClassLoader());
        String[] nodes = getNodes().trim().split("\\s+");
        List<RedisURI> uris = new ArrayList<>();
        for (String node : nodes) {
            log.info("Adding '" + node + "' to node list");
            uris.add(RedisURI.create(node.trim()));
        }
        if (uris.size() == 1) {
            final StatefulRedisConnection<String, Object> conn = client.connect(codec, uris.get(0));
            return new LettuceSessionClient(conn, codec);
        } else {
            StatefulRedisMasterSlaveConnection<String, Object> ms = MasterSlave.connect(client, codec, uris);
            ms.setReadFrom(ReadFrom.MASTER_PREFERRED);
            return new LettuceSessionClient(ms, codec);
        }
    }

    @Override
    public void unload() throws IOException {
        client.shutdown();
    }

    public String getNodes() {
        return nodes;
    }

    public void setNodes(String nodes) {
        this.nodes = nodes;
    }

}
