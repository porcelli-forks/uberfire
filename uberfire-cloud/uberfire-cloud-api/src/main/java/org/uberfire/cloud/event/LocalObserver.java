/*
 *   Copyright 2015 JBoss Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.uberfire.cloud.event;

import java.lang.annotation.Annotation;
import java.util.Collections;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.EventMetadata;
import javax.inject.Inject;

import org.uberfire.cloud.ExecutionMode;
import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.cloud.Publisher;
import org.uberfire.cloud.util.CDIUtils;
import org.uberfire.cloud.util.CloudTypeUtil;
import org.uberfire.commons.services.cdi.Startup;
import org.uberfire.commons.services.cdi.StartupType;

@ApplicationScoped
@Startup(StartupType.BOOTSTRAP)
public class LocalObserver {

    private Publisher publisher;
    private LocalUniqueId uniqueId;
    private CloudTypeUtil cloudTypeUtil;

    public LocalObserver() {
    }

    @Inject
    public LocalObserver( final Publisher publisher,
                          final LocalUniqueId uniqueId,
                          final CloudTypeUtil cloudTypeUtil ) {
        this.publisher = publisher;
        this.uniqueId = uniqueId;
        this.cloudTypeUtil = cloudTypeUtil;
    }

    public void observer( @Observes final Object event,
                          final EventMetadata emd ) {
        if ( uniqueId.getCurrentMode() == ExecutionMode.LOCAL ) {
            return;
        }
        if ( event instanceof CloudEvent || cloudTypeUtil.isCloudType( event ) ) {
            publisher.publishAndForget( toEvent( event, emd ) );
        }
    }

    private Event toEvent( final Object event,
                           final EventMetadata emd ) {
        return new Event( uniqueId.getId(), event, ( emd != null ) ? CDIUtils.getQualifiersPart( emd.getQualifiers().toArray( new Annotation[ 0 ] ) ) : Collections.<String>emptySet() );
    }

}
