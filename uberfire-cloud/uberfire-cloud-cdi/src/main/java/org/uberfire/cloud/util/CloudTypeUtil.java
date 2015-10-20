/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.uberfire.cloud.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.uberfire.commons.lifecycle.Disposable;
import org.uberfire.commons.services.cdi.Veto;

@Veto
public class CloudTypeUtil implements Disposable {

    private Set<Class<?>> cloudTypes = new HashSet<Class<?>>();

    public CloudTypeUtil() {
    }

    public CloudTypeUtil( final Collection<Class<?>> cloudTypes ) {
        this.cloudTypes.addAll( cloudTypes );
    }

    public boolean isCloudType( final Object object ) {
        for ( final Class<?> cloudType : cloudTypes ) {
            if ( cloudType.isInstance( object ) ) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void dispose() {
        cloudTypes.clear();
    }
}
