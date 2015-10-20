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

package org.uberfire.cloud;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.uberfire.commons.config.ConfigProperties;
import org.uberfire.commons.services.cdi.Veto;

@Veto
public class LocalUniqueId {

    private final String id = UUID.randomUUID().toString();

    private Set<String> localServiceNames;
    private ExecutionMode currentMode = ExecutionMode.LOCAL;

    public LocalUniqueId() {
        this( new ConfigProperties( System.getProperties() ) );
    }

    public LocalUniqueId( final ConfigProperties config ) {
        loadConfig( config );
    }

    private void loadConfig( final ConfigProperties config ) {
        final ConfigProperties.ConfigProperty serviceName = config.get( "org.uberfire.local.service.names", null );
        if ( serviceName.getValue() == null || serviceName.getValue().trim().isEmpty() ) {
            currentMode = ExecutionMode.LOCAL;
            localServiceNames = Collections.unmodifiableSet( new HashSet<String>() {{
                add( "local" );
            }} );
        } else {
            currentMode = ExecutionMode.SERVICE;
            localServiceNames = Collections.unmodifiableSet( new HashSet<String>() {{
                addAll( Arrays.asList( serviceName.getValue().toUpperCase().split( "," ) ) );
            }} );
        }
    }

    public String getId() {
        return id;
    }

    public Set<String> getLocalServiceNames() {
        return localServiceNames;
    }

    public ExecutionMode getCurrentMode() {
        return currentMode;
    }
}
