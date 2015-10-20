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

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class CDIUtils {

    /**
     * Return a list of string representations for the qualifiers.
     * @param qualifiers -
     * @return
     */
    public static Set<String> getQualifiersPart( final Annotation[] qualifiers ) {
        Set<String> qualifiersPart = null;
        if ( qualifiers != null ) {
            for ( final Annotation qualifier : qualifiers ) {
                if ( qualifiersPart == null ) {
                    qualifiersPart = new HashSet<>( qualifiers.length );
                }

                qualifiersPart.add( qualifier.annotationType().getName() );
            }
        }
        return qualifiersPart == null ? Collections.<String>emptySet() : qualifiersPart;

    }

}
