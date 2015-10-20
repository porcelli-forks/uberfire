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
