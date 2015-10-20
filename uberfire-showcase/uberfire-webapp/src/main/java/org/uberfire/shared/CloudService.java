package org.uberfire.shared;

import org.jboss.errai.bus.server.annotations.Remote;

@Remote
public interface CloudService {

    void process( final int times );

    String process( final int times,
                    final String value );

}
