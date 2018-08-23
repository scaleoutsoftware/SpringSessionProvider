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

import com.scaleoutsoftware.soss.client.CacheFactory;
import com.scaleoutsoftware.soss.client.NamedCache;
import com.scaleoutsoftware.soss.client.NamedCacheException;
import javafx.scene.transform.Scale;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.soss.ScaleoutSession;
import org.springframework.session.soss.ScaleoutSessionRepository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

public class TestScaleoutRepository {

    @Test
    public void testCreate() {
        SessionRepository<ScaleoutSession> repository = new ScaleoutSessionRepository("test", Duration.ofMinutes(5), true);
        Session session = repository.createSession();
        Assert.assertNotNull(session);
    }

    @Test
    public void testCreateProperties() {
        SessionRepository<ScaleoutSession> repository = new ScaleoutSessionRepository("test", Duration.ofMinutes(5), true);
        Session session = repository.createSession();
        Assert.assertNotNull(session);
        Assert.assertEquals(session.getMaxInactiveInterval(), Duration.ofMinutes(5));
    }

    @Test
    public void testFindByIdNullObject() {
        SessionRepository<ScaleoutSession> repository = new ScaleoutSessionRepository("test", Duration.ofMinutes(5), true);
        Session session = repository.findById("THIS_ID_DOES_NOT_EXIST");
        Assert.assertNull(session);
    }

    @Test
    public void testFindByIdObjectExists() throws NamedCacheException {
        String cacheName = "test";
        String sessionId = "THIS_ID_SHOULD_EXIST";
        NamedCache cache = CacheFactory.getCache(cacheName);

        cache.put(hashString(sessionId), new ScaleoutSession(Instant.now(), Duration.ofMinutes(5)));

        SessionRepository<ScaleoutSession> repository = new ScaleoutSessionRepository(cacheName, Duration.ofMinutes(5), true);
        Session session = repository.findById(sessionId);
        Assert.assertNotNull(session);
        // remove object to release lock.
        cache.remove(hashString(sessionId));
    }

    @Test
    public void testSaveNull() {
        SessionRepository<ScaleoutSession> repository = new ScaleoutSessionRepository("test", Duration.ofMinutes(5), true);
        repository.save(null);
    }

    @Test
    public void testSave() throws NamedCacheException {
        String cacheName = "test";
        String attributeName = "random attribute";
        int attributeValue = 100;
        NamedCache cache = CacheFactory.getCache(cacheName);
        ScaleoutSession session = new ScaleoutSession(Instant.now(), Duration.ofMinutes(5));
        session.setAttribute(attributeName , attributeValue);

        SessionRepository<ScaleoutSession> repository = new ScaleoutSessionRepository(cacheName, Duration.ofMinutes(5), true);
        repository.save(session);

        ScaleoutSession ret = (ScaleoutSession)cache.get(hashString(session.getId()));
        Assert.assertNotNull(ret);
        Assert.assertNotNull(ret.getAttribute(attributeName));
        Assert.assertEquals(attributeValue, (int)ret.getAttribute(attributeName));

        // remove object to release lock.
        cache.remove(ret.getId());
    }

    private byte[] hashString(String id ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(id.getBytes());
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

}
