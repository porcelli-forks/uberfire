/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    private final static String DEFAULT_QUEUE_BASE_NAME = "UFCloudQueue";

    private Connection connection;
    private String eventsTopicName;
    private String rpcTopicName;
    private String baseQueueName;

    @PostConstruct
    public void setup() {
        setup( new ConfigProperties( System.getProperties() ) );
    }

    public void setup( final ConfigProperties config ) {
        checkNotNull( "config", config );

        eventsTopicName = config.get( "org.uberfire.activemq.events", DEFAULT_EVENTS_NAME ).getValue();
        rpcTopicName = config.get( "org.uberfire.activemq.rpc", DEFAULT_RPC_NAME ).getValue();
        baseQueueName = config.get( "org.uberfire.activemq.queue.base", DEFAULT_QUEUE_BASE_NAME ).getValue();

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
    @BaseQueueName
    public String baseQueueName() {
        return baseQueueName;
    }

    @Produces
    public Connection connection() {
        return connection;
    }

}
