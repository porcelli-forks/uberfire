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
import javax.jms.Message;

import org.uberfire.cloud.ExecutionMode;
import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.cloud.event.Event;
import org.uberfire.cloud.event.EventDispatcher;
import org.uberfire.cloud.marshalling.MarshallingService;

public class EventsConsumer extends BaseConsumer {

    private final EventDispatcher eventDispatcher;

    public EventsConsumer( final MarshallingService marshallingService,
                           final EventDispatcher eventDispatcher,
                           final LocalUniqueId uniqueId ) {
        super( marshallingService, uniqueId );
        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public void onMessage( final Message msg ) {
        if ( uniqueId.getCurrentMode() == ExecutionMode.LOCAL ) {
            return;
        }
        if ( msg instanceof BytesMessage ) {
            final BytesMessage message = (BytesMessage) msg;
            try {
                byte[] ba = new byte[ (int) message.getBodyLength() ];
                message.readBytes( ba );
                if ( !message.getStringProperty( "origin" ).equals( uniqueId.getId() ) ) {
                    final Event event = (Event) marshallingService.unmarshall( ba );
                    eventDispatcher.dispatch( event );
                }
            } catch ( JMSException e ) {
                throw new RuntimeException( "Error", e );
            }

        }
    }
}
