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

package org.uberfire.cloud.jms;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.cloud.marshalling.MarshallingService;

public abstract class BaseConsumer implements MessageListener {

    protected MarshallingService marshallingService;
    protected LocalUniqueId uniqueId;

    public BaseConsumer( final MarshallingService marshallingService,
                         final LocalUniqueId uniqueId ) {
        this.marshallingService = marshallingService;
        this.uniqueId = uniqueId;
    }

    protected BytesMessage toByteMessage( final Session session,
                                          final Object value ) {
        final byte[] content = marshallingService.marshall( value );
        try {
            final BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes( content );
            bytesMessage.setStringProperty( "origin", uniqueId.getId() );
            return bytesMessage;
        } catch ( JMSException e ) {
            throw new RuntimeException( "Error", e );
        }
    }
}