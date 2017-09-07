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

import org.apache.catalina.session.StandardSession;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.security.Principal;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extension of {@link StandardSession} to
 * <ol>
 * <li>track state changes to the session to determine if redis persistence is necessary</li>
 * <li>allow redis persistence immediately when an attribute changes if the {@link RedisSessionManager} is so configured</li>
 * </ol>
 * If application logic modifies an existing session attribute, e.g., <code>Foo.class.cast(session.getAttribute("Foo")).setBar("Bar");</code> the session
 * <em>will not</em> be marked dirty. Setting {@link RedisSessionManager#setForceSaveAfterRequest(boolean)} to <code>true</code> will alleviate this but can
 * cause unnecessary persists. The preferred workaround for this (other than preventing mutation of session attributes) is to enable
 * {@link RedisSessionManager#isDirtyOnMutation()} and invoke <code>setAttribute()</code> with the existing key and new value. Alternatively 
 * one could use <code>session.removeAttribute(null);</code> but this is a bit of a hack.
 *
 * @author Steve Ungerer
 */
public class RedisSession extends StandardSession {
	private static final Log log = LogFactory.getLog(RedisSession.class);
	private static final long serialVersionUID = 1L;

	private transient boolean dirty;

	/**
	 * Constructs a new {@link RedisSession} with no manager. Intended for deserialization usage.
	 */
	protected RedisSession() {
		super(null);
	}

	public RedisSession(RedisSessionManager manager) {
		super(manager);
		clearDirty();
	}

	/**
	 * Has the state of this session been modified from its persistent state
	 * 
	 * @return
	 */
	public boolean isDirty() {
		return dirty;
	}

	/**
	 * Indicate this session is in sync with its persistent version
	 */
	public void clearDirty() {
		dirty = false;
	}

	@Override
    public RedisSessionManager getManager() {
        return RedisSessionManager.class.cast(super.getManager());
    }

    @Override
	public void setId(String id) {
		// no super() invocation; don't want to remove/add the session
		this.id = id;
	}

	/**
	 * Marks the session as dirty if an attribute changes and saves the session if so configured. {@inheritDoc}
	 */
	@Override
	public void setAttribute(String key, Object value) {
		RedisSessionManager rsm = getManager();
		if (rsm.getMaxSessionAttributeSize() != RedisSessionManager.DO_NOT_CHECK) {
		    int size = rsm.getEncodedSize(value);
		    if (size > rsm.getMaxSessionAttributeSize()) {
		        if (!rsm.isAllowOversizedSessions()) {
		            log.error("Attribute [" + key + "] with size [" + size + "] exceeds max attr size [" + rsm.getMaxSessionAttributeSize() + "]; not storing in session");
		            return;
		        } else {
		            log.error("Attribute [" + key + "] with size [" + size + "] exceeds max attr size [" + rsm.getMaxSessionAttributeSize() + "]");
		        }
		    }
		}
		
		Object oldValue = getAttribute(key); // must be retrieved before invoking super()
		super.setAttribute(key, value);
		if (rsm.isDirtyOnMutation() 
			|| (value != null && (oldValue == null || !value.equals(oldValue)))
			|| (oldValue != null && (value == null || !oldValue.equals(value)))
		) {
			if (!saveOnChange()) {
				this.dirty = true;
				if (log.isTraceEnabled()) {
					log.trace("Marking session as dirty. Attr [" + key + "] changed from [" + oldValue +"] to [" + value + "]");
				}
			} else {
				if (log.isTraceEnabled()) {
					log.trace("Saved session onchange. Attr [" + key + "] changed from [" + oldValue +"] to [" + value + "]");
				}
			}
		}
	}

	/**
	 * Marks the session as dirty when an attribute is removed and saves the session if so configured. {@inheritDoc}
	 */
	@Override
	public void removeAttribute(String name) {
		super.removeAttribute(name);
		if (!saveOnChange()) {
			this.dirty = true;
			if (log.isTraceEnabled()) {
				log.trace("Marking session as dirty. Attr [" + name + "] was removed");
			}
		} else {
			log.debug("Saved session onchange; Attr [" + name + "] was removed");
		}
	}

	@Override
	public void setPrincipal(Principal principal) {
		this.dirty = true;
		super.setPrincipal(principal);
	}

	/**
	 * Persist the session to redis if so configured.
	 * @return was the session saved to redis
	 */
	private boolean saveOnChange() {
	    RedisSessionManager rsm = getManager();
		if (rsm.isSaveOnChange()) {
			rsm.save(this, true);
			return true;
		}
		return false;
	}

	/**
	 * Performs post-deserialization logic.
	 * 
	 * @param manager
	 */
	public void postDeserialization(RedisSessionManager manager) {
		setManager(manager);
		if (listeners == null) {
			listeners = new ArrayList<>();
		}
		if (notes == null) {
			notes = new ConcurrentHashMap<>();
		}
	}

}
