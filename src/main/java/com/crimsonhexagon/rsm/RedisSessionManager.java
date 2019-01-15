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

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.session.ManagerBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Manages {@link RedisSession}s
 * 
 * @author Steve Ungerer
 */
public abstract class RedisSessionManager extends ManagerBase {
    protected final Log log = LogFactory.getLog(this.getClass());

    /**
     * Default value to prefix redis keys with.
     */
    public static final String DEFAULT_SESSION_KEY_PREFIX = "_rsm_";

    static final int DO_NOT_CHECK = -1;

    private int sessionExpirationTime; // in minutes
    private RedisSessionClient _client; // access should be done via #getClient()
    private String sessionKeyPrefix = DEFAULT_SESSION_KEY_PREFIX;
    private String ignorePattern = RedisSessionRequestValve.DEFAULT_IGNORE_PATTERN;
    private boolean saveOnChange;
    private boolean forceSaveAfterRequest;
    private boolean dirtyOnMutation;
    private int maxSessionAttributeSize = DO_NOT_CHECK;
    private int maxSessionSize = DO_NOT_CHECK;
    private boolean allowOversizedSessions;

    private ThreadLocal<RedisSessionState> currentSessionState = InheritableThreadLocal.withInitial(RedisSessionState::new);

    private RedisSessionRequestValve requestValve;

    protected RedisSessionClient getClient() {
        return _client;
    }

    /**
     * Construct the {@link RedisSessionClient}
     * 
     * @return
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    protected abstract RedisSessionClient buildClient() throws ClassNotFoundException, InstantiationException, IllegalAccessException;

    /**
     * Get the encoded size of the object
     * 
     * @param obj
     * @return
     */
    int getEncodedSize(Object obj) {
        return getClient().getEncodedSize(obj);
    }

    /**
     * Should the {@link RedisSession} be saved immediately when an attribute changes
     * 
     * @return
     */
    public boolean isSaveOnChange() {
        return saveOnChange;
    }

    /**
     * Should the {@link RedisSession} be saved on completion of every request regardless of {@link RedisSession#isDirty()}
     * 
     * @return
     */
    public boolean isForceSaveAfterRequest() {
        return forceSaveAfterRequest;
    }

    /**
     * Should any attribute mutation result in marking the session as dirty
     * 
     * @return
     */
    public boolean isDirtyOnMutation() {
        return dirtyOnMutation;
    }

