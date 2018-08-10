/*
 Copyright (c) 2016 by ScaleOut Software, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
package org.springframework.session.soss;

import com.scaleoutsoftware.soss.client.SossIndexAttribute;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.session.Session;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.*;


/**
 * A {@link org.springframework.session.Session} implementation that can be stored in ScaleOut StateServer.
 * A ScaleOut session is the object stored inside the ScaleOut StateServer store. The ScaleOutSessionRepository is
 * responsible for instantiating ScaleoutSession instances.
 */
public class ScaleoutSession implements Session, Serializable {
	private static final Log logger = LogFactory.getLog(ScaleoutSessionRepository.class);

	/**
	 * Default inactive time in minutes for a session.
	 */
	public final static int DEF_MAX_INACTIVE_TIME = 30;

	// session attributes
	private String _sessionId;
	private Instant _lastAccessTime;
	private Instant _createTime;
	private Duration _inactiveTime;
	private HashMap<String, Object> _attributes;

	// special session attribute used for query
	private String _principalNameIndexName;

	// these attributes are used to mark a session as new/old
	// and as a check to see if the session id has changed.
	private transient LinkedList<String> _oldIds = null;
	private boolean _isNew;

	/**
	 * Constructor for a ScaleoutSession.
	 * @param lastAccessTime the last time the session was retrieved from the ScaleOut StateServer store
	 * @param inactiveTime the maximum duration between current retrieval time and last access time before the session is removed
	 */
	ScaleoutSession(Instant lastAccessTime, Duration inactiveTime){
		 _createTime = _lastAccessTime = lastAccessTime;
		 _inactiveTime = inactiveTime;
		 _attributes = new HashMap<>();
		 _sessionId = newId();
		 _isNew = true;
	}

	/**
	 * Retrieves the session identifier.
	 * @return the session identifier
	 */
	public String getId() {
		return _sessionId;
	}


	/**
	 * Changes the session ID for this session.
	 * @return the new session id
	 */
	public String changeSessionId() {
		// When the session ID changes, we need to keep track of the old ID, so that we can remove it from
		// the store. It's possible to change the session ID multiple times before saving the session, so we keep
		// a transient list.
		if(_oldIds == null) {
			_oldIds = new LinkedList<>();
		}
		_oldIds.add(_sessionId);
		_sessionId = newId();
		return _sessionId;
	}

	/**
	 * Retrieves an attribute stored in this session.
	 * @param attributeName the string name of the attribute
	 * @param <T> the value associated with the attribute or null
	 * @return the value associated with the attributeName or null
	 */
	@SuppressWarnings("Unchecked")
	public <T> T getAttribute(String attributeName) {
		if(attributeName != null && _attributes != null) {
			return (T) _attributes.get(attributeName);
		} else {
			return null;
		}
	}

	/**
	 * Retrieves a set of strings that represent all attribute names stored in this session.
	 * @return the collection of attribute names
	 */
	public Set<String> getAttributeNames() {
		return _attributes.keySet();
	}

	/**
	 * Sets an attribute name and value for this session. If the attribute value is null and the attribute exists within
	 * the attribute collection, the attribute will be removed.
	 * @param attributeName the identifier of the attribute
	 * @param attributeValue the value of the attribute
	 */
	public void setAttribute(String attributeName, Object attributeValue) {
		if(attributeName != null && attributeValue != null) {
			_attributes.put(attributeName, attributeValue);
		} else if(attributeName != null) {
			removeAttribute(attributeName);
		}
	}

	/**
	 * Removes the attribute name and value from this session.
	 * @param attributeName removes the attribute
	 */
	public void removeAttribute(String attributeName) {
		_attributes.remove(attributeName);
	}

	/**
	 * Retrieves the creation time of the session.
	 * @return the creation time of the session
	 */
	public Instant getCreationTime() {
		return _createTime;
	}

	/**
	 * Sets the last access time of the session.
	 * @param lastAccessedTime the last time the session was accessed
	 */
	public void setLastAccessedTime(Instant lastAccessedTime) {
		_lastAccessTime = lastAccessedTime;
	}

	/**
	 * Retrieves the last access time of the session.
	 * @return the time the session was last accessed
	 */
	public Instant getLastAccessedTime() {
		return _lastAccessTime;
	}

	/**
	 * Sets the maximum amount of time a session can be inactive.
	 * @param interval the amount of time allowed for a session to be inactive
	 */
	public void setMaxInactiveInterval(Duration interval) {
		_inactiveTime = interval;
	}

	/**
	 * Retrieves the maximum amount of time a session can be inactive.
	 * @return the inactive time
	 */
	public Duration getMaxInactiveInterval() {
		return _inactiveTime;
	}

	/**
	 * represents if this session is expired.
	 * @return true/false if this session is expired
	 */
	public boolean isExpired() {
		Instant checkExpired = Instant.from(_lastAccessTime);
		checkExpired = checkExpired.plus(_inactiveTime);

		return checkExpired.compareTo(Instant.now()) <= 0;
	}

	boolean isNew() { return _isNew; }

	void markTouched() {
		_isNew = false;
	}

	/**
	 * Package private helper method which retrieves the old session identifiers that were previously associated with this session.
	 * @return old session identifiers
	 */
	List<String> oldIds() {
		List<String> ret = _oldIds;
		_oldIds = null;
		return ret;
	}

	/**
	 * Package private helper method
	 */
	void resolveQueryableAttributes() {
		_principalNameIndexName = ScaleoutSessionRepository.PRINCIPAL_NAME_RESOLVER.resolvePrincipal(this);
	}

	/**
	 * This is used for findByIndexNameAndIndexValue and is set by the session repository.
	 * @return the value for principalNameIndexName
	 */
	@SossIndexAttribute
	public String principalNameIndexName() {
		return _principalNameIndexName;
	}

	/**
	 * Helper method to create a new session ID.
	 * @return a new session identifier
	 */
	private static String newId() {
		return UUID.randomUUID().toString();
	}
}
