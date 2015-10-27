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

package org.uberfire.cloud.jms;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;

import org.uberfire.cloud.ExecutionMode;
import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.cloud.Publisher;
import org.uberfire.cloud.RoutingService;
import org.uberfire.cloud.event.EventDispatcher;
import org.uberfire.cloud.marshalling.MarshallingService;
import org.uberfire.cloud.rpc.LocalExecution;
import org.uberfire.cloud.rpc.Status;
import org.uberfire.commons.data.Pair;
import org.uberfire.commons.services.cdi.Startup;
import org.uberfire.commons.services.cdi.StartupType;

@ApplicationScoped
@Startup(StartupType.EAGER)
public class ActiveMQService implements Publisher {

    private Connection connection;
    private MarshallingService marshallingService;
    private LocalUniqueId uniqueId;
    private TemporaryTopic tempTopic;
    private MessageConsumer tempTopicConsumer;

    private Session eventsSession;
    private Session rpcSession;

    private MessageProducer eventsProducer;
    private MessageProducer rpcProducer;

    public ActiveMQService() {

    }

    @Inject
    public ActiveMQService( final LocalUniqueId uniqueId,
                            final Connection connection,
                            final @EventsTopicName String eventsTopicName,
                            final @RPCTopicName String rpcTopicName,
                            final MarshallingService marshallingService,
                            final EventDispatcher eventDispatcher,
                            final RoutingService router,
                            final LocalExecution localExecution ) {
        this.uniqueId = uniqueId;
        if ( uniqueId.getCurrentMode().equals( ExecutionMode.LOCAL ) ) {
            return;
        }
        this.connection = connection;
        try {
            eventsSession = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
            final Topic eventsTopic = eventsSession.createTopic( eventsTopicName );
            eventsProducer = eventsSession.createProducer( eventsTopic );

            rpcSession = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
            final Topic rpcTopic = rpcSession.createTopic( rpcTopicName );
            rpcProducer = rpcSession.createProducer( rpcTopic );

            //
            final Session tempSession = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
            tempTopic = tempSession.createTemporaryTopic();
            tempTopicConsumer = tempSession.createConsumer( tempTopic );
            {
                final Session eventsConsumeSession = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );

                final MessageConsumer eventsConsumer = eventsConsumeSession.createConsumer( eventsTopic );
                eventsConsumer.setMessageListener( new EventsConsumer( marshallingService, eventDispatcher, uniqueId ) );
            }

            {
                final Session rpcReplySession = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );

                final MessageConsumer routerConsumer = rpcReplySession.createConsumer( rpcTopic );
                routerConsumer.setMessageListener( new RPCServiceConsumer( rpcReplySession,
                                                                           marshallingService,
                                                                           localExecution,
                                                                           uniqueId,
                                                                           rpcReplySession.createProducer( null ),
                                                                           router ) );
            }
        } catch ( JMSException e ) {
            throw new RuntimeException( "Error", e );
        }
        this.marshallingService = marshallingService;
    }

    @Override
    public void publishAndForget( final Object value ) {
        if ( uniqueId.getCurrentMode() == ExecutionMode.LOCAL ) {
            return;
        }

        try {
            final BytesMessage message = toByteMessage( eventsSession, value );
            eventsProducer.send( message );
        } catch ( JMSException e ) {
            throw new RuntimeException( "Error", e );
        }
    }

    @Override
    public Pair<Status, ?> publishAndWait( final Object value ) {
        if ( uniqueId.getCurrentMode() == ExecutionMode.LOCAL ) {
            return null;
        }
        try {
            final BytesMessage message = toByteMessage( rpcSession, value );

            message.setJMSReplyTo( tempTopic );
            message.setJMSCorrelationID( message.getJMSMessageID() );

            rpcProducer.send( message );

            final Pair<Status, ?> pair = Pair.newPair( Status.EXECUTED, extractContent( (BytesMessage) tempTopicConsumer.receive() ) );
            return pair;
        } catch ( JMSException e ) {
            throw new RuntimeException( "Error", e );
        }
    }

    private Object extractContent( final BytesMessage message ) {
        try {
            byte[] ba = new byte[ (int) message.getBodyLength() ];
            message.readBytes( ba );
            return marshallingService.unmarshall( ba );
        } catch ( JMSException e ) {
            throw new RuntimeException( "Error", e );
        }
    }

    private BytesMessage toByteMessage( final Session session,
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