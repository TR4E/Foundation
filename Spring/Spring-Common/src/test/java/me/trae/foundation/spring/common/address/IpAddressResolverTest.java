package me.trae.foundation.spring.common.address;

import me.trae.foundation.spring.common.FoundationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
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

    @Test
    void emptyTrustListPreservesHeadersFromAnyPeerInProduction() {
        assertTrue(new IpAddressProperties().getTrustedProxyList().isEmpty());

        for (final String peer : new String[]{"203.0.113.1", "10.0.0.1", "not-a-peer"}) {
            assertEquals(Optional.of("203.0.113.9"), proxyResolver(true, Collections.emptyList()).getIpAddressByRequest(proxyRequest(peer, "CF-Connecting-IP", "203.0.113.9")));
        }
    }

    @Test
    void emptyTrustListPreservesDevelopmentHeaderPrecedence() {
        final MockHttpServletRequest httpServletRequest = proxyRequest("198.51.100.1", "X-Forwarded-For", "192.168.1.2");
        httpServletRequest.addHeader("X-Real-IP", "203.0.113.9");

        assertEquals(Optional.of("192.168.1.2"), proxyResolver(false, Collections.emptyList()).getIpAddressByRequest(httpServletRequest));
    }

    @Test
    void peersInAnyConfiguredRangeCanSupplyForwardedHeaders() {
        final IpAddressResolver ipAddressResolver = proxyResolver(true, List.of("192.0.2.0/24", "198.51.100.0/24"));

        assertEquals(Optional.of("203.0.113.9"), ipAddressResolver.getIpAddressByRequest(proxyRequest("198.51.100.8", "CF-Connecting-IP", "203.0.113.9")));
    }

    @Test
    void untrustedPublicPeersCannotForgeTheirIdentityThroughHeaders() {
        assertEquals(Optional.of("198.51.100.8"), proxyResolver(true, Collections.singletonList("192.0.2.0/24")).getIpAddressByRequest(proxyRequest("198.51.100.8", "CF-Connecting-IP", "203.0.113.9")));
    }

    @Test
    void untrustedPrivatePeersAreRejectedInProduction() {
        assertTrue(proxyResolver(true, Collections.singletonList("192.0.2.0/24")).getIpAddressByRequest(proxyRequest("10.0.0.8", "CF-Connecting-IP", "203.0.113.9")).isEmpty());
    }

    @Test
    void untrustedPrivatePeersAreReturnedInDevelopment() {
        assertEquals(Optional.of("10.0.0.8"), proxyResolver(false, Collections.singletonList("192.0.2.0/24")).getIpAddressByRequest(proxyRequest("10.0.0.8", "X-Forwarded-For", "203.0.113.9")));
    }

    @Test
    void unparseablePeersAreUntrustedAndHaveNoFallbackIdentity() {
        assertTrue(proxyResolver(true, Collections.singletonList("192.0.2.0/24")).getIpAddressByRequest(proxyRequest("not-a-peer", "CF-Connecting-IP", "203.0.113.9")).isEmpty());
    }

    @Test
    void aTrustedPeerWithoutTheExpectedHeaderFailsClosed() {
        final MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setRemoteAddr("192.0.2.8");

        assertTrue(proxyResolver(true, Collections.singletonList("192.0.2.0/24")).getIpAddressByRequest(httpServletRequest).isEmpty());
    }

    @Test
    void commaSeparatedHeadersSkipInvalidEntriesAndKeepTheFirstAcceptableOne() {
        assertEquals(Optional.of("203.0.113.9"), proxyResolver(true, Collections.singletonList("192.0.2.0/24")).getIpAddressByRequest(proxyRequest("192.0.2.8", "CF-Connecting-IP", "bad, 10.0.0.1, 203.0.113.9, 198.51.100.8")));
        assertEquals(Optional.of("10.0.0.1"), proxyResolver(false, Collections.singletonList("192.0.2.0/24")).getIpAddressByRequest(proxyRequest("192.0.2.8", "X-Forwarded-For", "bad, 10.0.0.1, 203.0.113.9")));
    }

    @Test
    void productionRejectsEveryNonPublicHeaderAddressCategory() {
        for (final String address : new String[]{"127.0.0.1", "::1", "0.0.0.0", "::", "10.0.0.1", "192.168.0.1", "169.254.0.1", "fe80::1", "fc00::1", "fd00::1", "::ffff:192.168.0.1"}) {
            assertTrue(proxyResolver(true, Collections.singletonList("192.0.2.0/24")).getIpAddressByRequest(proxyRequest("192.0.2.8", "CF-Connecting-IP", address)).isEmpty(), address);
        }
    }

    @Test
    void mappedPublicAddressesAreAcceptedAfterPublicAddressValidation() {
        assertEquals(Optional.of("::ffff:8.8.8.8"), proxyResolver(true, Collections.singletonList("192.0.2.0/24")).getIpAddressByRequest(proxyRequest("192.0.2.8", "CF-Connecting-IP", "::ffff:8.8.8.8")));
    }

    @Test
    void developmentAcceptsPrivateAddressesFromTrustedPeers() {
        assertEquals(Optional.of("192.168.0.8"), proxyResolver(false, Collections.singletonList("192.0.2.0/24")).getIpAddressByRequest(proxyRequest("192.0.2.8", "X-Forwarded-For", "192.168.0.8")));
    }

    @Test
    void ipv6TrustedRangesMatchIpv6Peers() {
        assertEquals(Optional.of("2001:db8:9::1"), proxyResolver(true, Collections.singletonList("2001:db8:7::/48")).getIpAddressByRequest(proxyRequest("2001:db8:7:8::1", "CF-Connecting-IP", "2001:db8:9::1")));
    }

    private static IpAddressResolver proxyResolver(final boolean production, final List<String> trustedProxyList) {
        final FoundationProperties foundationProperties = new FoundationProperties();
        foundationProperties.setProduction(production);
        final IpAddressProperties ipAddressProperties = new IpAddressProperties();
        ipAddressProperties.setTrustedProxyList(trustedProxyList);

        return new IpAddressResolver(foundationProperties, ipAddressProperties);
    }

    private static MockHttpServletRequest proxyRequest(final String peer, final String header, final String address) {
        final MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setRemoteAddr(peer);
        httpServletRequest.addHeader(header, address);

        return httpServletRequest;
    }

    private static Optional<String> resolve(final boolean production, final String address) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(production ? "CF-Connecting-IP" : "X-Forwarded-For", address);

        return resolver(production).getIpAddressByRequest(request);
    }
}