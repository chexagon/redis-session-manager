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

import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.session.ManagerBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import com.crimsonhexagon.rsm.redisson.RedissonSessionClient;

/**
 * Manages {@link RedisSession}s
 * 
 * @author Steve Ungerer
 */
public class RedisSessionManager extends ManagerBase {
	private static final Log log = LogFactory.getLog(RedisSessionManager.class);

	/**
	 * Default value to prefix redis keys with.
	 */
	public static final String DEFAULT_SESSION_KEY_PREFIX="_rsm_";
	/**
	 * Default client to use for redis communication
	 */
	public static final String DEFAULT_CLIENT_CLASSNAME = RedissonSessionClient.class.getName(); 
	/**
	 * Default pattern for requests to ignore
	 */
	public static final String DEFAULT_IGNORE_PATTERN = ".*\\.(ico|png|gif|jpg|jpeg|swf|css|js)$";
	
	public static final String DEFAULT_ENDPOINT = "localhost:6379";
	
	private RedisSessionClient client;
	private String clientClassName = DEFAULT_CLIENT_CLASSNAME;
	private String sessionKeyPrefix = DEFAULT_SESSION_KEY_PREFIX;
	private String ignorePattern = DEFAULT_IGNORE_PATTERN;
	private String endpoint = DEFAULT_ENDPOINT;
	private boolean saveOnChange;
	private boolean forceSaveAfterRequest;
	
	private ThreadLocal<RedisSessionState> currentSessionState = ThreadLocal.withInitial(RedisSessionState::new);
	
	private RedisSessionRequestValve requestValve;

	/**
	 * Should the {@link RedisSession} be saved immediately when an attribute changes
	 * @return
	 */
	public boolean isSaveOnChange() {
		return saveOnChange;
	}

	/**
	 * Should the {@link RedisSession} be saved on completion of every request regardless of {@link RedisSession#isDirty()}
	 * @return
	 */
	public boolean isForceSaveAfterRequest() {
		return forceSaveAfterRequest;
	}

	/**
	 * Obtain the {@link ClassLoader} for this context. Necessary for deserialization of {@link RedisSession}s
	 * @return
	 */
	public ClassLoader getContainerClassLoader() {
		return getContext().getLoader().getClassLoader();
	}
	
	@Override
	public void load() throws ClassNotFoundException, IOException {
		// noop
	}

	@Override
	public void unload() throws IOException {
		// noop
	}

	@Override
	protected synchronized void startInternal() throws LifecycleException {
		super.startInternal();

		try {
			initializeClient();
		} catch (Throwable t) {
			log.fatal("Unable to load serializer", t);
			throw new LifecycleException(t);
		}

		this.requestValve = new RedisSessionRequestValve(this, ignorePattern);
		getContext().getParent().getPipeline().addValve(requestValve);
        getContext().getPipeline().addValve(requestValve);

		log.info("Will expire sessions after " + getMaxInactiveInterval() + " seconds");
		setDistributable(true);
		setState(LifecycleState.STARTING);
	}
	
	/**
	 * Initialize the {@link RedisSessionClient}
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	protected void initializeClient() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		log.info("Using client class: " + clientClassName);
		Class<?> clientClass = Class.forName(clientClassName);
		if (!RedisSessionClient.class.isAssignableFrom(clientClass)) {
		    throw new IllegalArgumentException("Class " + clientClassName +" is not a RedisSessionClient");
		}
		this.client = RedisSessionClient.class.cast(clientClass.newInstance());
		this.client.initialize(this);
	}


	@Override
	protected synchronized void stopInternal() throws LifecycleException {
		setState(LifecycleState.STOPPING);
		log.info("Stopping");
		getContext().getParent().getPipeline().removeValve(requestValve);
		getContext().getPipeline().removeValve(requestValve);
		super.stopInternal();
	}

	@Override
	public Session createSession(String requestedSessionId) {

		String sessionId = null;
		while (sessionId == null) {
			if (requestedSessionId == null || requestedSessionId.trim().length() == 0) {
				requestedSessionId = generateSessionId();
			}
			sessionId = prefixJvmRoute(requestedSessionId);
			if (client.exists(generateRedisSessionKey(sessionId))) {
				log.debug("Rejecting duplicate sessionId: " + sessionId);
				sessionId = null;
			} else {
				log.debug("Generated new sessionId: " + sessionId);
			}
		}
		
		RedisSession session = createEmptySession();
		session.setNew(true);
		session.setValid(true);
		session.setCreationTime(System.currentTimeMillis());
		session.setMaxInactiveInterval(getMaxInactiveInterval());
		session.setId(sessionId);
		session.tellNew();
		currentSessionState.set(new RedisSessionState(session, false)); // persisted will be set to true in save()
		save(session, true);
		return session;
	}

	/**
	 * Adds the jvmRoute to the given sessionId
	 * @param sessionId
	 * @return
	 */
	private String generateRedisSessionKey(final String sessionId) {
		if (sessionId == null) {
			throw new IllegalArgumentException("sessionId must not be null");
		}
		String sessionKey = sessionId;
		if (!sessionKey.startsWith(sessionKeyPrefix)) {
			sessionKey = sessionKeyPrefix + sessionKey;
		}
		return sessionKey;
	}

	private String prefixJvmRoute(String sessionId) {
		String jvmRoute = getJvmRoute();
		if (jvmRoute != null) {
			String jvmRoutePrefix = '.' + jvmRoute;
			return sessionId.endsWith(jvmRoutePrefix) ? sessionId : sessionId + jvmRoutePrefix;
		} else {
			return sessionId;
		}
	}
	
