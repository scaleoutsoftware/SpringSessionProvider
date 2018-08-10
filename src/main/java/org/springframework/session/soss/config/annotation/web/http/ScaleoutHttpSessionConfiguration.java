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
package org.springframework.session.soss.config.annotation.web.http;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.soss.ScaleoutSession;
import org.springframework.session.soss.ScaleoutSessionRepository;

import java.time.Duration;
import java.util.Map;

/**
 * ScaleOutHttpSessionConfiguration is responsible for instantiating a ScaleOutSession repository with the proper
 * configuration parameters from the {@link EnableScaleoutHttpSession} annotation.
 */
@Configuration
@EnableScheduling
public class ScaleoutHttpSessionConfiguration extends SpringHttpSessionConfiguration implements ImportAware {

    // reasonable repository and session defaults.
    private String _cacheName       = ScaleoutSessionRepository.DEF_CACHE_NAME;
    private boolean _useLocking     = ScaleoutSessionRepository.DEF_USE_LOCKING;
    private int _maxInactiveTime    = ScaleoutSession.DEF_MAX_INACTIVE_TIME;

    /**
     * Returns a new ScaleOutSessionRepository with default parameters or parameters assigned during import.
     * @return a new ScaleOutSessionRepository.
     */
    @Bean
    public ScaleoutSessionRepository sessionRepository() {
        Duration maxInactive = Duration.ofMinutes(_maxInactiveTime);
        ScaleoutSessionRepository repo = new ScaleoutSessionRepository(_cacheName, maxInactive, _useLocking);
        return repo;
    }

    /**
     * Imports metadata from the EnableScaleoutHttpSession class.
     * @param importMetadata the annotation metadata to pull info from.
     */
    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        Map<String, Object> annotationValueMap = importMetadata.getAnnotationAttributes(EnableScaleoutHttpSession.class.getName());
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(annotationValueMap);
        _cacheName = attributes.getString("cacheName");
        _maxInactiveTime = attributes.getNumber("maxInactiveTimeMinutes");
        _useLocking = attributes.getBoolean("useLocking");

    }
}
