package me.trae.foundation.spring.security.jwt.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.spring.security.jwt.JwtProperties;

import java.time.Duration;
import java.util.function.Function;

@AllArgsConstructor
@Getter
public enum TokenType {

    ACCESS_TOKEN("access_token", JwtProperties::getAccessTokenCookieName, JwtProperties::getAccessTokenDuration),
    REFRESH_TOKEN("refresh_token", JwtProperties::getRefreshTokenCookieName, JwtProperties::getRefreshTokenDuration);

    private final String key;
    private final Function<JwtProperties, String> cookieNameFunction;
    private final Function<JwtProperties, Duration> durationFunction;
}