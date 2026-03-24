package org.dujia.agenticrag.configs;

import org.dujia.agenticrag.commons.Common;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    @Bean
    public DirectExchange uploadExchange() {
        return new DirectExchange(Common.UPLOAD_EXCHANGE);
    }
    @Bean
    public Queue uploadQueue() {
        return new Queue(Common.UPLOAD_QUEUE);
    }
    @Bean
    public Binding uploadBinding(DirectExchange exchange, Queue queue) {
        return BindingBuilder.bind(queue).to(exchange).with(Common.UPLOAD_ROUTING_KEY);
    }
}
