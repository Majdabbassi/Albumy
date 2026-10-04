package com.mmea.albumy.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RealtimeBridgeTest {

    // Regression: the bridge used to forward a parsed JsonNode, which Spring's message converter
    // serialized as a bean ({"array":false,...}), so browsers never saw type/data and ignored it.
    @Test
    void forwardsTheOriginalJsonToTheEventTopic() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        RealtimeBridgeConfig.RealtimeBridge bridge = new RealtimeBridgeConfig.RealtimeBridge(template);
        String json = "{\"topic\":\"photos\",\"eventId\":7,\"type\":\"PHOTO_READY\",\"data\":{\"id\":42}}";

        bridge.onMessage(new DefaultMessage("albumy:events".getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8)), null);

        verify(template).convertAndSend("/topic/events/7/photos", json);
    }
}
