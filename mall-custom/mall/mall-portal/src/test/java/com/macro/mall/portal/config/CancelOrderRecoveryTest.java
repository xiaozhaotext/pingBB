package com.macro.mall.portal.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.aop.framework.ProxyFactory;
import java.util.concurrent.atomic.AtomicInteger;

class CancelOrderRecoveryTest {
    interface Listener { void accept(Channel channel, Message message); }

    @Test
    void threeFailedAttemptsTransferOriginalMessageToRecovery() {
        AtomicInteger attempts = new AtomicInteger();
        RuntimeException failure = new RuntimeException("invalid reservation");
        MessageRecoverer recovery = mock(MessageRecoverer.class);
        Listener listener = (channel, message) -> { attempts.incrementAndGet(); throw failure; };
        ProxyFactory proxy = new ProxyFactory(listener);
        proxy.addAdvice(CancelOrderRecoveryConfig.cancellationRetryAdvice(recovery));
        Message message = new Message(new byte[]{42});
        ((Listener) proxy.getProxy()).accept(null, message);
        assertThat(attempts.get()).isEqualTo(3);
        verify(recovery).recover(message, failure);
    }

    @Test
    void failedRecoveryRemainsAnErrorRatherThanAcknowledgingLostMessage() {
        MessageRecoverer recovery = mock(MessageRecoverer.class);
        doThrow(new IllegalStateException("broker confirm failed")).when(recovery).recover(any(), any());
        Listener listener = (channel, message) -> { throw new RuntimeException("invalid reservation"); };
        ProxyFactory proxy = new ProxyFactory(listener);
        proxy.addAdvice(CancelOrderRecoveryConfig.cancellationRetryAdvice(recovery));
        assertThatThrownBy(() -> ((Listener) proxy.getProxy()).accept(null, new Message(new byte[]{42})))
                .isInstanceOf(IllegalStateException.class).hasMessage("broker confirm failed");
    }
    @Test
    void autoConfigurationRetainsDefaultTemplateAndEnablesConfirmedRecovery() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
                .withUserConfiguration(CancelOrderRecoveryConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("rabbitTemplate");
                    assertThat(context.getBean("cancelOrderContainerFactory"))
                            .isInstanceOf(SimpleRabbitListenerContainerFactory.class);
                    assertThat(context.getBean(CachingConnectionFactory.class).isPublisherConfirms()).isTrue();
                    assertThat(context.getBean(CachingConnectionFactory.class).isPublisherReturns()).isTrue();
                    Queue queue = context.getBean("cancelFailureQueue", Queue.class);
                    assertThat(queue.isDurable()).isTrue();
                    assertThat(queue.isAutoDelete()).isFalse();
                    assertThat(queue.getArguments()).doesNotContainKeys("x-message-ttl", "x-max-length");
                    DirectExchange exchange = context.getBean("cancelFailureExchange", DirectExchange.class);
                    assertThat(exchange.isDurable()).isTrue();
                    Binding binding = context.getBean("cancelFailureBinding", Binding.class);
                    assertThat(binding.getDestination()).isEqualTo(queue.getName());
                    assertThat(binding.getExchange()).isEqualTo(exchange.getName());
                    assertThat(binding.getRoutingKey()).isEqualTo(CancelOrderRecoveryConfig.FAILED);
                });
    }

    @Test
    void unsupportedConnectionFactoryFailsClosed() {
        assertThatThrownBy(() -> new CancelOrderRecoveryConfig().cancelOrderContainerFactory(
                null, mock(ConnectionFactory.class)))
                .isInstanceOf(IllegalStateException.class);
    }
}
