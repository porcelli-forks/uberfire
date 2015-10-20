package org.uberfire.cloud.jms;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;

import org.uberfire.cloud.ExecutionMode;
import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.cloud.RoutingService;
import org.uberfire.cloud.injection.ServiceCall;
import org.uberfire.cloud.marshalling.MarshallingService;

/**
 * TODO: update me
 */
public class RPCRouterConsumer extends BaseConsumer {

    private final RoutingService router;
    private final QueueRouter queueRouter;

    public RPCRouterConsumer( final MarshallingService marshallingService,
                              final RoutingService router,
                              final QueueRouter queueRouter,
                              final LocalUniqueId uniqueId ) {
        super( marshallingService, uniqueId );
        this.router = router;
        this.queueRouter = queueRouter;
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
                        queueRouter.route( serviceName, message );
                    }
                }
            } catch ( JMSException e ) {
                throw new RuntimeException( "Error", e );
            }
        }
    }
}
