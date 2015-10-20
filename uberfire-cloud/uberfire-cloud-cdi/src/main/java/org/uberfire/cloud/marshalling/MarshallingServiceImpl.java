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

package org.uberfire.cloud.marshalling;

import java.io.ByteArrayOutputStream;
import javax.enterprise.context.ApplicationScoped;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;
import org.objenesis.strategy.StdInstantiatorStrategy;

@ApplicationScoped
public class MarshallingServiceImpl implements MarshallingService {

    private final KryoPool pool = new KryoPool.Builder( () -> new Kryo() {{
        setInstantiatorStrategy( new DefaultInstantiatorStrategy( new StdInstantiatorStrategy() ) );
    }} ).softReferences().build();

    @Override
    public byte[] marshall( final Object content ) {
        return pool.run( kryo -> {
            Output output = null;
            try {
                output = new Output( new ByteArrayOutputStream() );
                kryo.writeClassAndObject( output, content );
                return output.toBytes();
            } finally {
                if ( output != null ) {
                    output.close();
                }
            }
        } );
    }

    @Override
    public Object unmarshall( final byte[] content ) {
        return pool.run( kryo -> kryo.readClassAndObject( new Input( content ) ) );
    }
}
