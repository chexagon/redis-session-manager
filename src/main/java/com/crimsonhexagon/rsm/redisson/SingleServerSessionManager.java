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
