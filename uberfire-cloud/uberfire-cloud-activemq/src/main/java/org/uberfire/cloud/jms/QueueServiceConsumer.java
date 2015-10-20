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
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.uberfire.cloud.ExecutionMode;
import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.cloud.injection.ServiceCall;
import org.uberfire.cloud.marshalling.MarshallingService;
import org.uberfire.cloud.rpc.LocalExecution;
import org.uberfire.cloud.rpc.Status;
import org.uberfire.commons.data.Pair;

/**
 * TODO: update me
 */
public class QueueServiceConsumer extends BaseConsumer {

    private final MessageProducer replierMessageProducer;
    private final Session replySession;
    private final LocalExecution localExecution;

    public QueueServiceConsumer( final Session replySession,
                                 final MarshallingService marshallingService,
                                 final LocalExecution localExecution,
                                 final LocalUniqueId uniqueId,
                                 final MessageProducer replyProducer ) {
        super( marshallingService, uniqueId );
        this.replySession = replySession;
        this.replierMessageProducer = replyProducer;
        this.localExecution = localExecution;
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
                    final ServiceCall serviceCall = (ServiceCall) marshallingService.unmarshall( ba );
                    final Pair<Status, ?> result = localExecution.execute( serviceCall );
                    final BytesMessage replyMessage = toByteMessage( replySession, result.getK2() );


                    replyMessage.setStringProperty( "id", message.getStringProperty( "id" ) );

                    replierMessageProducer.send( replyMessage );
                }
            } catch ( JMSException e ) {
                throw new RuntimeException( "Error", e );
            }
        }
    }
}
