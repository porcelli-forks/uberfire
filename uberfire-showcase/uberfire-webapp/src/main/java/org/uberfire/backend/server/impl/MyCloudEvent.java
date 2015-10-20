package org.uberfire.backend.server.impl;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.inject.Named;

import org.jboss.errai.bus.server.annotations.Service;
import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.shared.CloudEvent;

@ApplicationScoped
@Service
public class MyCloudEvent implements CloudEvent {

    @Inject
    private LocalUniqueId uniqueId;

    @Inject
    private Event<CloudSampleEvent> event;

    @Inject
    @Named("myquali!")
    private Event<CloudSampleEvent> qualifiedEvent;

    @Override
    public void sendEvent( final int times ) {
        event.fire( new CloudSampleEvent( uniqueId.getId() ) );
    }

    @Override
    public void sendQualifiedEvent( final int times ) {
        qualifiedEvent.fire( new CloudSampleEvent( uniqueId.getId() ) );
    }
}
