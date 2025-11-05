package com.gephub.kyc_service.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {
    @Bean
    public Queue kycQueue(@Value("${gephub.amqp.queue}") String queueName) {
        return new Queue(queueName, true);
    }
}


