package com.indie.catalog.domain.cart.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.catalog.domain.artist.service.FollowerCountSync;
import com.indie.catalog.domain.cart.service.CartService;
import com.indie.catalog.domain.order.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserEventConsumerTest {
    private final CartService cart = mock(CartService.class);
    private final OrderService orders = mock(OrderService.class);
    private final FollowerCountSync counts = mock(FollowerCountSync.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final UserEventConsumer consumer = new UserEventConsumer(cart, orders, new ObjectMapper(), counts);
    private final ConsumerRecord<String, String> event = new ConsumerRecord<>("user.withdrawn", 0, 0, "7",
            "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"payload\":{\"userId\":7}}");

    @Test
    void refreshesCountsBeforeAcknowledgingWithdrawal() throws Exception {
        consumer.onUserWithdrawn(event, ack);
        var order = inOrder(cart, orders, counts, ack);
        order.verify(cart).clear(7);
        order.verify(orders).cancelPendingOf(7);
        order.verify(counts).refreshAll();
        order.verify(ack).acknowledge();
    }

    @Test
    void failedRefreshIsNotAcknowledgedAndCanBeRetried() throws Exception {
        when(counts.refreshAll()).thenThrow(new IllegalStateException("member unavailable")).thenReturn(1);
        assertThatThrownBy(() -> consumer.onUserWithdrawn(event, ack)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(ack);
        consumer.onUserWithdrawn(event, ack);
        verify(counts, times(2)).refreshAll();
        verify(ack).acknowledge();
    }
}
