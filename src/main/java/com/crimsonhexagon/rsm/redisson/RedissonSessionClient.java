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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import com.crimsonhexagon.rsm.RedisSession;
import com.crimsonhexagon.rsm.RedisSessionClient;

/**
 * Redisson-backed {@link RedisSessionClient}
 *
 * @author Steve Ungerer
 */
public class RedissonSessionClient implements RedisSessionClient {
	protected final Log log = LogFactory.getLog(getClass());
	
	private final RedissonClient redissonClient;
	
	public RedissonSessionClient(Config config) {
		this.redissonClient = Redisson.create(config);
	}
	
	@Override
	public void save(String key, RedisSession session) {
		redissonClient.getBucket(key).set(session);
		if (log.isTraceEnabled()) {
		    try {
		        int size = getEncodedSize(session);
		        log.trace(session.getId() + " size: " + size);
		    } catch (Exception e) {
		        log.error("Failed to record session size", e);
		    }
		}
	}

	@Override
	public RedisSession load(String key) {
		Object obj = redissonClient.getBucket(key).get();
		if (obj != null) {
			if (RedisSession.class.isAssignableFrom(obj.getClass())) {
				return RedisSession.class.cast(obj);
			} else {
				log.warn("Incompatible session class found in redis for session [" + key + "]: " + obj.getClass());
				delete(key);
			}
		}
		return null;
	}

	@Override
	public void delete(String key) {
		redissonClient.getBucket(key).delete();
	}

	@Override
	public void expire(String key, long expirationTime, TimeUnit timeUnit) {
		redissonClient.getBucket(key).expire(expirationTime, timeUnit);
	}

	@Override
	public boolean exists(String key) {
		return redissonClient.getBucket(key).isExists();
	}

	@Override
    public int getEncodedSize(Object obj) {
	    try {
            return redissonClient.getConfig().getCodec().getValueEncoder().encode(obj).readableBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException(e); // redisson style
        }
    }

    @Override
	public void shutdown() {
		redissonClient.shutdown();
	}
}
