package com.crimsonhexagon.rsm.redisson;

import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.ReplicatedServersConfig;
import org.redisson.connection.balancer.LoadBalancer;
import org.redisson.connection.balancer.RoundRobinLoadBalancer;

/**
 * Manager for an AWS ElastiCache replication group
 *
 * @author Steve Ungerer
 */
public class ElasticacheSessionManager extends BaseRedissonSessionManager {
	public static final String DEFAULT_LOAD_BALANCER_CLASS = RoundRobinLoadBalancer.class.getName();
	public static final int DEFAULT_MASTER_CONN_POOL_SIZE = 100;
	public static final int DEFAULT_SLAVE_CONN_POOL_SIZE = 100;
	public static final int DEFAULT_NODE_POLL_INTERVAL = 1_000;
	
	private String nodes;
	private String loadBalancerClass = DEFAULT_LOAD_BALANCER_CLASS;
	private int masterConnectionPoolSize = DEFAULT_MASTER_CONN_POOL_SIZE;
	private int slaveConnectionPoolSize = DEFAULT_SLAVE_CONN_POOL_SIZE;
	private int nodePollInterval = DEFAULT_NODE_POLL_INTERVAL;
	
	@Override
	protected Config configure(Config config) {
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
		
		ReplicatedServersConfig ecCfg = config.useReplicatedServers();
		ecCfg
			.addNodeAddress(nodes.trim().split("\\s+"))
			.setDatabase(database)
			.setMasterConnectionPoolSize(masterConnectionPoolSize)
			.setSlaveConnectionPoolSize(slaveConnectionPoolSize)
			.setPassword(password)
			.setTimeout(timeout)
			.setReadMode(ReadMode.MASTER_SLAVE)
			.setPingTimeout(pingTimeout)
			.setRetryAttempts(retryAttempts)
			.setRetryInterval(retryInterval)
			.setScanInterval(nodePollInterval);
		if (lb != null) {
			ecCfg.setLoadBalancer(lb);
		}
		return config;
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

	public int getNodePollInterval() {
		return nodePollInterval;
	}

	public void setNodePollInterval(int nodePollInterval) {
		this.nodePollInterval = nodePollInterval;
	}
}