    /**
     * Obtain the {@link ClassLoader} for this context. Necessary for deserialization of {@link RedisSession}s
     * 
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
            this._client = buildClient();
        } catch (Throwable t) {
            log.fatal("Unable to load serializer", t);
            throw new LifecycleException(t);
        }

        this.requestValve = new RedisSessionRequestValve(this, ignorePattern);
        getContext().getParent().getPipeline().addValve(requestValve);
        this.sessionExpirationTime = getContext().getSessionTimeout();
        if (this.sessionExpirationTime < 0) {
            log.warn("Ignoring negative session expiration time");
            this.sessionExpirationTime = 0;
        }
        log.info("Will expire sessions after " + sessionExpirationTime + " minutes");
        setState(LifecycleState.STARTING);
    }

    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
        log.info("Stopping");
        getContext().getParent().getPipeline().removeValve(requestValve);
        getClient().shutdown();
        super.stopInternal();
    }

    @Override
    public Session createSession(String requestedSessionId) {
        RedisSession session = createEmptySession();
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(sessionExpirationTime * 60);
        session.setId(requestedSessionId == null ? generateSessionId() : requestedSessionId);
        session.tellNew();
        currentSessionState.set(new RedisSessionState(session, false)); // persisted will be set to true in save()
        save(session, true);
        return session;
    }

    @Override
    protected void changeSessionId(Session session, String newId, boolean notifySessionListeners, boolean notifyContainerListeners) {
        final String oldId = session.getId();
        super.changeSessionId(session, newId, notifySessionListeners, notifyContainerListeners);
        if (RedisSession.class.isAssignableFrom(session.getClass())) {
            final RedisSession rSession = RedisSession.class.cast(session);
            currentSessionState.set(new RedisSessionState(rSession, false));
            getClient().delete(generateRedisSessionKey(oldId));
            save(rSession, true);
        } else {
            throw new UnsupportedOperationException("Could not change a session ID with class " + session.getClass());
        }
    }

    @Override
    protected String generateSessionId() {
        String sessionId = null;
        while (sessionId == null) {
            sessionId = prefixJvmRoute(super.generateSessionId());
            if (getClient().exists(generateRedisSessionKey(sessionId))) {
                log.debug("Rejecting duplicate sessionId: " + sessionId);
                sessionId = null;
            } else {
                log.debug("Generated new sessionId: " + sessionId);
            }
        }
        return sessionId;
    }

    /**
     * Generate the storage key for the given sessionId
     * 
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

    /**
     * Prefix the given sessionId with the JVM Route
     * 
     * @param sessionId
     * @return
     */
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
            try {
                session = getClient().load(generateRedisSessionKey(id));
            } catch (Throwable t) {
                log.error("Failed to load session [" + id + "] from redis", t);
            }
            if (session != null) {
                log.debug("Found session " + id + " in redis");
                session.postDeserialization(this);
                session.setNew(false); // Fix issue #12
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

        if (currentSessionState.get().session == null) {
            currentSessionState.set(new RedisSessionState(redisSession, false));
        }

        final boolean currentSessionPersisted = currentSessionState.get().persisted;
        final String sessionKey = generateRedisSessionKey(redisSession.getId());
        if (forceSave
            || redisSession.isDirty()
            || !currentSessionPersisted) {
            if (getMaxSessionSize() != DO_NOT_CHECK) {
                final int size = getEncodedSize(redisSession);
                if (size > getMaxSessionSize()) {
                    if (!isAllowOversizedSessions()) {
                        log.error("Not saving [" + redisSession.getId() + "] to redis. Size of [" + size + "] exceeds max of [" + getMaxSessionSize() + "]");
                        return;
                    } else {
                        log.error("Session [" + redisSession.getId() + "] size of [" + size + "] exceeds max of [" + getMaxSessionSize() + "]; still saving");
                    }
                }
            }
            log.debug("Saving " + redisSession.getId() + " to redis");
            try {
                getClient().save(sessionKey, redisSession);
            } catch (Throwable t) {
                log.error("Failed to save session [" + redisSession.getId() + "]", t);
            }
            redisSession.clearDirty();
            currentSessionState.get().markPersisted();
        } else {
            log.debug("Not saving " + redisSession.getId() + " to redis");
        }

        log.trace("Setting expire on " + redisSession.getId() + " to " + sessionExpirationTime);
        getClient().expire(sessionKey, sessionExpirationTime, TimeUnit.MINUTES);
    }

    @Override
    public void remove(Session session, boolean update) {
        log.debug("Removing session ID : " + session.getId());
        try {
            getClient().delete(generateRedisSessionKey(session.getId()));
        } catch (Throwable t) {
            log.error("Failed to remove session [" + session.getId() + "]", t);
        }
        currentSessionState.remove();
    }

    /**
     * Handle post-request actions. Invoked from {@link RedisSessionRequestValve}
     */
    public void afterRequest(boolean requestProcessed) {
        try {
            RedisSession session = currentSessionState.get().session;
            if (log.isTraceEnabled()) {
                log.trace("afterRequest for " + (session == null ? "null" : session.getId()) + "; processed: " + requestProcessed);
            }
            if (session != null) {
                if (session.isValid()) {
                    log.trace("Request with session completed, saving session " + session.getId());
                    save(session, isForceSaveAfterRequest());
                } else {
                    log.debug("HTTP Session has been invalidated, removing :" + session.getId());
                    remove(session);
                }
            }
        } catch (Exception e) {
            log.error("Error storing/removing session", e);
        } finally {
            currentSessionState.remove();
        }
    }

    @Override
    public void processExpires() {
        // Redis will handle expiration
    }

    /**
     * Define the prefix for all redis keys.<br>
     * Defaults to {@value #DEFAULT_SESSION_KEY_PREFIX}
     * 
     * @param sessionKeyPrefix
     */
    public void setSessionKeyPrefix(String sessionKeyPrefix) {
        this.sessionKeyPrefix = sessionKeyPrefix;
    }

