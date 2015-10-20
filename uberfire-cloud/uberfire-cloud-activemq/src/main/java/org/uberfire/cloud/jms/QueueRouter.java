package org.uberfire.cloud.jms;

import javax.jms.BytesMessage;

public interface QueueRouter {

    void route( final String serviceName,
                final BytesMessage originalMessage );
}
