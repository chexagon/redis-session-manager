package com.crimsonhexagon.rsm.redisson;

import org.redisson.Config;
import org.redisson.ElasticacheServersConfig;
import org.redisson.connection.LoadBalancer;
import org.redisson.connection.RoundRobinLoadBalancer;

import com.crimsonhexagon.rsm.RedisSessionClient;
import com.crimsonhexagon.rsm.RedisSessionManager;

import io.netty.util.internal.StringUtil;

/**
 * Manager for an AWS ElastiCache replication group
 *
 * @author Steve Ungerer
 */
public class ElasticacheSessionManager extends RedisSessionManager {
	private String nodes;
	private String loadBalancerClass = RoundRobinLoadBalancer.class.getName();
	private int masterConnectionPoolSize = 100;
	private int slaveConnectionPoolSize = 100;
	private int database = 0;
	private String password = null;
	private int timeout = 60000;
	private int pingTimeout = 1000;
	private int retryAttempts = 20;
	private int retryInterval = 1000;
	private int nodePollInterval = 1000;
	
	/**
	 * Initialize the {@link RedisSessionClient}
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	@Override
	protected RedisSessionClient buildClient() {
		if (nodes == null || nodes.trim().length() == 0) {
			throw new IllegalStateException("Manager must specify node string. e.g., nodes=\"node1.com:6379 node2.com:6379\"");
		}
		LoadBalancer lb = null;
		if (loadBalancerClass != null && loadBalancerClass.trim().length() != 0) {
			try {
				lb = LoadBalancer.class.cast(Class.forName(loadBalancerClass).newInstance());
			} catch (Exception e) {
				log.error("Failed to instantiate LoadBalancer", e);
			}
		}
		
		Config config = new Config();
		ElasticacheServersConfig ecCfg = config.useElasticacheServers();
		ecCfg
			.addNodeAddress(StringUtil.split(nodes, ' '))
			.setDatabase(database)
			.setMasterConnectionPoolSize(masterConnectionPoolSize)
			.setSlaveConnectionPoolSize(slaveConnectionPoolSize)
			.setPassword(password)
			.setTimeout(timeout)
			.setPingTimeout(pingTimeout)
			.setRetryAttempts(retryAttempts)
			.setRetryInterval(retryInterval)
			.setScanInterval(nodePollInterval);
		if (lb != null) {
			ecCfg.setLoadBalancer(lb);
		}
		return new RedissonSessionClient(config, getContainerClassLoader());
	}

	public String getNodes() {
		return nodes;
	}

	public void setNodes(String nodes) {
		this.nodes = nodes;
	}

	public String getLoadBalancerClass() {
		return loadBalancerClass;
	}

	public void setLoadBalancerClass(String loadBalancerClass) {
		this.loadBalancerClass = loadBalancerClass;
	}

	public int getMasterConnectionPoolSize() {
		return masterConnectionPoolSize;
	}

	public void setMasterConnectionPoolSize(int masterConnectionPoolSize) {
		this.masterConnectionPoolSize = masterConnectionPoolSize;
	}

	public int getSlaveConnectionPoolSize() {
		return slaveConnectionPoolSize;
	}

	public void setSlaveConnectionPoolSize(int slaveConnectionPoolSize) {
		this.slaveConnectionPoolSize = slaveConnectionPoolSize;
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

	public int getNodePollInterval() {
		return nodePollInterval;
	}

	public void setNodePollInterval(int nodePollInterval) {
		this.nodePollInterval = nodePollInterval;
	}
}
