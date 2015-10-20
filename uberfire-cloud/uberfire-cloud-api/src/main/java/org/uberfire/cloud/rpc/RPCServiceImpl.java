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

import javax.inject.Inject;

import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.cloud.Publisher;
import org.uberfire.cloud.injection.ServiceCall;
import org.uberfire.commons.data.Pair;
import org.uberfire.commons.services.cdi.Veto;

@Veto
public class RPCServiceImpl implements RPCService {

    private final Publisher publisher;
    private final LocalUniqueId uniqueId;

    @Inject
    public RPCServiceImpl( final Publisher publisher,
                           final LocalUniqueId uniqueId ) {
        this.publisher = publisher;
        this.uniqueId = uniqueId;
    }

    @Override
    public Object execute( final ServiceCall call ) {
        final Pair<Status, ?> result = publisher.publishAndWait( call );
        if ( result.getK1().equals( Status.EXECUTED ) ) {
            return result.getK2();
        }
        throw new RuntimeException( "Couldn't Execute" );
    }

    @Override
    public String getLocalId() {
        return uniqueId.getId();
    }
}
