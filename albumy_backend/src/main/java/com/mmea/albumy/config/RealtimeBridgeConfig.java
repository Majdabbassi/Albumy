package com.mmea.albumy.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmea.albumy.serviceimpl.RealtimeEventsServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Configuration
@Profile("!worker")
public class RealtimeBridgeConfig {

    private static final Logger log = LoggerFactory.getLogger(RealtimeBridgeConfig.class);

    @Bean
    public RealtimeBridge realtimeBridge(SimpMessagingTemplate messagingTemplate) {
        return new RealtimeBridge(messagingTemplate);
    }

    @Bean
    public RedisMessageListenerContainer realtimeListenerContainer(RedisConnectionFactory connectionFactory,
                                                                   RealtimeBridge bridge) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(new MessageListenerAdapter(bridge),
                new PatternTopic(RealtimeEventsServiceImpl.CHANNEL));
        return container;
    }

    static class RealtimeBridge implements org.springframework.data.redis.connection.MessageListener {

        private final SimpMessagingTemplate messagingTemplate;
        private final ObjectMapper objectMapper = new ObjectMapper();

        RealtimeBridge(SimpMessagingTemplate messagingTemplate) {
            this.messagingTemplate = messagingTemplate;
        }

        @Override
        public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
            try {
                String json = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                JsonNode root = objectMapper.readTree(json);
                long eventId = root.path("eventId").asLong();
                String topic = root.path("topic").asText();
                String dest = "/topic/events/" + eventId + "/" + topic;
                // Forward the original JSON text. Sending the JsonNode itself makes Spring's
                // message converter serialize it as a bean ({"array":false,...}), not as JSON.
                messagingTemplate.convertAndSend(dest, json);
            } catch (Exception e) {
                log.warn("Failed to forward realtime event: {}", e.getMessage());
            }
        }
    }
}