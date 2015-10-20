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

package org.uberfire.cloud.rpc;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.uberfire.cloud.injection.ServiceCall;
import org.uberfire.commons.data.Pair;
import org.uberfire.commons.services.cdi.Veto;

@Veto
public class LocalExecutionImpl implements LocalExecution {

    private final BeanManager beanManager;
    private final Map<String, Annotation> allQualifiers;

    public LocalExecutionImpl( final BeanManager beanManager,
                               final Map<String, Annotation> allQualifiers ) {
        this.beanManager = beanManager;
        this.allQualifiers = allQualifiers;
    }

    @Override
    public Pair<Status, ?> execute( final ServiceCall serviceCall ) {
        final Set<Bean<?>> beans = beanManager.getBeans( serviceCall.getTargetType(), toAnnotations( serviceCall.getQualifiers() ) );

        if ( beans.isEmpty() ) {
            return Pair.newPair( Status.BEAN_NOT_FOUND, null );
        }

        final Bean<?> bean = beans.iterator().next();
        final Object instance = beanManager.getReference( bean, bean.getBeanClass(), beanManager.createCreationalContext( bean ) );

        try {
            return Pair.newPair( Status.EXECUTED,
                                 serviceCall.getMethod().invoke( instance, serviceCall.getArgs() ) );
        } catch ( Throwable e ) {
            return Pair.newPair( Status.ERROR, e );
        }
    }

    private Annotation[] toAnnotations( final Set<String> qualifiers ) {
        final Annotation[] annotations = new Annotation[ qualifiers.size() ];
        int index = 0;
        for ( final String qualifier : qualifiers ) {
            annotations[ index ] = allQualifiers.get( qualifier );
            index++;
        }
        return annotations;
    }

    @Override
    public void dispose() {
        allQualifiers.clear();
    }
}
