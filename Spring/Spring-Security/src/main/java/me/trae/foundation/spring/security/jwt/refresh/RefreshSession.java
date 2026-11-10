package me.trae.foundation.spring.security.jwt.refresh;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public final class RefreshSession {

    private final UUID sessionId;
    private final String tokenHash, previousTokenHash;
    private final long expireAt, rotatedAt;
}