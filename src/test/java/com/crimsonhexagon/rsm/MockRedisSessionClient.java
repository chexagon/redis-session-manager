package com.crimsonhexagon.rsm;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MockRedisSessionClient implements RedisSessionClient {

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
		//noop
	}

}
