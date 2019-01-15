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

package com.crimsonhexagon.rsm.redisson;

import org.redisson.config.Config;

/**
 * Manager for a single server using redisson
 *
 * @author Steve Ungerer
 */
public class SingleServerSessionManager extends BaseRedissonSessionManager {
    public static final String DEFAULT_ENDPOINT = "redis://localhost:6379";
    public static final int DEFAULT_CONN_POOL_SIZE = 100;

    private String endpoint = DEFAULT_ENDPOINT;
    private int connectionPoolSize = DEFAULT_CONN_POOL_SIZE;

    @Override
    protected Config configure(Config config) {
        config.useSingleServer()
            .setAddress(getEndpoint())
            .setDatabase(database)
            .setConnectionPoolSize(connectionPoolSize)
            .setPassword(password)
            .setTimeout(timeout)
            .setPingTimeout(pingTimeout)
            .setRetryAttempts(retryAttempts)
            .setRetryInterval(retryInterval);
        return config;
    }

    /**
     * Set the redis endpoint (hostname:port)<br>
     * Defaults to {@value #DEFAULT_ENDPOINT}
     * 
     * @param endpoint
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public void setConnectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
    }
}
