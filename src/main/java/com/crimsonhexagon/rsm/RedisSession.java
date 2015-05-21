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

import java.security.Principal;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.catalina.session.StandardSession;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Extension of {@link StandardSession} to
 * <ol>
 * <li>
 * <li>allow redis persistence immediately when an attribute changes if the {@link RedisSessionManager} is so configured</li>
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
	public void setId(String id) {
		// no super() invocation; don't want to remove/add the session
		this.id = id;
	}

	/**
	 * Marks the session as dirty if an attribute changes and saves the session if so configured.
	 * {@inheritDoc}
	 */
	@Override
	public void setAttribute(String key, Object value) {
		Object oldValue = getAttribute(key);
		super.setAttribute(key, value);
		if ((value != null && (oldValue == null || !value.equals(oldValue)))
			|| (oldValue != null && (value == null || !oldValue.equals(value)))) {
			if (!saveOnChange()) {
				this.dirty = true;
			} else {
				log.debug("Saved on change; attr [" + key + "] changed");
			}
		}
	}

	/**
	 * Marks the session as dirty when an attribute is removed and saves the session if so configured.
	 * {@inheritDoc}
	 */
	@Override
	public void removeAttribute(String name) {
		super.removeAttribute(name);
		if (!saveOnChange()) {
			this.dirty = true;
		} else {
			log.debug("Saved on change; attr [" + name + "] removed");
		}
	}

	@Override
	public void setPrincipal(Principal principal) {
		this.dirty = true;
		super.setPrincipal(principal);
	}

	/**
	 * @return was the session saved to redis
	 */
	private boolean saveOnChange() {
		if (RedisSessionManager.class.isAssignableFrom(this.manager.getClass())
			&& RedisSessionManager.class.cast(this.manager).isSaveOnChange()
		) {
			RedisSessionManager.class.cast(this.manager).save(this, true);
			return true;
		}
		return false;
	}

	/**
	 * Performs post-deserialization logic.
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
