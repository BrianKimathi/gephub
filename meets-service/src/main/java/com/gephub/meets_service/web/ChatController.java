package com.gephub.meets_service.web;

import com.gephub.meets_service.domain.Message;
import com.gephub.meets_service.repository.MessageRepository;
import com.gephub.meets_service.repository.RoomRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;

@Controller
public class ChatController {
    private final MessageRepository messages;
    private final RoomRepository rooms;
    private final RedisTemplate<String, Object> redis;

    public ChatController(MessageRepository messages, RoomRepository rooms, RedisTemplate<String, Object> redis) {
        this.messages = messages;
        this.rooms = rooms;
        this.redis = redis;
    }

    @MessageMapping("/rooms/{roomId}/chat")
    @SendTo("/topic/rooms/{roomId}/chat")
    @Transactional
    public ChatMessage chat(@Payload ChatMessage msg, @org.springframework.messaging.handler.annotation.DestinationVariable UUID roomId, SimpMessageHeaderAccessor accessor) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        var room = rooms.findById(roomId).orElse(null);
        if (room == null || room.getEndedAt() != null) return null;
        Message m = new Message();
        m.setId(UUID.randomUUID());
        m.setRoomId(roomId);
        m.setUserId(jwt.getSubject());
        m.setDisplayName(msg.displayName());
        m.setContent(msg.content());
        m.setMessageType(msg.type() == null ? "text" : msg.type());
        messages.save(m);
        redis.convertAndSend("room:" + roomId + ":chat", Map.of("id", m.getId().toString(), "userId", m.getUserId(), "displayName", m.getDisplayName(), "content", m.getContent(), "type", m.getMessageType(), "createdAt", m.getCreatedAt().toString()));
        return new ChatMessage(msg.id(), msg.displayName(), msg.content(), msg.type(), m.getCreatedAt().toString());
    }

    public record ChatMessage(String id, String displayName, String content, String type, String createdAt) {}
}

