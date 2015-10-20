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

package org.uberfire.cloud.marshalling;

import org.junit.Test;

import static org.junit.Assert.*;

public class MarshallingServiceTest {

    private final MarshallingServiceImpl marshallingService = new MarshallingServiceImpl();

    @Test
    public void testGeneralMarshalling() {
        final Test1 test1Original = new Test1( 99, true, new Test2( "value01" ), "value2" );
        byte[] marshall = marshallingService.marshall( test1Original );

        final Object result = marshallingService.unmarshall( marshall );

        assertTrue( test1Original.equals( result ) );

        try {
            Test2.class.cast( marshallingService.unmarshall( marshall ) );
            fail();
        } catch ( ClassCastException ex ) {
        } catch ( RuntimeException ex ) {
            fail( "Wrong exception" );
        }
    }

    public static class Test1 {

        private final int primitive;
        private final Boolean boxedPrimitive;
        private final Test2 object;
        private final String value;

        public Test1( final int primitive,
                      final Boolean boxedPrimitive,
                      final Test2 object,
                      final String value ) {
            this.primitive = primitive;
            this.boxedPrimitive = boxedPrimitive;
            this.object = object;
            this.value = value;
        }

        public int getPrimitive() {
            return primitive;
        }

        public Boolean getBoxedPrimitive() {
            return boxedPrimitive;
        }

        public Test2 getObject() {
            return object;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals( final Object o ) {
            if ( this == o ) {
                return true;
            }
            if ( !( o instanceof Test1 ) ) {
                return false;
            }

            final Test1 test1 = (Test1) o;

            if ( primitive != test1.primitive ) {
                return false;
            }
            if ( !boxedPrimitive.equals( test1.boxedPrimitive ) ) {
                return false;
            }
            if ( !object.equals( test1.object ) ) {
                return false;
            }
            return value.equals( test1.value );

        }

        @Override
        public int hashCode() {
            int result = primitive;
            result = 31 * result + boxedPrimitive.hashCode();
            result = 31 * result + object.hashCode();
            result = 31 * result + value.hashCode();
            return result;
        }
    }

    public static class Test2 {

        private final String value;

        public Test2( final String value ) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals( final Object o ) {
            if ( this == o ) {
                return true;
            }
            if ( !( o instanceof Test2 ) ) {
                return false;
            }

            final Test2 test2 = (Test2) o;

            return value.equals( test2.value );

        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
