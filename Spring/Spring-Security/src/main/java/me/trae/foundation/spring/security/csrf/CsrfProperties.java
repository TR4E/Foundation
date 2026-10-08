package me.trae.foundation.spring.security.csrf;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "foundation.security.csrf")
public class CsrfProperties {

    private boolean requireSameOrigin = true;

    private String cookieName = "XSRF-TOKEN";
    private String headerName = "X-XSRF-TOKEN";

    private Duration cookieDuration = Duration.ofDays(7);
}