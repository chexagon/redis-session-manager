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

import java.beans.PropertyChangeSupport;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.crimsonhexagon.rsm.note.SavedRequestConverter;
import org.apache.catalina.authenticator.Constants;
import org.apache.catalina.session.StandardSession;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

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

	/* Hack for tomcat form-based-authentication support */
	private static final Map<String, Function<Object, Object>> serializableNoteConverters = new HashMap<>();

	private transient boolean dirty;

	/**
	 * Serializable notes (for form-base authentication support)
	 */
	private Map<String, Object> serializableNotes = new HashMap<>();

	/* Hack for tomcat form-based-authentication support: principal stored in redis (otherwise, it's
	 * transient in the superclass */
	private Principal principal = null;

	/**
	 * Constructs a new {@link RedisSession} with no manager. Intended for deserialization usage.
	 */
	protected RedisSession() {
		super(null);
	}

	static {
		addSerializableNoteConverter(Constants.FORM_REQUEST_NOTE, new SavedRequestConverter());
		addSerializableNoteConverter(Constants.FORM_PRINCIPAL_NOTE, (object) -> object);
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
	public void setId(String id) {
		// no super() invocation; don't want to remove/add the session
		this.id = id;
	}

	/**
	 * Marks the session as dirty if an attribute changes and saves the session if so configured. {@inheritDoc}
	 */
	@Override
	public void setAttribute(String key, Object value) {
		Object oldValue = getAttribute(key); // must be retrieved before invoking super()
		super.setAttribute(key, value);
		if (RedisSessionManager.class.cast(manager).isDirtyOnMutation() 
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
		this.principal = principal;
	}

	/**
	 * Persist the session to redis if so configured.
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
	 * 
	 * @param manager
	 */
	public void postDeserialization(RedisSessionManager manager) {
		setManager(manager);
		if (listeners == null) {
			listeners = new ArrayList<>();
		}
		if (support == null) {
			setField("support", new PropertyChangeSupport(this));
		}
		if (notes == null) {
			notes = new ConcurrentHashMap<>();
		}
		serializableNotes.forEach((name, value) -> {
			super.setNote(name, convertNoteToOriginalValue(name, value));
		});
		super.principal = this.principal;
	}

	/**
	 * This is needed to restore final transient field values of Tomcat Session class
	 * after session deserialization
	 *
	 * @exception IllegalArgumentException if an exception occurs
	 */
	private void setField(String name, Object value) {
		Field field = null;
		try {
			field = StandardSession.class.getDeclaredField(name);
			removeFinalModifier(field);
			field.set(this, value);

		} catch (NoSuchFieldException e) {
			throw new IllegalStateException("Inexistant field " + name + " in class "
					+ this.getClass().getName() + e.toString(), e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Error setting field value " + name + e.toString(), e);
		}
	}

	/**
	 * Removes final modifier from field
	 *
	 * @exception IllegalStateException if an exception occurs
	 */
	private void removeFinalModifier(Field field) {
		final Field modifiersField;
		try {
			modifiersField = Field.class.getDeclaredField("modifiers");
			AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
				public Object run() throws Exception {
					if(!modifiersField.isAccessible()) {
						modifiersField.setAccessible(true);
					}
					modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
					return null;
				}
			});
		} catch (NoSuchFieldException|PrivilegedActionException e) {
			throw new IllegalStateException("Error removing final modifier to field: " + field
					+ " : " + e.toString(), e);
		}
	}

	@Override
	public void removeNote(String name) {
		super.removeNote(name);
		if (shouldSerializeNote(name)) {
			removeSerializedNote(name);
		}
	}

	@Override
	public void setNote(String name, Object value) {
		super.setNote(name, value);
		if (shouldSerializeNote(name)) {
			setSerializedNote(name, value);
		}
	}

	private void setSerializedNote(String name, Object value) {
		serializableNotes.put(name, convertNoteToSerializableValue(name, value));
		if (!saveOnChange()) {
			this.dirty = true;
			if (log.isTraceEnabled()) {
				log.trace("Marking session as dirty. Note [" + name + "] set to [" + value + "]");
			}
		} else {
			if (log.isTraceEnabled()) {
				log.trace("Saved session onchange. Note [" + name + "] set to [" + value + "]");
			}
		}
	}

	private void removeSerializedNote(String name) {
		if (! serializableNotes.containsKey(name)) {
			return;
		}
		serializableNotes.remove(name);
		if (!saveOnChange()) {
			this.dirty = true;
			if (log.isTraceEnabled()) {
				log.trace("Marking session as dirty. Note [" + name + "] was removed");
			}
		} else {
			log.debug("Saved session onchange; Note [" + name + "] was removed");
		}
	}

	private boolean shouldSerializeNote(String name) {
		return serializableNoteConverters.containsKey(name);
	}

	protected Object convertNoteToSerializableValue(String name, Object value) {
		if (value == null) {
			return null;
		}
		Function<Object, Object> converter = serializableNoteConverters.get(name);
		if (converter == null) {
			throw new IllegalArgumentException("Unsupported serializable note attribute " + name);
		}
		return converter.apply(value);
	}

	protected Object convertNoteToOriginalValue(String name, Object value) {
		return convertNoteToSerializableValue(name, value);
	}

	public static void addSerializableNoteConverter(String name, Function<Object, Object> converter) {
		serializableNoteConverters.put(name, converter);
	}
}
