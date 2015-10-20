package org.uberfire.cloud.jms;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.uberfire.commons.config.ConfigProperties;
import org.uberfire.commons.services.cdi.Startup;
import org.uberfire.commons.services.cdi.StartupType;

import static org.uberfire.commons.validation.PortablePreconditions.*;

@Startup(StartupType.BOOTSTRAP)
@ApplicationScoped
public class ActiveMQProducer {

    private final static String DEFAULT_EVENTS_NAME = "UFCloudEventsTopic";
    private final static String DEFAULT_RPC_NAME = "UFCloudRPCTopic";

    private Connection connection;
    private String eventsTopicName;
    private String rpcTopicName;

    public ActiveMQProducer() {
    }

    @PostConstruct
    public void setup() {
        setup( new ConfigProperties( System.getProperties() ) );
    }

    public void setup( final ConfigProperties config ) {
        checkNotNull( "config", config );

        eventsTopicName = config.get( "org.uberfire.activemq.events", DEFAULT_EVENTS_NAME ).getValue();
        rpcTopicName = config.get( "org.uberfire.activemq.rpc", DEFAULT_RPC_NAME ).getValue();

        final String brokerURL = config.get( "org.uberfire.cloud.activemq.url", "vm://localhost?broker.persistent=false" ).getValue();
        final String username = config.get( "org.uberfire.cloud.activemq.username", null ).getValue();
        final String password = config.get( "org.uberfire.cloud.activemq.password", null ).getValue();

        final ConnectionFactory connectionFactory;
        if ( username != null && password != null ) {
            connectionFactory = new ActiveMQConnectionFactory( username, password, brokerURL );
        } else {
            connectionFactory = new ActiveMQConnectionFactory( brokerURL );
        }

        try {
            this.connection = connectionFactory.createConnection();
            connection.start();
            System.err.println( "done!" );
        } catch ( JMSException e ) {
            throw new RuntimeException( e );
        }

    }

    public void onShutdown( @Observes BeforeShutdown beforeShutdown ) {
        try {
            connection.close();
        } catch ( JMSException e ) {
            e.printStackTrace();
        }
    }

    @Produces
    @EventsTopicName
    public String topicName() {
        return eventsTopicName;
    }

    @Produces
    @RPCTopicName
    public String rpcTopicName() {
        return rpcTopicName;
    }

    @Produces
    public Connection connection() {
        return connection;
    }

}
