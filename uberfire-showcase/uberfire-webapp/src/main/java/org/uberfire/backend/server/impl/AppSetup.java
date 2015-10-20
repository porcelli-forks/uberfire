package org.uberfire.backend.server.impl;

import java.net.URI;
import java.util.HashMap;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;

import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.commons.services.cdi.Startup;
import org.uberfire.io.IOService;
import org.uberfire.java.nio.file.FileSystemAlreadyExistsException;

@ApplicationScoped
@Startup
public class AppSetup {

    private static final String PLAYGROUND_ORIGIN = "https://github.com/guvnorngtestuser1/guvnorng-playground.git";
    private static final String PLAYGROUND_UID = "guvnorngtestuser1";

    @Inject
    @Named("ioStrategy")
    private IOService ioService;

    @Inject
    private LocalUniqueId uniqueId;

    @PostConstruct
    public void assertPlayground() {
        try {
            ioService.newFileSystem( URI.create( "default://uf-playground" ), new HashMap<String, Object>() {{
                put( "origin", PLAYGROUND_ORIGIN );
                put( "username", PLAYGROUND_UID );
            }} );
        } catch ( final FileSystemAlreadyExistsException ignore ) {
        }
    }

    public void onCloudEvent1( @Observes final CloudSampleEvent event ) {
        if ( !event.getSource().equals( uniqueId.getId() ) ) {
            System.err.println( "Remote Event!" );
        } else {
            System.err.println( "Local Event!" );
        }
    }

    public void onCloudEvent2( @Observes @Named("myquali!") final CloudSampleEvent event ) {
        if ( !event.getSource().equals( uniqueId.getId() ) ) {
            System.err.println( "Remote Quali Event!" );
        } else {
            System.err.println( "Local Quali Event!" );
        }
    }

}
