package me.trae.foundation.spring.common;

import me.trae.foundation.spring.common.address.IpAddressProperties;
import me.trae.foundation.spring.common.address.IpAddressResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "foundation.production=true",
        "foundation.ip-address.proxy-header-list[0]=CF-Connecting-IP",
        "foundation.ip-address.proxy-header-list[1]=True-Client-IP"
})
final class CommonAutoConfigurationTest {

    @Autowired
    private IpAddressResolver ipAddressResolver;

    @Autowired
    private FoundationProperties foundationProperties;

    @Autowired
    private IpAddressProperties ipAddressProperties;

    @Test
    void theResolverIsRegisteredOnTheClasspathAlone() {
        assertTrue(this.ipAddressResolver != null);
    }

    @Test
    void theProductionFlagBinds() {
        assertTrue(this.foundationProperties.isProduction());
    }

    @Test
    void theHeaderListBinds() {
        assertEquals(List.of("CF-Connecting-IP", "True-Client-IP"), this.ipAddressProperties.getProxyHeaderList());
    }

    @Test
    void theBoundHeaderListIsTheOneTheResolverUses() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("True-Client-IP", "203.0.113.9");

        assertEquals(Optional.of("203.0.113.9"), this.ipAddressResolver.getIpAddressByRequest(request));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfiguration {
    }
}