    /**
     * If <code>true</code> the session will be persisted to redis immediately when any attribute is modified.<br>
     * Default is <code>false</code> which persists a modified session when the request is complete.
     * 
     * @param saveOnChange
     */
    public void setSaveOnChange(boolean saveOnChange) {
        this.saveOnChange = saveOnChange;
    }

    /**
     * If <code>true</code> the session will always be persisted to redis after a request completes regardless of {@link RedisSession#isDirty()}.<br>
     * Default is <code>false</code> which persists a session after a request only if {@link RedisSession#isDirty()} is <code>true</code>
     * 
     * @param forceSaveAfterRequest
     */
    public void setForceSaveAfterRequest(boolean forceSaveAfterRequest) {
        this.forceSaveAfterRequest = forceSaveAfterRequest;
    }

    /**
     * If <code>true</true> the session will be marked as dirty on any mutation. When <code>false</code> the attribute is
     * checked for equality against the old value and the session is marked as dirty only if they differ.<br>
     * When <code>false</code> the following code would <em>not</em> mark the session as dirty:
     * <br>
     * 
     * <pre>
     * List<String> stringList = (List<String>) session.getAttribute("myList");
     * stringList.add("another value");
     * session.setAttribute("myList", stringList);
     * </pre>
     * 
     * <br>
     * Because the list is mutated, the 'old' value is identical to the 'new' value when equality is checked. It is a better
     * design to ensure session objects are immutable, but setting <code>dirtyOnMutation</code> to <code>true</code> will
     * workaround this with minimal overhead.
     * 
     * @param dirtyOnMutation
     */
    public void setDirtyOnMutation(boolean dirtyOnMutation) {
        this.dirtyOnMutation = dirtyOnMutation;
    }

    /**
     * Set a pattern (must adhere to {@link Pattern} specs) for requests to ignore.
     * This pattern is matched <em>case-insensitive</em> against {@link Request#getRequestURI()}.<br>
     * Defaults to {@value #DEFAULT_IGNORE_PATTERN}
     * 
     * @param ignorePattern
     */
    public void setIgnorePattern(String ignorePattern) {
        this.ignorePattern = ignorePattern;
    }

    /**
     * Set a maximum size, in bytes, of each attribute within a session. If an attribute exceeds this size
     * it will not be stored in the session.<br>
     * Performance note: values will be encoded twice, once for size checking and once for actual storage.
     * 
     * @param maxSessionAttributeSize
     */
    public void setMaxSessionAttributeSize(int maxSessionAttributeSize) {
        this.maxSessionAttributeSize = maxSessionAttributeSize;
    }

    int getMaxSessionAttributeSize() {
        return maxSessionAttributeSize;
    }

    /**
     * Set a maximum size, in bytes, of the entire serialized session in redis. If the session exceeds this size
     * it will not be saved to redis.<br>
     * Performance note: sessions will be encoded twice, once for size checking and once for actual storage.
     * 
     * @param maxSessionSize
     */
    public void setMaxSessionSize(int maxSessionSize) {
        this.maxSessionSize = maxSessionSize;
    }

    int getMaxSessionSize() {
        return maxSessionSize;
    }

    /**
     * When {@link #setMaxSessionAttributeSize(int)} or {@link #setMaxSessionSize(int)} is used, the default
     * behavior is to prevent sessions with attributes or total size exceeding the configured value.
     * If {@link #allowOversizedSessions} is <code>true</code> then these exceptional attrs/sessions will
     * only be logged and will still be saved to redis.
     * 
     * @param allowAndLogSessionSizeErrors
     */
    public void setAllowOversizedSessions(boolean allowAndLogSessionSizeErrors) {
        this.allowOversizedSessions = allowAndLogSessionSizeErrors;
    }

    boolean isAllowOversizedSessions() {
        return allowOversizedSessions;
    }

    /**
     * Get the current {@link RedisSessionState}
     * 
     * @return
     */
    RedisSessionState getCurrentState() {
        return currentSessionState.get();
    }

    /**
     * Set the current {@link RedisSessionState}; intended for testing
     * 
     * @param state
     */
    void setCurrentState(RedisSessionState state) {
        currentSessionState.set(state);
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

        @Override
        public String toString() {
            return "sessionId: [" + sessionId + "]; persisted = [" + persisted + "]";
        }
    }
}
