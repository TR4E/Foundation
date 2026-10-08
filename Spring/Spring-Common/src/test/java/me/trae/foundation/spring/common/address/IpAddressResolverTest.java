package me.trae.foundation.spring.common.address;

import me.trae.foundation.spring.common.FoundationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IpAddressResolverTest {

    private static IpAddressResolver resolver(final boolean production) {
        final FoundationProperties foundationProperties = new FoundationProperties();
        foundationProperties.setProduction(production);

        return new IpAddressResolver(foundationProperties, new IpAddressProperties());
    }

    @Test
    void productionReadsOnlyTheProxyHeader() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertTrue(resolver(true).getIpAddressByRequest(request).isEmpty());

        request.addHeader("CF-Connecting-IP", "203.0.113.10");

        assertEquals(Optional.of("203.0.113.10"), resolver(true).getIpAddressByRequest(request));
    }

    @Test
    void developmentReadsTheOrdinaryHeaders() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "192.168.1.50");

        assertEquals(Optional.of("192.168.1.50"), resolver(false).getIpAddressByRequest(request));
    }

    @Test
    void developmentIgnoresTheProxyHeader() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "203.0.113.10");

        assertTrue(resolver(false).getIpAddressByRequest(request).isEmpty());
    }

    @Test
    void splitsCommaSeparatedValuesWithoutSpaces() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.1,203.0.113.9");

        assertEquals(Optional.of("10.0.0.1"), resolver(false).getIpAddressByRequest(request));
    }

    @Test
    void productionSkipsPrivateEntriesAndTakesTheFirstPublicOne() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "10.0.0.1, 192.168.1.1, 203.0.113.9");

        assertEquals(Optional.of("203.0.113.9"), resolver(true).getIpAddressByRequest(request));
    }

    @Test
    void productionRejectsLoopbackAndLinkLocalAndMulticast() {
        assertTrue(resolve(true, "127.0.0.1").isEmpty());
        assertTrue(resolve(true, "0.0.0.0").isEmpty());
        assertTrue(resolve(true, "169.254.1.1").isEmpty());
        assertTrue(resolve(true, "224.0.0.1").isEmpty());
        assertTrue(resolve(true, "::1").isEmpty());
    }

    @Test
    void productionRejectsIpv4MappedPrivateAddresses() {
        assertTrue(resolve(true, "::ffff:127.0.0.1").isEmpty());
        assertTrue(resolve(true, "::ffff:10.0.0.1").isEmpty());
    }

    @Test
    void productionRejectsIpv6UniqueLocal() {
        assertTrue(resolve(true, "fd00::1").isEmpty());
        assertTrue(resolve(true, "fc00::1").isEmpty());
    }

    @Test
    void productionAcceptsCarrierGradeNat() {
        assertEquals(Optional.of("100.64.0.1"), resolve(true, "100.64.0.1"));
    }

    @Test
    void productionAcceptsPublicIpv6() {
        assertEquals(Optional.of("2001:db8::1"), resolve(true, "2001:db8::1"));
    }

    @Test
    void skipsUnparseableEntries() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "not-an-address, 203.0.113.9");

        assertEquals(Optional.of("203.0.113.9"), resolver(true).getIpAddressByRequest(request));
    }

    @Test
    void neverFallsBackToRemoteAddress() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");

        assertTrue(resolver(true).getIpAddressByRequest(request).isEmpty());
        assertTrue(resolver(false).getIpAddressByRequest(request).isEmpty());
    }

    @Test
    void blankHeaderIsSkipped() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "   ");

        assertTrue(resolver(true).getIpAddressByRequest(request).isEmpty());
    }

    @Test
    void configuredHeaderListIsHonoured() {
        final FoundationProperties foundationProperties = new FoundationProperties();
        foundationProperties.setProduction(true);

        final IpAddressProperties ipAddressProperties = new IpAddressProperties();
        ipAddressProperties.setProxyHeaderList(List.of("True-Client-IP"));

        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("True-Client-IP", "203.0.113.9");

        assertEquals(Optional.of("203.0.113.9"), new IpAddressResolver(foundationProperties, ipAddressProperties).getIpAddressByRequest(request));
    }

    private static Optional<String> resolve(final boolean production, final String address) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(production ? "CF-Connecting-IP" : "X-Forwarded-For", address);

        return resolver(production).getIpAddressByRequest(request);
    }
}
