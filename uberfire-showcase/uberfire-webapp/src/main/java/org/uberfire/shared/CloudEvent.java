package org.uberfire.shared;

import org.jboss.errai.bus.server.annotations.Remote;

@Remote
public interface CloudEvent {

    void sendEvent( final int times );

    void sendQualifiedEvent( final int times );

}
