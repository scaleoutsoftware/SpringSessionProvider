/*
 Copyright (c) 2018 by ScaleOut Software, Inc.

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

import com.scaleoutsoftware.soss.client.*;
import com.scaleoutsoftware.soss.client.da.*;
import com.scaleoutsoftware.soss.client.da.ReadOptions;
import com.scaleoutsoftware.soss.client.query.EqualFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.soss.config.annotation.web.http.EnableScaleoutHttpSession;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


/**
 * A {@link org.springframework.session.SessionRepository} implementation that is backed by ScaleOut StateServer's
 * NamedCache API. This implementation does not support publishing events.
 *
 * <p>
 *     Integrating with the {@link ScaleoutSessionRepository} is very easy. Inside your application code, simply add
 *     a basic configuration class with the {@link EnableScaleoutHttpSession}
 *     annotation.
 * </p>
 *
 * <p>
 *     Optionally, you can set the cache name and the timeout for sessions. By default, the cache name used is
 *     "SpringSessionRepo" and the session timeout is 30 minutes and locking is enabled.
 * </p>
 */
public class ScaleoutSessionRepository implements FindByIndexNameSessionRepository<ScaleoutSession> {
    private static final Log logger = LogFactory.getLog(ScaleoutSessionRepository.class);
    // spring integration helper classes and attributes
    static final PrincipalNameResolver PRINCIPAL_NAME_RESOLVER = new PrincipalNameResolver();
    private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

    // reasonable defaults for a session repository
    /**
     * Default namespace to store sessions.
     */
    public static final String DEF_CACHE_NAME = "SpringSessionRepo";
    /**
     * Default locking value.
     */
    public static final boolean DEF_USE_LOCKING = true;
    /**
     * default remote read-pending retry interval.
     */
    public static final int DEF_REMOTE_READPENDING_RETRY_INTERVAL = 10;
    /**
     * default remote read-pending retries.
     */
    public static final int DEF_REMOTE_READPENDING_RETRIES = 2400;

    // the NamedCache to use for query
    private final NamedCache _cache;

    // helper objects for locking
    private final ConcurrentHashMap<String, DataAccessor> _sessionAccessors;
    private final HashSet<ReadOptions> _readOptions;
    private final CreatePolicy _createPolicy;

    // private member configuration variables
    private final Duration _maxInactiveTime;
    private final boolean _useLocking;
    private final int _remoteReadPendingInterval;
    private final int _remoteReadPendingRetries;