	@Override
	public RedisSession createEmptySession() {
		return new RedisSession(this);
	}

	@Override
	public void add(Session session) {
		if (RedisSession.class.isAssignableFrom(session.getClass())) {
			save(RedisSession.class.cast(session), false);
		} else {
			throw new UnsupportedOperationException("Could not add a session with class " + session.getClass());
		}
	}

	@Override
	public Session findSession(String id) throws IOException {
		RedisSession session = null;
		log.debug("Finding session " + id);
		if (id == null) {
			currentSessionState.remove();
		} else if (id.equals(currentSessionState.get().sessionId)) {
			log.debug("CurrentSession found for " + id);
			session = currentSessionState.get().session;
		} else {
			log.debug("Loading from redis");
			session = client.load(generateRedisSessionKey(id));
			if (session != null) {
				log.debug("Found session " + id + " in redis");
				session.postDeserialization(this);
				currentSessionState.set(new RedisSessionState(session, true));
			} else {
				log.debug("Session " + id + " not found in redis");
				currentSessionState.remove();
			}
		}

		return session;
	}

	public void save(RedisSession redisSession, boolean forceSave) {
		log.debug("Checking if session " + redisSession.getId() + " needs to be saved in redis");

		if (log.isTraceEnabled()) {
			log.trace("Session Contents [" + redisSession.getId() + "]:");
			Enumeration<String> en = redisSession.getAttributeNames();
			while (en.hasMoreElements()) {
				String e = en.nextElement();
				log.trace("  " + e + ": " + String.valueOf(redisSession.getAttribute(e)));
			}
		}

		final boolean currentSessionPersisted = currentSessionState.get().persisted;
		final String sessionKey = generateRedisSessionKey(redisSession.getId());
		if (forceSave
			|| redisSession.isDirty()
			|| !currentSessionPersisted
		) {
			log.debug("Saving " + redisSession.getId() + " to redis");
			client.save(sessionKey, redisSession);
			redisSession.clearDirty();
			currentSessionState.get().markPersisted();
		} else {
			log.debug("Not saving " + redisSession.getId() + " to redis");
		}

		log.trace("Setting expire on " + redisSession.getId() + " to " + getMaxInactiveInterval());
		client.expire(sessionKey, getMaxInactiveInterval(), TimeUnit.SECONDS);
	}

	@Override
	public void remove(Session session, boolean update) {
		log.debug("Removing session ID : " + session.getId());
		client.delete(generateRedisSessionKey(session.getId()));
		currentSessionState.remove();
	}

	/**
	 * 
	 */
	public void afterRequest() {
		RedisSession session = currentSessionState.get().session;
		if (session != null) {
			try {
				if (session.isValid()) {
					log.trace("Request with session completed, saving session " + session.getId());
					save(session, isForceSaveAfterRequest());
				} else {
					log.debug("HTTP Session has been invalidated, removing :" + session.getId());
					remove(session);
				}
			} catch (Exception e) {
				log.error("Error storing/removing session", e);
			} finally {
				currentSessionState.remove();
				log.trace("Session removed from ThreadLocal :" + session.getIdInternal());
			}
		}
	}

	@Override
	public void processExpires() {
		// Redis will handle expiration
	}

    /**
     * Define the fully qualified class name of the {@link RedisSessionClient} to use.
     * Defaults to {@value #DEFAULT_CLIENT_CLASSNAME}
     * @param clientClassName 
     */
    public void setClientClassName(String clientClassName) {
        this.clientClassName = clientClassName;
    }

	/**
	 * Define the prefix for all redis keys.<br>
	 * Defaults to {@value #DEFAULT_SESSION_KEY_PREFIX}
	 * @param sessionKeyPrefix
	 */
	public void setSessionKeyPrefix(String sessionKeyPrefix) {
        this.sessionKeyPrefix = sessionKeyPrefix;
    }

    /**
     * If <code>true</code> the session will be persisted to redis immediately when any attribute is modified.<br>
     * Default is <code>false</code> which persists a modified session when the request is complete.
     * @param saveOnChange
     */
    public void setSaveOnChange(boolean saveOnChange) {
        this.saveOnChange = saveOnChange;
    }

    /**
     * If <code>true</code> the session will always be persisted to redis after a request completes regardless of {@link RedisSession#isDirty()}.<br>
     * Default is <code>false</code> which persists a session after a request only if {@link RedisSession#isDirty()} is <code>true</code>
     * @param forceSaveAfterRequest
     */
    public void setForceSaveAfterRequest(boolean forceSaveAfterRequest) {
        this.forceSaveAfterRequest = forceSaveAfterRequest;
    }
    
    /**
     * Set a pattern (must adhere to {@link Pattern} specs) for requests to ignore.<br>
     * Defaults to {@value #DEFAULT_IGNORE_PATTERN}
     * @param ignorePattern
     */
    public void setIgnorePattern(String ignorePattern) {
		this.ignorePattern = ignorePattern;
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

	/**
	 * Encapsulates metadata about a {@link RedisSession}
	 */
	class RedisSessionState {
    	final String sessionId;
    	final RedisSession session;
    	boolean persisted;
    	
    	RedisSessionState() {
    		this.sessionId = null;
    		this.session = null;
    		this.persisted = false;
		}
    	
    	RedisSessionState(RedisSession session, boolean persisted) {
    		this.sessionId = session.getId();
    		this.session = session;
    		this.persisted = persisted;
    	}
    	
    	void markPersisted() {
    		// can't mark state as persisted if no session is set
    		if (this.session == null) {
    			throw new IllegalStateException("Can't mark a null session as persisted");
    		}
			this.persisted = true;
    	}
    }
}
