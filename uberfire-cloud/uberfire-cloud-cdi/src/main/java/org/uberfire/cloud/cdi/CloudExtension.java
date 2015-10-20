/*
 *   Copyright 2015 JBoss Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.uberfire.cloud.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBeanAttributes;
import javax.enterprise.inject.spi.ProcessObserverMethod;
import javax.enterprise.util.AnnotationLiteral;

import org.uberfire.cloud.Cloud;
import org.uberfire.cloud.ExecutionMode;
import org.uberfire.cloud.LocalUniqueId;
import org.uberfire.cloud.Publisher;
import org.uberfire.cloud.RoutingService;
import org.uberfire.cloud.RoutingServiceImpl;
import org.uberfire.cloud.event.EventDispatcher;
import org.uberfire.cloud.event.EventDispatcherImpl;
import org.uberfire.cloud.event.LocalObserver;
import org.uberfire.cloud.injection.CloudProxy;
import org.uberfire.cloud.rpc.LocalExecution;
import org.uberfire.cloud.rpc.LocalExecutionImpl;
import org.uberfire.cloud.rpc.RPCService;
import org.uberfire.cloud.rpc.RPCServiceImpl;
import org.uberfire.cloud.util.CloudTypeUtil;

public class CloudExtension implements Extension {

    private final LocalUniqueId uniqueId = new LocalUniqueId();

    private final Map<String, Annotation> eventQualifiers = new HashMap<String, Annotation>();

    private final Collection<ProcessBeanAttributes<?>> cloudServiceTypes = new ArrayList<ProcessBeanAttributes<?>>();

    private final Set<Class<?>> cloudTypes = new HashSet<Class<?>>();
    private final Map<Class<?>, String> typeServiceMap = new HashMap<Class<?>, String>();

    private CloudTypeUtil cloudTypeUtil;

    void processObserverMethod( @Observes final ProcessObserverMethod processObserverMethod ) {
        final Type t = processObserverMethod.getObserverMethod().getObservedType();
        Class type = null;

        if ( t instanceof Class ) {
            type = (Class) t;
        }

        if ( type != null ) {
            final Set<Annotation> annotations = processObserverMethod.getObserverMethod().getObservedQualifiers();
            final Annotation[] methodQualifiers = annotations.toArray( new Annotation[ annotations.size() ] );
            for ( final Annotation qualifier : methodQualifiers ) {
                eventQualifiers.put( qualifier.annotationType().getName(), qualifier );
            }
        }
    }

    <T> void observeResources( @Observes final ProcessAnnotatedType<T> pat ) {
        final AnnotatedType<T> type = pat.getAnnotatedType();

        if ( type.getJavaClass().isAnnotationPresent( Cloud.class ) ) {
            cloudTypes.add( pat.getAnnotatedType().getJavaClass() );
        }
    }

    <X> void processBeanProperties( @Observes final ProcessBeanAttributes<X> pba ) {
        if ( uniqueId.getCurrentMode() == ExecutionMode.LOCAL ) {
            if ( pba.getAnnotated().getBaseType().equals( LocalObserver.class ) ) {
                pba.veto();
            }
            return;
        }

        if ( pba.getAnnotated().isAnnotationPresent( Cloud.class ) ) {
            final String serviceName = pba.getAnnotated().getAnnotation( Cloud.class ).value().toUpperCase();
            if ( uniqueId.getLocalServiceNames().contains( serviceName ) ) {
                final Class<?> clazz = (Class<?>) pba.getAnnotated().getBaseType();
                cloudTypes.remove( clazz );
                typeServiceMap.put( clazz, serviceName );
            } else {
                cloudServiceTypes.add( pba );
                pba.veto();
            }
        }
    }

    void afterBeanDiscovery( @Observes final AfterBeanDiscovery abd,
                             final BeanManager bm ) {
        this.cloudTypeUtil = new CloudTypeUtil( cloudTypes );

        abd.addBean( new Bean<LocalUniqueId>() {

            @Override
            public Class<?> getBeanClass() {
                return LocalUniqueId.class;
            }

            @Override
            public Set<InjectionPoint> getInjectionPoints() {
                return Collections.emptySet();
            }

            @Override
            public String getName() {
                return "LocalUniqueId";
            }

            @Override
            public Set<Annotation> getQualifiers() {
                return new HashSet<Annotation>() {{
                    add( new AnnotationLiteral<Default>() {
                    } );
                    add( new AnnotationLiteral<Any>() {
                    } );
                }};
            }

            @Override
            public Class<? extends Annotation> getScope() {
                return ApplicationScoped.class;
            }

            @Override
            public Set<Class<? extends Annotation>> getStereotypes() {
                return Collections.emptySet();
            }

            @Override
            public Set<Type> getTypes() {
                return new HashSet<Type>() {{
                    add( LocalUniqueId.class );
                    add( Object.class );
                }};
            }

            @Override
            public boolean isAlternative() {
                return false;
            }

            @Override
            public boolean isNullable() {
                return false;
            }

            @Override
            public LocalUniqueId create( CreationalContext<LocalUniqueId> ctx ) {
                return uniqueId;
            }

            @Override
            public void destroy( final LocalUniqueId instance,
                                 final CreationalContext<LocalUniqueId> ctx ) {
                ctx.release();
            }
        } );

        abd.addBean( new Bean<CloudTypeUtil>() {

            @Override
            public Class<?> getBeanClass() {
                return CloudTypeUtil.class;
            }

            @Override
            public Set<InjectionPoint> getInjectionPoints() {
                return Collections.emptySet();
            }

            @Override
            public String getName() {
                return "CloudTypeUtil";
            }

            @Override
            public Set<Annotation> getQualifiers() {
                return new HashSet<Annotation>() {{
                    add( new AnnotationLiteral<Default>() {
                    } );
                    add( new AnnotationLiteral<Any>() {
                    } );
                }};
            }

            @Override
            public Class<? extends Annotation> getScope() {
                return ApplicationScoped.class;
            }

            @Override
            public Set<Class<? extends Annotation>> getStereotypes() {
                return Collections.emptySet();
            }

            @Override
            public Set<Type> getTypes() {
                return new HashSet<Type>() {{
                    add( CloudTypeUtil.class );
                    add( Object.class );
                }};
            }

            @Override
            public boolean isAlternative() {
                return false;
            }

            @Override
            public boolean isNullable() {
                return false;
            }

            @Override
            public CloudTypeUtil create( CreationalContext<CloudTypeUtil> ctx ) {
                return cloudTypeUtil;
            }

            @Override
            public void destroy( final CloudTypeUtil instance,
                                 final CreationalContext<CloudTypeUtil> ctx ) {
                instance.dispose();
                ctx.release();
            }
        } );

        if ( uniqueId.getCurrentMode() != ExecutionMode.LOCAL ) {
            for ( final ProcessBeanAttributes<?> pba : cloudServiceTypes ) {
                abd.addBean( new Bean() {

                    @Override
                    public Class<?> getBeanClass() {
                        return (Class<?>) pba.getAnnotated().getBaseType();
                    }

                    @Override
                    public Set<InjectionPoint> getInjectionPoints() {
                        return Collections.emptySet();
                    }

                    @Override
                    public String getName() {
                        return pba.getBeanAttributes().getName();
                    }

                    @Override
                    public Set<Annotation> getQualifiers() {
                        return pba.getBeanAttributes().getQualifiers();
                    }

                    @Override
                    public Class<? extends Annotation> getScope() {
                        return pba.getBeanAttributes().getScope();
                    }

                    @Override
                    public Set<Class<? extends Annotation>> getStereotypes() {
                        return pba.getBeanAttributes().getStereotypes();
                    }

                    @Override
                    public Set<Type> getTypes() {
                        return pba.getBeanAttributes().getTypes();
                    }

                    @Override
                    public boolean isAlternative() {
                        return pba.getBeanAttributes().isAlternative();
                    }

                    @Override
                    public boolean isNullable() {
                        return false;
                    }

                    @Override
                    public Object create( CreationalContext ctx ) {
                        return CloudProxy.newInstance( resolveBean( RPCService.class, bm ),
                                                       (Class<?>) pba.getAnnotated().getBaseType(),
                                                       Collections.<String>emptySet() );
                    }

                    @Override
                    public void destroy( final Object instance,
                                         final CreationalContext ctx ) {
                        ctx.release();
                    }
                } );
            }

            abd.addBean( new Bean<EventDispatcherImpl>() {

                @Override
                public Class<?> getBeanClass() {
                    return EventDispatcherImpl.class;
                }

                @Override
                public Set<InjectionPoint> getInjectionPoints() {
                    return Collections.emptySet();
                }

                @Override
                public String getName() {
                    return "EventDispatcher";
                }

                @Override
                public Set<Annotation> getQualifiers() {
                    return new HashSet<Annotation>() {{
                        add( new AnnotationLiteral<Default>() {
                        } );
                        add( new AnnotationLiteral<Any>() {
                        } );
                    }};
                }

                @Override
                public Class<? extends Annotation> getScope() {
                    return ApplicationScoped.class;
                }

                @Override
                public Set<Class<? extends Annotation>> getStereotypes() {
                    return Collections.emptySet();
                }

                @Override
                public Set<Type> getTypes() {
                    return new HashSet<Type>() {{
                        add( EventDispatcher.class );
                        add( Object.class );
                    }};
                }

                @Override
                public boolean isAlternative() {
                    return false;
                }

                @Override
                public boolean isNullable() {
                    return false;
                }

                @Override
                public EventDispatcherImpl create( CreationalContext<EventDispatcherImpl> ctx ) {
                    return new EventDispatcherImpl( resolveBean( RPCService.class, bm ),
                                                    bm,
                                                    eventQualifiers );
                }

                @Override
                public void destroy( final EventDispatcherImpl instance,
                                     final CreationalContext<EventDispatcherImpl> ctx ) {
                    try {
                        instance.dispose();
                    } catch ( final Exception ex ) {
                        //logger.warn( ex.getMessage(), ex );
                    }
                    ctx.release();
                }
            } );

            abd.addBean( new Bean<LocalExecutionImpl>() {

                @Override
                public Class<?> getBeanClass() {
                    return LocalExecutionImpl.class;
                }

                @Override
                public Set<InjectionPoint> getInjectionPoints() {
                    return Collections.emptySet();
                }

                @Override
                public String getName() {
                    return "LocalExecution";
                }

                @Override
                public Set<Annotation> getQualifiers() {
                    return new HashSet<Annotation>() {{
                        add( new AnnotationLiteral<Default>() {
                        } );
                        add( new AnnotationLiteral<Any>() {
                        } );
                    }};
                }

                @Override
                public Class<? extends Annotation> getScope() {
                    return ApplicationScoped.class;
                }

                @Override
                public Set<Class<? extends Annotation>> getStereotypes() {
                    return Collections.emptySet();
                }

                @Override
                public Set<Type> getTypes() {
                    return new HashSet<Type>() {{
                        add( LocalExecution.class );
                        add( Object.class );
                    }};
                }

                @Override
                public boolean isAlternative() {
                    return false;
                }

                @Override
                public boolean isNullable() {
                    return false;
                }

                @Override
                public LocalExecutionImpl create( final CreationalContext<LocalExecutionImpl> ctx ) {
                    return new LocalExecutionImpl( bm,
                                                   Collections.<String, Annotation>emptyMap() );
                }

                @Override
                public void destroy( final LocalExecutionImpl instance,
                                     final CreationalContext<LocalExecutionImpl> ctx ) {
                    try {
                        instance.dispose();
                    } catch ( final Exception ex ) {
                        //logger.warn( ex.getMessage(), ex );
                    }
                    ctx.release();
                }
            } );

            abd.addBean( new Bean<RoutingServiceImpl>() {

                @Override
                public Class<?> getBeanClass() {
                    return RoutingServiceImpl.class;
                }

                @Override
                public Set<InjectionPoint> getInjectionPoints() {
                    return Collections.emptySet();
                }

                @Override
                public String getName() {
                    return "RoutingServiceImpl";
                }

                @Override
                public Set<Annotation> getQualifiers() {
                    return new HashSet<Annotation>() {{
                        add( new AnnotationLiteral<Default>() {
                        } );
                        add( new AnnotationLiteral<Any>() {
                        } );
                    }};
                }

                @Override
                public Class<? extends Annotation> getScope() {
                    return ApplicationScoped.class;
                }

                @Override
                public Set<Class<? extends Annotation>> getStereotypes() {
                    return Collections.emptySet();
                }

                @Override
                public Set<Type> getTypes() {
                    return new HashSet<Type>() {{
                        add( RoutingService.class );
                        add( Object.class );
                    }};
                }

                @Override
                public boolean isAlternative() {
                    return false;
                }

                @Override
                public boolean isNullable() {
                    return false;
                }

                @Override
                public RoutingServiceImpl create( final CreationalContext<RoutingServiceImpl> ctx ) {
                    return new RoutingServiceImpl( typeServiceMap );
                }

                @Override
                public void destroy( final RoutingServiceImpl instance,
                                     final CreationalContext<RoutingServiceImpl> ctx ) {
                    ctx.release();
                }
            } );

            abd.addBean( new Bean<RPCServiceImpl>() {

                @Override
                public Class<?> getBeanClass() {
                    return RPCServiceImpl.class;
                }

                @Override
                public Set<InjectionPoint> getInjectionPoints() {
                    return Collections.emptySet();
                }

                @Override
                public String getName() {
                    return "RPCServiceImpl";
                }

                @Override
                public Set<Annotation> getQualifiers() {
                    return new HashSet<Annotation>() {{
                        add( new AnnotationLiteral<Default>() {
                        } );
                        add( new AnnotationLiteral<Any>() {
                        } );
                    }};
                }

                @Override
                public Class<? extends Annotation> getScope() {
                    return ApplicationScoped.class;
                }

                @Override
                public Set<Class<? extends Annotation>> getStereotypes() {
                    return Collections.emptySet();
                }

                @Override
                public Set<Type> getTypes() {
                    return new HashSet<Type>() {{
                        add( RPCService.class );
                        add( Object.class );
                    }};
                }

                @Override
                public boolean isAlternative() {
                    return false;
                }

                @Override
                public boolean isNullable() {
                    return false;
                }

                @Override
                public RPCServiceImpl create( final CreationalContext<RPCServiceImpl> ctx ) {
                    return new RPCServiceImpl( resolveBean( Publisher.class, bm ),
                                               resolveBean( LocalUniqueId.class, bm ) );
                }

                @Override
                public void destroy( final RPCServiceImpl instance,
                                     final CreationalContext<RPCServiceImpl> ctx ) {
                    ctx.release();
                }
            } );
        }
    }

    private <T> T resolveBean( final Class<T> type,
                               final BeanManager bm ) {
        final Bean<T> bean = (Bean<T>) bm.getBeans( type ).iterator().next();
        final CreationalContext<T> _ctx = bm.createCreationalContext( bean );
        return type.cast( bm.getReference( bean, type, _ctx ) );
    }

}
