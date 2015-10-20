package org.uberfire.backend.server.impl;

import org.uberfire.cloud.Cloud;

@Cloud
public class CloudSampleEvent {

    private final String source;

    public CloudSampleEvent( final String source ) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }
}
