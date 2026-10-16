package com.macro.mall.portal.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecovererWithConfirms;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Keep invalid cancellation messages for investigation instead of requeuing forever. */
@Configuration
public class CancelOrderRecoveryConfig {
    public static final String FAILED = "mall.order.cancel.failed";

    @Bean
    DirectExchange cancelFailureExchange() {
        return new DirectExchange(FAILED, true, false);
    }

    @Bean
    Queue cancelFailureQueue() {
        return new Queue(FAILED, true);
    }

    @Bean
    Binding cancelFailureBinding() {
        return BindingBuilder.bind(cancelFailureQueue()).to(cancelFailureExchange()).with(FAILED);
    }

    @Bean
    SimpleRabbitListenerContainerFactory cancelOrderContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        if (!(connectionFactory instanceof CachingConnectionFactory caching)) {
            throw new IllegalStateException("Cancellation recovery requires publisher confirms");
        }
        caching.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        caching.setPublisherReturns(true);
        RabbitTemplate recoveryTemplate = new RabbitTemplate(connectionFactory);
        recoveryTemplate.setMandatory(true);
        var recoverer = new RepublishMessageRecovererWithConfirms(recoveryTemplate, FAILED, FAILED,
                CachingConnectionFactory.ConfirmType.CORRELATED);
        recoverer.setConfirmTimeout(10000);
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(cancellationRetryAdvice(recoverer));
        return factory;
    }

    static RetryOperationsInterceptor cancellationRetryAdvice(MessageRecoverer recoverer) {
        return RetryInterceptorBuilder.stateless().maxAttempts(3)
                .backOffOptions(1000, 2, 5000).recoverer(recoverer).build();
    }
}
