package com.crimsonhexagon.rsm.redisson;

import java.io.IOException;
import java.io.ObjectInputStream;

import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.codec.SerializationCodec;
import org.redisson.config.Config;
import org.redisson.config.TransportMode;

import com.crimsonhexagon.rsm.RedisSessionClient;
import com.crimsonhexagon.rsm.RedisSessionManager;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.epoll.Epoll;

/**
 * Base class for Redisson-backed session manager
 *
 * @author Steve Ungerer
 */
public abstract class BaseRedissonSessionManager extends RedisSessionManager {
	protected final Log log = LogFactory.getLog(getClass());
	
	public static final int DEFAULT_DATABASE = 0;
	public static final String DEFAULT_PASSWORD = null;
	public static final int DEFAULT_TIMEOUT = 60_000;
	public static final int DEFAULT_PING_TIMEOUT = 1_000;
	public static final int DEFAULT_RETRY_ATTEMPTS = 20;
	public static final int DEFAULT_RETRY_INTERVAL = 1_000;

	protected int database = DEFAULT_DATABASE;
	protected String password = DEFAULT_PASSWORD;
	protected int timeout = DEFAULT_TIMEOUT;
	protected int pingTimeout = DEFAULT_PING_TIMEOUT;
	protected int retryAttempts = DEFAULT_RETRY_ATTEMPTS;
	protected int retryInterval = DEFAULT_RETRY_INTERVAL;
	
	@Override
	protected final RedisSessionClient buildClient() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		Config config = new Config()
			.setCodec(new ContextClassloaderSerializationCodec(getContainerClassLoader()))
			.setTransportMode(isEpollSupported() ? TransportMode.EPOLL : TransportMode.NIO);
		return new RedissonSessionClient(configure(config));
	}

	/**
	 * Perform appropriate configuration of the Redisson {@link Config}
	 * @param config
	 * @return
	 */
	protected abstract Config configure(Config config);
	
	/**
	 * Determine if native Epoll for netty is available
	 * @return
	 */
	protected boolean isEpollSupported() {
		final boolean available = Epoll.isAvailable();
		if (available) {
			log.info("Using native epoll");
		}
		return available;
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
}
