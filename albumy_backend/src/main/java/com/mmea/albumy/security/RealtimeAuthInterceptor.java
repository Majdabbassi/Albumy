package com.mmea.albumy.security;

import com.mmea.albumy.model.User;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

@Component
public class RealtimeAuthInterceptor implements ChannelInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String EVENT_TOPIC_PATTERN = "/topic/events/*/*";

    private final JwtUtil jwtUtil;
    private final RealtimeTicketService realtimeTicketService;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    public RealtimeAuthInterceptor(JwtUtil jwtUtil, RealtimeTicketService realtimeTicketService,
                                   UserRepository userRepository, EventRepository eventRepository) {
        this.jwtUtil = jwtUtil;
        this.realtimeTicketService = realtimeTicketService;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null || !PATH_MATCHER.match(EVENT_TOPIC_PATTERN, destination)) {
            return message;
        }
        long eventId = extractEventId(destination);
        if (eventId <= 0) {
            return null;
        }
        return isAuthorized(eventId, accessor) ? message : null;
    }

    private long extractEventId(String destination) {
        String[] parts = destination.split("/", -1);
        if (parts.length < 4) {
            return -1;
        }
        try {
            return Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private boolean isAuthorized(long eventId, StompHeaderAccessor accessor) {
        String authorization = firstHeader(accessor, "Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            if (!jwtUtil.validateToken(token)) {
                return false;
            }
            String username = jwtUtil.getUsernameFromToken(token);
            return userRepository.findByUsername(username)
                    .filter(user -> canAccessEvent(user, eventId))
                    .isPresent();
        }
        String ticket = firstHeader(accessor, "X-Realtime-Token");
        return ticket != null && realtimeTicketService.isValid(eventId, ticket);
    }

    private boolean canAccessEvent(User user, long eventId) {
        if (user.getRole() == User.Role.ADMIN) {
            return true;
        }
        return eventRepository.findById(eventId)
                .map(event -> event.getOrganizer().getId().equals(user.getId()))
                .orElse(false);
    }

    private String firstHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}