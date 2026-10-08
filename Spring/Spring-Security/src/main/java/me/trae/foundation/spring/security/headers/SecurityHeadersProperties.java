package me.trae.foundation.spring.security.headers;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "foundation.security.headers")
public class SecurityHeadersProperties {

    private String contentTypeOptions = "nosniff";
    private String referrerPolicy = "strict-origin-when-cross-origin";
    private String permissionsPolicy = "camera=(), microphone=(), geolocation=()";
    private String strictTransportSecurity = "max-age=31536000; includeSubDomains; preload";
    private String frameOptions = "DENY";

    private boolean contentSecurityPolicyEnabled = true;

    private List<String> defaultSrc = List.of("'self'");
    private List<String> baseUri = List.of("'self'");
    private List<String> objectSrc = List.of("'none'");
    private List<String> frameAncestors = List.of("'none'");
    private List<String> formAction = List.of("'self'");
    private List<String> scriptSrc = List.of("'self'");
    private List<String> styleSrc = List.of("'self'");
    private List<String> fontSrc = List.of("'self'");
    private List<String> imgSrc = List.of("'self'", "data:");
    private List<String> connectSrc = List.of("'self'");
    private List<String> frameSrc = List.of("'none'");
    private List<String> childSrc = List.of("'none'");
}