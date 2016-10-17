package com.crimsonhexagon.rsm.redisson;

import org.redisson.Config;
import org.redisson.codec.SerializationCodec;

import com.crimsonhexagon.rsm.RedisSessionClient;
import com.crimsonhexagon.rsm.RedisSessionManager;

/**
 * Manager for a single server using redisson
 *
 * @author Steve Ungerer
 */
public class SingleServerSessionManager extends RedisSessionManager {
	private String endpoint = "localhost:6379";
	private int connectionPoolSize = 100;
	private int database = 0;
	private String password = null;
	private int timeout = 60000;
	private int pingTimeout = 1000;
	private int retryAttempts = 20;
	private int retryInterval = 1000;
	
	/**
	 * Initialize the {@link RedisSessionClient}
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	@Override
	protected RedisSessionClient buildClient() {
		Config config = new Config()
			.setUseLinuxNativeEpoll(System.getProperty("os.name").startsWith("Linux"));
		config.setCodec(new SerializationCodec());
		config.useSingleServer()
			.setAddress(getEndpoint())
			.setDatabase(database)
			.setConnectionPoolSize(connectionPoolSize)
			.setPassword(password)
			.setTimeout(timeout)
			.setPingTimeout(pingTimeout)
			.setRetryAttempts(retryAttempts)
			.setRetryInterval(retryInterval);
			
		return new RedissonSessionClient(config, getContainerClassLoader());
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

	public int getDatabase() {
		return database;
	}

	public void setDatabase(int database) {
		this.database = database;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public int getPingTimeout() {
		return pingTimeout;
	}

	public void setPingTimeout(int pingTimeout) {
		this.pingTimeout = pingTimeout;
	}

	public int getRetryAttempts() {
		return retryAttempts;
	}

	public void setRetryAttempts(int retryAttempts) {
		this.retryAttempts = retryAttempts;
	}

	public int getRetryInterval() {
		return retryInterval;
	}

	public void setRetryInterval(int retryInterval) {
		this.retryInterval = retryInterval;
	}
	
	
}
