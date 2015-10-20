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

package org.uberfire.cloud;

import java.util.HashMap;
import java.util.Map;

import org.uberfire.cloud.injection.ServiceCall;
import org.uberfire.commons.services.cdi.Veto;

@Veto
public class RoutingServiceImpl implements RoutingService {

    private final Map<Class<?>, String> typeServiceMap = new HashMap<Class<?>, String>();

    public RoutingServiceImpl() {

    }

    public RoutingServiceImpl( final Map<Class<?>, String> typeServiceMap ) {
        this.typeServiceMap.putAll( typeServiceMap );
    }

    @Override
    public String resolveServiceName( final ServiceCall object ) {
        return typeServiceMap.get( object.getTargetType() );
    }
}