    /**
     * Instantiates the ScaleOutSessionRepository.
     * @param cacheName the cache name to store {@link org.springframework.session.soss.ScaleoutSession}s
     * @param maxInactiveTime the max inactive time of a session
     * @param useLocking if the scaleout repository is using locking
     */
    public ScaleoutSessionRepository(String cacheName, Duration maxInactiveTime, boolean useLocking, String remoteStoreName, int remoteReadPendingInterval, int remoteReadRetries) {
        _maxInactiveTime = maxInactiveTime;
        _useLocking = useLocking;
        _sessionAccessors = new ConcurrentHashMap<>();
        _remoteReadPendingInterval = remoteReadPendingInterval;
        _remoteReadPendingRetries = remoteReadRetries;

        // setup a new create policy
        _createPolicy = new CreatePolicy();
        _createPolicy.setTimeout(TimeSpan.fromMinutes(maxInactiveTime.toMinutes()));
        if(remoteStoreName != null) {
            _createPolicy.setDefaultCoherencyPolicy(new NotifyCoherencyPolicy());
        }

        // setup a new set of read options for a DataAccessor that uses locking
        _readOptions = new HashSet<>();
        _readOptions.add(ReadOptions.ObjectMayNotExist); // don't throw "ObjectNotFound" exceptions -- return null.
        _readOptions.add(ReadOptions.ReturnCachedObjectIfValid); // use the client cache
        if(remoteStoreName != null) {
            _readOptions.add(ReadOptions.ReadRemoteObject);
        }

        if(_useLocking) {
            _readOptions.add(ReadOptions.LockObject); // if the object exists, lock the object
        }

        try {
            _cache = CacheFactory.getCache(cacheName);
            StateServerKey.setDefaultAppId(StateServerKey.appNameToId(cacheName));
            if(remoteStoreName != null) {
                List<RemoteStore> stores = new LinkedList<>();
                stores.add(new RemoteStore(remoteStoreName));
                _cache.setRemoteStores(stores);
            }
        } catch (StateServerException e) {
            logger.error("Couldn't create namespace.");
            throw new RuntimeException(e);
        } catch (NamedCacheException e) {
            logger.error("Couldn't create NamedCache.");
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a new ScaleOut session.
     * @return a new ScaleOut Session
     */
    @Override
	public ScaleoutSession createSession() {
        return new ScaleoutSession(Instant.now(), _maxInactiveTime);
	}

    /**
     * Saves a {@link org.springframework.session.soss.ScaleoutSession}.
     * @param session the session to save
     */
	@Override
	public void save(ScaleoutSession session) {
	    if(session == null) return;

        List<String> oldIds = session.oldIds();
        // if the session is new, or we have old sessions -- which means the key has changed --
        // then we need to create the session and if necessary remove the old sessions.
        if(session.isNew() || oldIds != null) {
            saveNewSession(session, oldIds);
        } else {
            // if the session is not new, we need to update
            saveExistingSession(session);
        }
    }

    /**
     * Finds a session based on the session id or null if no {@link org.springframework.session.soss.ScaleoutSession} with that ID exists.
     * @param id the id of the session to find
     * @return the associated {@link org.springframework.session.soss.ScaleoutSession} with the parameter id or NULL
     */
    @Override
    public ScaleoutSession findById(String id) {
        if(id == null) return null;
        return retrieveSession(id);
    }

    /**
     * Deletes a {@link org.springframework.session.soss.ScaleoutSession} from the
     * configured NamedCache with the parameter session id.
     * @param id the session id to delete
     */
    @Override
    public void deleteById(String id) {
        if(id == null) return;
        delete(id);
    }

    /**
     * Retrieves a HashMap correlating session IDs to {@link org.springframework.session.soss.ScaleoutSession}.
     * @param indexName the principal name
     * @param indexValue the desired value
     * @return a hashmap containing all sessions associated with the configured index name and parameter index value or an empty map
     */
    @Override
    public Map<String, ScaleoutSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
        if(!PRINCIPAL_NAME_INDEX_NAME.equals(indexName) ||
                indexValue == null) {
            return Collections.emptyMap();
        } else {
            Set<CachedObjectId<ScaleoutSession>> keys = null;
            try {
                keys = _cache.queryKeys(ScaleoutSession.class, new EqualFilter("principalNameIndexName", indexValue));
            } catch (NamedCacheException e) {
                logger.error("Error thrown querying keys.", e);
            }
            if(keys != null) {
                Map<String, ScaleoutSession> map = new HashMap<>();
                for(CachedObjectId<ScaleoutSession> key : keys) {
                    try {
                        map.put(key.getKeyString(), _cache.get(key));
                    } catch (NamedCacheException e) {
                        logger.error("Error thrown retrieving object.", e);
                    }
                }
                return map;
            }
        }
        return null;
    }

    // private helper method to hash a string to a 32-byte key
    private byte[] hashStringKey(String id) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(id.getBytes());
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    // private helper method to remove old sessions
    private void removeOldSessions(List<String> oldIds) {
        if(oldIds != null) {
            for(String id : oldIds){
                try {
                    // will handle local cleanup for local DAs
                    delete(id);
                } catch (Exception e) {
                    logger.error("Exception thrown while deleting old session.", e);
                }
            }
        }
    }

    // private helper method to retrieve a session
    private ScaleoutSession retrieveSession(String id) {
        int remoteReadAttempt = 0;
	    // create or retrieve a DA
        DataAccessor da = null;
        if(_useLocking) {
            da = _sessionAccessors.get(id);
        }

        if(da == null) {
            da = getDA(id);
        }

        // perform read
        ReadResult readResult = null;
        do {
            try {
                if(da != null)
                    readResult = da.read(_readOptions);
            } catch (ObjectLockedException ole) {
                // If the object is locked, it means two threads tried to retrieve the same session and some other thread
                // won. In case the other thread is local to this client, we will retrieve the correct DA and retry.
                // If another client won (i.e. some other instance of the session repository has the lock), we will keep
                // re-trying the read until we can successfully read and lock the session.
                if(_useLocking) {
                    DataAccessor tempDa = _sessionAccessors.get(id);
                    if (tempDa != null) {
                        da = tempDa;
                    }
                } else {
                    // with locking disabled an ObjectLockedException should not occur -- log the error and return null
                    logger.error(ole);
                    return null;
                }
            } catch (ReadThroughPendingException rtpe) {
                // If a read through pending exception is thrown, it means the session is on a remote store and the
                // local store is in process of pulling the object to the local store. In this case, we simply retry the
                // read and lock according to the configured number of retries and retry interval.
                remoteReadAttempt++;
                if (remoteReadAttempt >= _remoteReadPendingRetries) {
                    logger.error("read through pending timed-out.");
                    return null;
                } else
                    try {
                        Thread.sleep(_remoteReadPendingInterval);
                    } catch (InterruptedException e1) {
                        throw new RuntimeException("Unexpected error while waiting to retry");
                    }
            }
            catch (StateServerException e) {
                logger.error(e);
                return null;
            }
        } while(readResult == null);

        // read completed -- and if locking is enabled, the object is locked -- any exception from this point on means we need to
        // release the lock (IOException, or ClassCastException). The finally block is used for lock cleanup and
        // keeping track of the DA that holds the correct lock ticket.
        boolean releaseLock = false;
        try {
            ScaleoutSession session = retrieveSessionFromReadResult(readResult);
            if(session != null) {
                if(session.isExpired()) {
                    // the session is expired, delete it and return null
                    delete(id);
                    return null;
                } else {
                    // mark the session
                    session.setLastAccessedTime(Instant.now());
                    session.markTouched();
                }
            }
            return session;
        } catch (Exception e) {
            releaseLock = _useLocking;
            return null;
        } finally {
            // if an exception occurred, we need to cleanup, i.e., release the stateserver lock and remove the local
            // DA from the table
            if(releaseLock) {
                try {
                    da.releaseLock();
                } catch (StateServerException e) {
                    logger.warn(e);
                }
            } else if(_useLocking) { // make sure the proper DA is in the local table
                _sessionAccessors.put(id, da);
            }
        }
    }

    // private helper method to create a DA
    private DataAccessor getDA(String id) {
        if(id == null) return null;
        try {
            StateServerKey key = new StateServerKey(hashStringKey(id));
            key.setKeyString(id);
            DataAccessor da = new DataAccessor(key);
            da.setLockedWhenReading(_useLocking);
            return da;
        } catch (StateServerException e) {
            logger.error(e);
            return null;
        }
    }

    // private helper method to create a new session in stateserver
    private void saveNewSession(ScaleoutSession session, List<String> oldSessionIds) {
        session.resolveQueryableAttributes();
        try {
            DataAccessor da = getDA(session.getId());
            session.markTouched();
            if(da != null)
                da.create(_createPolicy, session);
        } catch (ObjectExistsException oee) {
            logger.warn(oee);
            saveExistingSession(session);
        } catch (StateServerException e) {
            logger.error("Exception thrown while saving new session", e);
        } finally {
            removeOldSessions(oldSessionIds);
        }
    }

    // private helper method to save an existing session -- i.e. update the session.
    private void saveExistingSession(ScaleoutSession session) {
	    DataAccessor da = null;
	    boolean releaseLock = false;
        boolean removeSessionAccessor = false;
        boolean foundSessionAccessorWithLock = false;

        try {


            do {
                // If we're updating a session, that means we've found an existing session (i.e. session.isNew() == false).
                // so, we need to retrieve the DA we used to retrieve the session
                if(_useLocking) {
                    da = _sessionAccessors.get(session.getId());
                    foundSessionAccessorWithLock = (da != null);
                }

                // Even when we're unlocking, it's possible to call "save" multiple times -- in which case, we need to create
                // a new DA to perform the update because the session is no longer locked and we don't have a local DA. We
                // also need to create a DA if locking is turned off.
                if(da == null) {
                    da = getDA(session.getId());
                }

                // it's always safe to call update and unlock even when locking is disabled or we don't have a lock ticket
                try {
                    if(da != null) {
                        da.update(session, true);
                        removeSessionAccessor = true;
                        break;
                    }
                } catch(ObjectLockedException ole) { logger.warn("object locked, retrying."); }
            } while(true);

        } catch (StateServerException e) {
            logger.error("Error thrown saving session.", e);
            releaseLock = foundSessionAccessorWithLock;
        } finally {
            try {
                 // cleanup if an exception occurred
                if(releaseLock) {
                    da.releaseLock();
                }

                // cleanup to remove the existing DA
                if(removeSessionAccessor) {
                    _sessionAccessors.remove(session.getId());
                }
            } catch (Exception e) {
                logger.error(e);
            }
        }
    }

    // private helper to extract a session object from a DA read result
    private ScaleoutSession retrieveSessionFromReadResult(ReadResult result) throws IOException, ClassNotFoundException {
        Object obj = null;
        byte[] serializedSession = null;
        if(result != null) {
            if(result.getStatus() != StateServerResult.Success) {
                return null;
            } else if(result.isCachedObjectValid()) {
                obj = result.getCachedObject();
            } else {
                serializedSession = result.getBytes();
            }
        }
        if (obj != null) {
            return (ScaleoutSession) obj;
        }
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedSession));
        obj = ois.readObject();
        ois.close();
        return (ScaleoutSession) obj;
    }

    // private helper method to delete a session and handles local cleanup
	private void delete(String s) {
	    if(s == null) return;
        try {
            DataAccessor da = null;
            if(_useLocking) {
                da = _sessionAccessors.remove(s);
            }

            if(da == null) {
                da = getDA(s);
            }
            da.delete();
        } catch (StateServerException e) {
            logger.error("Error thrown deleting session.", e);
        }
    }

    /**
     * Static helper class to resolve the PRINCIPAL_NAME_INDEX_NAME OR SPRING_SECURITY_CONTEXT. This class is used to
     * set the principal name (annotated SossIndexAttribute method) in {@link org.springframework.session.soss.ScaleoutSession}.
     */
    static class PrincipalNameResolver {
        private SpelExpressionParser parser = new SpelExpressionParser();

        public String resolvePrincipal(Session session) {
            String principalName = session.getAttribute(PRINCIPAL_NAME_INDEX_NAME);
            if (principalName != null) {
                return principalName;
            }
            Object authentication = session.getAttribute(SPRING_SECURITY_CONTEXT);
            if (authentication != null) {
                Expression expression = this.parser.parseExpression("authentication?.name");
                return expression.getValue(authentication, String.class);
            }
            return null;
        }

    }
}
