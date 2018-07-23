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

import java.util.concurrent.TimeUnit;

/**
 * Defines the API for interation with the redis server/cluster/etc
 *
 * @author Steve Ungerer
 */
public interface RedisSessionClient {
    
	/**
	 * Save the session to the given key.
	 * @param key
	 * @param session
	 */
	void save(String key, RedisSession session);
	
	/**
	 * Load the session defined by the given key.
	 * @param key
	 * @return the loaded {@link RedisSession} or <code>null</code> if no such key exists
	 */
	RedisSession load(String key);
	
	/**
	 * Delete the session defined by the given key.
	 * @param key
	 */
	void delete(String key);
	
	/**
	 * Update the expiration time for the session defined by the given key.
	 * @param key
	 * @param expirationTime
	 * @param timeUnit
	 */
	void expire(String key, long expirationTime, TimeUnit timeUnit);
	
	/**
	 * Check if a session with the given key exists in redis.
	 * @param key
	 * @return
	 */
	boolean exists(String key);
	
    /**
     * Get the encoded size of the given object
     * @param obj
     * @return
     */
    int getEncodedSize(Object obj);
	
	/**
	 * Perform any tasks necessary when shutting down
	 */
	void shutdown();
}
