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

import java.util.Set;

public class Event {

    private final String originId;
    private final Object event;
    private final Set<String> qualifiers;

    public Event( final String originId,
                  final Object event,
                  final Set<String> qualifiers ) {
        this.originId = originId;
        this.event = event;
        this.qualifiers = qualifiers;
    }

    public String getOriginId() {
        return originId;
    }

    public Object getEvent() {
        return event;
    }

    public Set<String> getQualifiers() {
        return qualifiers;
    }

    @Override
    public boolean equals( final Object o ) {
        if ( this == o ) {
            return true;
        }
        if ( !( o instanceof Event ) ) {
            return false;
        }

        final Event event1 = (Event) o;

        if ( !originId.equals( event1.originId ) ) {
            return false;
        }
        if ( !event.equals( event1.event ) ) {
            return false;
        }
        return qualifiers.equals( event1.qualifiers );

    }

    @Override
    public int hashCode() {
        int result = originId.hashCode();
        result = 31 * result + event.hashCode();
        result = 31 * result + qualifiers.hashCode();
        return result;
    }
}
