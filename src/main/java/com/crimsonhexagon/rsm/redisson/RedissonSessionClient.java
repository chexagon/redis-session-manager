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

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.util.CustomObjectInputStream;
import org.redisson.Config;
import org.redisson.Redisson;
import org.redisson.codec.SerializationCodec;

import com.crimsonhexagon.rsm.RedisSession;
import com.crimsonhexagon.rsm.RedisSessionClient;
import com.crimsonhexagon.rsm.RedisSessionManager;

/**
 * Redisson-backed {@link RedisSessionClient}
 *
 * @author Steve Ungerer
 */
public class RedissonSessionClient implements RedisSessionClient {

	private Redisson redisson;
	private RedisSessionManager manager;
	
	@Override
	public void initialize(RedisSessionManager manager) {
        this.manager = manager;
		//FIXME
		Config config = new Config();
		config.useSingleServer().setAddress("127.0.0.1:6379");
		config.setCodec(new ContextClassloaderSerializationCodec());
	    this.redisson = Redisson.create(config);
    }
	
	
	@Override
	public void save(String key, RedisSession session) {
		redisson.getBucket(key).set(session);
	}

	@Override
	public RedisSession load(String key) {
		return RedisSession.class.cast(redisson.getBucket(key).get());
	}

	@Override
	public void delete(String key) {
		redisson.getBucket(key).delete();
	}

	@Override
	public void expire(String key, long expirationTime, TimeUnit timeUnit) {
		redisson.getBucket(key).expire(expirationTime, timeUnit);
	}

	@Override
	public boolean exists(String key) {
		return redisson.getBucket(key).exists();
	}

	/**
	 * Extension of {@link SerializationCodec} to use tomcat's {@link CustomObjectInputStream} with the {@link ClassLoader}
	 * provided by the {@link RedisSessionManager}
	 */
	public class ContextClassloaderSerializationCodec extends SerializationCodec {
	    @Override
	    public Object decodeKey(ByteBuffer bytes) {
	        return this.decode(bytes);
	    }

	    @Override
	    public Object decodeValue(ByteBuffer bytes) {
	        return this.decode(bytes);
	    }

	    private Object decode(ByteBuffer bytes) {
	        try {
	            ByteArrayInputStream in = new ByteArrayInputStream(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.limit());
	            final ObjectInputStream ois;
	            if (manager != null && manager.getContainerClassLoader() != null) {
	            	ois = new CustomObjectInputStream(in, manager.getContainerClassLoader());
	            } else {
	            	ois = new ObjectInputStream(in);
	            }
	            return ois.readObject();
	        } catch (Exception e) {
	            throw new IllegalStateException(e);
	        }
	    }
	}
	
}
