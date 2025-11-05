package com.gephub.meets_service.service;

import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.VideoGrant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LiveKitTokenService {
    private final String apiKey;
    private final String apiSecret;

    public LiveKitTokenService(@Value("${gephub.meets.livekit.apiKey:}") String apiKey,
                                @Value("${gephub.meets.livekit.apiSecret:}") String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public String createAccessToken(UUID roomId, String userId, String displayName, String role) {
        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setIdentity(userId);
        token.setName(displayName);
        token.addGrant(new VideoGrant()
                .setRoomJoin(RoomJoin.JOIN)
                .setRoom(roomId.toString())
                .setCanPublish(true)
                .setCanSubscribe(true)
                .setCanPublishData(true));
        if ("host".equals(role) || "moderator".equals(role)) {
            token.addGrant(new VideoGrant()
                    .setRoomJoin(RoomJoin.JOIN)
                    .setRoom(roomId.toString())
                    .setCanPublish(true)
                    .setCanSubscribe(true)
                    .setCanPublishData(true)
                    .setCanUpdateOwnMetadata(true));
        }
        return token.toJwt();
    }
}

