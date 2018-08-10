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
}
