package me.trae.foundation.spring.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "foundation.security.jwt")
public class JwtProperties {

    private String issuer = "foundation";
    private String audience = "foundation";

    private String accessTokenCookieName = "ACCESS-TOKEN";
    private String refreshTokenCookieName = "REFRESH-TOKEN";
    private String fingerprintCookieName = "FINGERPRINT";

    private String accessTokenKeySeed;
    private String refreshTokenKeySeed;

    private Duration accessTokenDuration = Duration.ofMinutes(15);
    private Duration refreshTokenDuration = Duration.ofDays(7);

    private Duration clockSkew = Duration.ofSeconds(5);
    private Duration rotationGrace = Duration.ofSeconds(5);

    private int maxSessions = 10;
}