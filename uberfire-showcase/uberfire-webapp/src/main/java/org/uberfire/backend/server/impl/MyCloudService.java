package org.uberfire.backend.server.impl;

import java.math.BigInteger;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jboss.errai.bus.server.annotations.Service;
import org.uberfire.cloud.Cloud;
import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.shared.CloudService;

@ApplicationScoped
@Service
@Cloud("my-service")
public class MyCloudService implements CloudService {

    @Inject
    private LocalUniqueId uniqueId;

    @Override
    public void process( final int times ) {
        for ( int i = 0; i < times; i++ ) {
            BigInteger result = new BigInteger( "1" );
            for ( int z = 1; i <= times; i++ ) {
                result = result.multiply( new BigInteger( String.valueOf( z ) ) );
            }
        }

        System.err.println( "process executed!" );
    }

    @Override
    public String process( final int times,
                           final String value ) {
        BigInteger result = null;
        for ( int i = 0; i < times; i++ ) {
            result = new BigInteger( "1" );
            for ( int z = 1; i <= times; i++ ) {
                result = result.multiply( new BigInteger( String.valueOf( z ) ) );
            }
        }
        if ( result == null ) {
            return "null!";
        }

        System.err.println( "process executed! == " + result.toString() + " " + value );

        return result.toString() + " " + value;
    }
}
