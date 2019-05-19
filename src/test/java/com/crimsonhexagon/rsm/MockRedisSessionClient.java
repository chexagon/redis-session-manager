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

package com.crimsonhexagon.rsm;

import org.redisson.client.codec.Codec;
import org.redisson.codec.SerializationCodec;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MockRedisSessionClient implements RedisSessionClient {
    private Codec codec = new SerializationCodec();
    private ConcurrentHashMap<String, RedisSession> store = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Expiration> expirationTimes = new ConcurrentHashMap<>();

    @Override
    public void save(String key, RedisSession session) {
        store.put(key, session);
    }

    @Override
    public RedisSession load(String key) {
        return store.get(key);
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public void expire(String key, long expirationTime, TimeUnit timeUnit) {
        expirationTimes.put(key, new Expiration(expirationTime, timeUnit));
    }

    @Override
    public boolean exists(String key) {
        return store.containsKey(key);
    }

    public static class Expiration {
        public final long expirationTime;
        public final TimeUnit timeUnit;

        public Expiration(long expirationTime, TimeUnit timeUnit) {
            this.expirationTime = expirationTime;
            this.timeUnit = timeUnit;
        }
    }

    @Override
    public void shutdown() {
        // noop
    }

    @Override
    public int getEncodedSize(Object obj) {
        try {
            return codec.getValueEncoder().encode(obj).readableBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
