package org.uberfire.cloud.jms;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.uberfire.cloud.ExecutionMode;
import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.cloud.RoutingService;
import org.uberfire.cloud.injection.ServiceCall;
import org.uberfire.cloud.marshalling.MarshallingService;
import org.uberfire.cloud.rpc.LocalExecution;
import org.uberfire.cloud.rpc.Status;
import org.uberfire.commons.data.Pair;

/**
 * TODO: update me
 */
public class RPCServiceConsumer extends BaseConsumer {

    private final MessageProducer replierMessageProducer;
    private final Session replySession;
    private final LocalExecution localExecution;
    private final RoutingService router;

    public RPCServiceConsumer( final Session replySession,
                               final MarshallingService marshallingService,
                               final LocalExecution localExecution,
                               final LocalUniqueId uniqueId,
                               final MessageProducer replyProducer,
                               final RoutingService router ) {
        super( marshallingService, uniqueId );
        this.replySession = replySession;
        this.replierMessageProducer = replyProducer;
        this.localExecution = localExecution;
        this.router = router;
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
                    final String serviceName = router.resolveServiceName( serviceCall );
                    if ( serviceName != null ) {
                        final Pair<Status, ?> result = localExecution.execute( serviceCall );
                        final BytesMessage replyMessage = toByteMessage( replySession, result.getK2() );

                        replyMessage.setJMSCorrelationID( message.getJMSCorrelationID() );

                        replierMessageProducer.send( message.getJMSReplyTo(), replyMessage );
                    }
                }
            } catch ( JMSException e ) {
                throw new RuntimeException( "Error", e );
            }
        }
    }
}
