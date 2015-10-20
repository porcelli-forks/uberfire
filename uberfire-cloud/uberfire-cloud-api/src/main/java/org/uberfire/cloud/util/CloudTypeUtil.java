package org.uberfire.cloud.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.uberfire.commons.lifecycle.Disposable;
import org.uberfire.commons.services.cdi.Veto;

@Veto
public class CloudTypeUtil implements Disposable {

    private Set<Class<?>> cloudTypes = new HashSet<Class<?>>();

    public CloudTypeUtil() {
    }

    public CloudTypeUtil( final Collection<Class<?>> cloudTypes ) {
        this.cloudTypes.addAll( cloudTypes );
    }

    public boolean isCloudType( final Object object ) {
        for ( final Class<?> cloudType : cloudTypes ) {
            if ( cloudType.isInstance( object ) ) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void dispose() {
        cloudTypes.clear();
    }
}
