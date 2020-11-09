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
package org.springframework.session.soss.config.annotation.web.http;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.session.soss.ScaleoutSession;
import org.springframework.session.soss.ScaleoutSessionRepository;

import java.lang.annotation.*;


/**
 * The EnableScaleoutHttpSession annotation is used to enable ScaleOut StateServer
 * as a session repository for storing HTTP sessions.
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({ java.lang.annotation.ElementType.TYPE })
@Import(ScaleoutHttpSessionConfiguration.class)
@Configuration
public @interface EnableScaleoutHttpSession {

    /**
     * Sets the max inactive time of a session in minutes.
     * @return the max inactive time of a session
     */
    int maxInactiveTimeMinutes() default ScaleoutSession.DEF_MAX_INACTIVE_TIME;

    /**
     * Sets the NamedCache (namespace) name used by the ScaleoutSessionRepository.
     * @return the cache name to use
     */
    String cacheName() default ScaleoutSessionRepository.DEF_CACHE_NAME;

    /**
     * Sets whether or not to lock sessions on retrieval.
     * @return whether or not to lock sessions on retrieval
     */
    boolean useLocking() default ScaleoutSessionRepository.DEF_USE_LOCKING;

    /**
     * If GeoServer pull is licensed and configured, setting the value for remoteStoreName indicates that replica objects
     * in a remote store should be notified every time the local, master object is updated.
     * @return the remote store name (Must have GeoServer Pro licensed.)
     */
    String remoteStoreName();

    /**
     * Note, requires {@link EnableScaleoutHttpSession#remoteStoreName()} to have a value.
     *
     * The {@link EnableScaleoutHttpSession#remoteReadPendingRetryInterval()} can be used to set the interval to wait
     * when performing a read from a remote store in case a WAN error occurs.
     *
     * If GeoServer pull is licensed and configured, when performing a remote read of an object from a another store via
     * GeoServer pull replication, the {@link ScaleoutSessionRepository} may need to repeatedly attempt to
     * perform the remote read in a number of situations (for example, the master copy of the object may be in transit to
     * a different remote store, or another thread in this client may already be trying to perform a remote read and is
     * refreshing the local replica of the object).
     * @return the millisecond time interval between remote store read retries
     */
    int remoteReadPendingRetryInterval() default ScaleoutSessionRepository.DEF_REMOTE_READPENDING_RETRY_INTERVAL;

    /**
     * Note, requires {@link EnableScaleoutHttpSession#remoteStoreName()} to have a value.
     *
     * The {@link EnableScaleoutHttpSession#maxRemoteReadRetries()} can be used to set the maximum number of retries
     * the {@link ScaleoutSessionRepository} will exhaust before throwing a CantAccessException.
     *
     * If GeoServer pull is licensed and configured, when performing a remote read of an object from a another store
     * via GeoServer pull replication, the {@link ScaleoutSessionRepository} may need to repeatedly attempt to
     * perform the remote read in a number of situations (for example, the master copy of the object may be in transit
     * to a different remote store, or another thread in this client may already be trying to perform a remote read and
     * is refreshing the local replica of the object).
     * @return the maximum number of remote read retries
     */
    int maxRemoteReadRetries() default ScaleoutSessionRepository.DEF_REMOTE_READPENDING_RETRIES;
}
