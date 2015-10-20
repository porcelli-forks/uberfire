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

package org.uberfire.cloud.injection;

import java.lang.reflect.Method;
import java.util.Set;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.uberfire.cloud.rpc.RPCService;

import static org.uberfire.commons.validation.PortablePreconditions.*;

public class CloudProxy {

    public static <T> T newInstance( final RPCService rpcService,
                                     final Class<T> target,
                                     final Set<String> qualifiers ) {
        checkNotNull( "rpcService", rpcService );
        checkNotNull( "target", target );
        checkNotNull( "qualifiers", qualifiers );

        Enhancer e = new Enhancer();
        e.setClassLoader( target.getClassLoader() );
        e.setSuperclass( target );
        e.setCallback( new MethodInterceptor() {
            public Object intercept( Object obj,
                                     Method method,
                                     Object[] args,
                                     MethodProxy proxy ) throws Throwable {
                return rpcService.execute( new ServiceCall( rpcService.getLocalId(), target, qualifiers, method, args ) );
            }
        } );

        return target.cast( e.create() );
    }
}
