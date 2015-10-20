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

package org.uberfire.cloud.injection;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

public class ServiceCall {

    private final String origin;
    private final Class targetType;
    private final Set<String> qualifiers;
    private final Method method;
    private final Object[] args;

    public ServiceCall( final String origin,
                        final Class targetType,
                        final Set<String> qualifiers,
                        final Method method,
                        final Object[] args ) {
        this.origin = origin;
        this.targetType = targetType;
        this.qualifiers = qualifiers;
        this.method = method;
        this.args = args;
    }

    public String getOrigin() {
        return origin;
    }

    public Class getTargetType() {
        return targetType;
    }

    public Set<String> getQualifiers() {
        return qualifiers;
    }

    public Method getMethod() {
        return method;
    }

    public Object[] getArgs() {
        return args;
    }

    @Override
    public boolean equals( final Object o ) {
        if ( this == o ) {
            return true;
        }
        if ( !( o instanceof ServiceCall ) ) {
            return false;
        }

        final ServiceCall that = (ServiceCall) o;

        if ( !origin.equals( that.origin ) ) {
            return false;
        }
        if ( !targetType.equals( that.targetType ) ) {
            return false;
        }
        if ( !qualifiers.equals( that.qualifiers ) ) {
            return false;
        }
        if ( !method.equals( that.method ) ) {
            return false;
        }
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals( args, that.args );

    }

    @Override
    public int hashCode() {
        int result = origin.hashCode();
        result = 31 * result + targetType.hashCode();
        result = 31 * result + qualifiers.hashCode();
        result = 31 * result + method.hashCode();
        result = 31 * result + Arrays.hashCode( args );
        return result;
    }
}
