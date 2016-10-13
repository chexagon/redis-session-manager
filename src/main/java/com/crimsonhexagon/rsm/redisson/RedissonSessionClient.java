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
import java.io.ObjectInputStream;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.redisson.Config;
import org.redisson.Redisson;
import org.redisson.RedissonClient;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.codec.SerializationCodec;

import com.crimsonhexagon.rsm.RedisSession;
import com.crimsonhexagon.rsm.RedisSessionClient;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

/**
 * Redisson-backed {@link RedisSessionClient} for a single server configuration
 *
 * @author Steve Ungerer
 */
public class RedissonSessionClient implements RedisSessionClient {
	protected final Log log = LogFactory.getLog(getClass());
	
	private final RedissonClient redissonClient;
	
	public RedissonSessionClient(Config config, ClassLoader containerClassLoader) {
		config.setCodec(new ContextClassloaderSerializationCodec(containerClassLoader));
		this.redissonClient = Redisson.create(config);
	}
	
	@Override
	public void save(String key, RedisSession session) {
		redissonClient.getBucket(key).set(session);
		if (log.isTraceEnabled()) {
		    try {
		        byte[] size = redissonClient.getConfig().getCodec().getValueEncoder().encode(session);
		        log.trace(session.getId() + " size: " + size.length);
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

	/**
	 * Extension of {@link SerializationCodec} to use tomcat's {@link CustomObjectInputStream} with the {@link ClassLoader} provided 
	 */
	public static class ContextClassloaderSerializationCodec extends SerializationCodec {
		private final ClassLoader containerClassLoader;
		
		public ContextClassloaderSerializationCodec(ClassLoader containerClassLoader) {
			this.containerClassLoader = containerClassLoader;
		}
		
	    @Override
	    public Decoder<Object> getValueDecoder() {
	        return new Decoder<Object>() {
	            @Override
	            public Object decode(ByteBuf buf, State state) throws IOException {
	    	        try {
	    	            ByteBufInputStream in = new ByteBufInputStream(buf);
	    	            final ObjectInputStream ois;
	    	            if (containerClassLoader != null) {
	    	            	ois = new CustomObjectInputStream(in, containerClassLoader);
	    	            } else {
	    	            	ois = new ObjectInputStream(in);
	    	            }
	    	            return ois.readObject();
	    	        } catch (IOException io) {
	    	        	throw io;
	    	        } catch (Exception e) {
	    	            throw new IOException(e);
	    	        }
	            }
	        };
	    }
	}

	@Override
	public void shutdown() {
		redissonClient.shutdown();
	}
}
