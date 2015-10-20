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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.ObserverMethod;

import org.uberfire.cloud.rpc.RPCService;
import org.uberfire.commons.services.cdi.Veto;

@Veto
public class EventDispatcherImpl implements EventDispatcher {

    private final RPCService rpcService;
    private final BeanManager beanManager;
    private final Map<String, Annotation> allQualifiers;

    public EventDispatcherImpl( final RPCService rpcService,
                                final BeanManager beanManager,
                                final Map<String, Annotation> allQualifiers ) {
        this.rpcService = rpcService;
        this.beanManager = beanManager;
        this.allQualifiers = allQualifiers;
    }

    @Override
    public void dispatch( final Event event ) {
        if ( !rpcService.getLocalId().equals( event.getOriginId() ) ) {
            final Set<String> qualifierNames = event.getQualifiers();
            final List<Annotation> qualifiers = new ArrayList<Annotation>();

            if ( qualifierNames != null ) {
                for ( final String qualifierName : qualifierNames ) {
                    final Annotation qualifier = allQualifiers.get( qualifierName );
                    if ( qualifier != null ) {
                        qualifiers.add( qualifier );
                    }
                }
            }

            // Fire event to all local observers
            final Annotation[] qualArray = qualifiers.toArray( new Annotation[ qualifiers.size() ] );
            final Set<ObserverMethod<? super Object>> observerMethods = beanManager.resolveObserverMethods( event.getEvent(), qualArray );
            for ( ObserverMethod<? super Object> observer : observerMethods ) {
                if ( !observer.getBeanClass().isAssignableFrom( LocalObserver.class ) ) {
                    observer.notify( event.getEvent() );
                }
            }
        }
    }

    @Override
    public void dispose() {
        allQualifiers.clear();
    }
}

