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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import org.uberfire.cloud.ExecutionMode;
import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.cloud.Publisher;
import org.uberfire.cloud.RoutingService;
import org.uberfire.cloud.event.EventDispatcher;
import org.uberfire.cloud.injection.ServiceCall;
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
    private RoutingService router;
    private LocalUniqueId uniqueId;

    private Session eventsSession;
    private Session rpcResultSession;

    private MessageProducer eventsProducer;
    private MessageProducer rpcResultProducer;

    private String baseQueueName;
    private final Map<String, Pair<Session, MessageProducer>> serviceProducers = new HashMap<>();

    private final ConcurrentHashMap<String, Object> rpcResults = new ConcurrentHashMap<>();

    public ActiveMQService() {
    }

    @Inject
    public ActiveMQService( final LocalUniqueId uniqueId,
                            final Connection connection,
                            final @EventsTopicName String eventsTopicName,
                            final @RPCTopicName String rpcTopicName,
                            final @BaseQueueName String baseQueueName,
                            final MarshallingService marshallingService,
                            final EventDispatcher eventDispatcher,
                            final RoutingService router,
                            final LocalExecution localExecution ) {
        this.uniqueId = uniqueId;
        this.baseQueueName = baseQueueName;
        this.connection = connection;
        try {
            {//events producer & consumer
                eventsSession = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
                final Topic eventsTopic = eventsSession.createTopic( eventsTopicName );
                eventsProducer = eventsSession.createProducer( eventsTopic );

                final Session eventsConsumeSession = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
                final MessageConsumer eventsConsumer = eventsConsumeSession.createConsumer( eventsTopic, null, true );
                eventsConsumer.setMessageListener( new EventsConsumer( marshallingService, eventDispatcher, uniqueId ) );
            }

            {//rpc result
                rpcResultSession = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
                final Topic rpcResultTopic = rpcResultSession.createTopic( rpcTopicName );
                rpcResultProducer = rpcResultSession.createProducer( rpcResultTopic );

                final Session rpcResultConsumeSession = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
                final MessageConsumer rpcResultConsumer = rpcResultConsumeSession.createConsumer( rpcResultTopic, null, true );
                rpcResultConsumer.setMessageListener( new BaseConsumer( marshallingService, uniqueId ) {
                    @Override
                    public void onMessage( final Message message ) {
                        try {
                            final Object value = extractContent( (BytesMessage) message );
                            rpcResults.put( message.getStringProperty( "id" ), value );
                        } catch ( JMSException e ) {
                            throw new RuntimeException( e );
                        }
                    }
                } );

            }

            for ( final String service : uniqueId.getLocalServiceNames() ) {
                final Session rpcConsumeSession = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
                final Queue serviceQueue = rpcConsumeSession.createQueue( baseQueueName + "/" + service );

                final MessageConsumer rpcConsumer = rpcConsumeSession.createConsumer( serviceQueue );
                rpcConsumer.setMessageListener( new QueueServiceConsumer( rpcResultSession,
                                                                          marshallingService,
                                                                          localExecution,
                                                                          uniqueId,
                                                                          rpcResultProducer ) );
            }
        } catch ( JMSException e ) {
            throw new RuntimeException( "Error", e );
        }
        this.marshallingService = marshallingService;
        this.router = router;
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
    public Pair<Status, ?> publishAndWait( final Object _value ) {
        if ( uniqueId.getCurrentMode() == ExecutionMode.LOCAL ) {
            return null;
        }
        if ( !( _value instanceof ServiceCall ) ) {
            return null;
        }
        final ServiceCall value = (ServiceCall) _value;
        final String serviceName = router.resolveServiceName( value );
        if ( serviceName == null ) {
            return null;
        }

        final Pair<Session, MessageProducer> jmsPair = resolveJMS( serviceName );

        try {
            final BytesMessage message = toByteMessage( jmsPair.getK1(), value );
            final String messageID = message.getStringProperty( "id" );

            jmsPair.getK2().send( message );

            while ( !rpcResults.containsKey( messageID ) ) {
                try {
                    Thread.sleep( 50 );
                } catch ( final InterruptedException ignored ) {
                }
            }
            Object result = rpcResults.remove( messageID );
            if ( value.equals( Void.class ) ) {
                result = null;
            }
            return Pair.newPair( Status.EXECUTED, result );
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
            bytesMessage.setStringProperty( "id", UUID.randomUUID().toString() );
            return bytesMessage;
        } catch ( JMSException e ) {
            throw new RuntimeException( "Error", e );
        }
    }

    private Pair<Session, MessageProducer> resolveJMS( final String serviceName ) {
        final Pair<Session, MessageProducer> result = serviceProducers.get( serviceName );
        if ( result == null ) {
            try {
                final Session session = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
                final Queue serviceQueue = session.createQueue( baseQueueName + "/" + serviceName );
                final Pair<Session, MessageProducer> _result = Pair.newPair( session, session.createProducer( serviceQueue ) );
                serviceProducers.put( serviceName, _result );
                return _result;
            } catch ( JMSException e ) {
                throw new RuntimeException( e );
            }
        }
        return result;
    }
}