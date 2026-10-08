package me.trae.foundation.spring.security.headers;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContentSecurityPolicyBuilderTest {

    @Test
    void buildsTheLockedDownDefaults() {
        final String policy = ContentSecurityPolicyBuilder.build(new SecurityHeadersProperties());

        assertTrue(policy.contains("default-src 'self'"));
        assertTrue(policy.contains("object-src 'none'"));
        assertTrue(policy.contains("frame-ancestors 'none'"));
        assertTrue(policy.contains("img-src 'self' data:"));
    }

    @Test
    void directivesAreSeparatedBySemicolons() {
        final SecurityHeadersProperties properties = empty();
        properties.setDefaultSrc(List.of("'self'"));
        properties.setScriptSrc(List.of("'self'", "https://cdn.example.com"));

        assertEquals("default-src 'self'; script-src 'self' https://cdn.example.com", ContentSecurityPolicyBuilder.build(properties));
    }

    @Test
    void emptyDirectivesAreDropped() {
        final SecurityHeadersProperties properties = empty();
        properties.setScriptSrc(List.of("'self'"));

        final String policy = ContentSecurityPolicyBuilder.build(properties);

        assertEquals("script-src 'self'", policy);
        assertFalse(policy.contains("default-src"));
    }

    @Test
    void nullDirectivesAreDropped() {
        final SecurityHeadersProperties properties = empty();
        properties.setDefaultSrc(null);
        properties.setScriptSrc(List.of("'self'"));

        assertEquals("script-src 'self'", ContentSecurityPolicyBuilder.build(properties));
    }

    @Test
    void everyDirectiveEmptyGivesAnEmptyPolicy() {
        assertTrue(ContentSecurityPolicyBuilder.build(empty()).isEmpty());
    }

    private static SecurityHeadersProperties empty() {
        final SecurityHeadersProperties properties = new SecurityHeadersProperties();

        properties.setDefaultSrc(Collections.emptyList());
        properties.setBaseUri(Collections.emptyList());
        properties.setObjectSrc(Collections.emptyList());
        properties.setFrameAncestors(Collections.emptyList());
        properties.setFormAction(Collections.emptyList());
        properties.setScriptSrc(Collections.emptyList());
        properties.setStyleSrc(Collections.emptyList());
        properties.setFontSrc(Collections.emptyList());
        properties.setImgSrc(Collections.emptyList());
        properties.setConnectSrc(Collections.emptyList());
        properties.setFrameSrc(Collections.emptyList());
        properties.setChildSrc(Collections.emptyList());

        return properties;
    }
}
