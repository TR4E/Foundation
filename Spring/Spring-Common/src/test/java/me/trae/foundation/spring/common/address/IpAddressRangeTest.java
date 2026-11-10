package me.trae.foundation.spring.common.address;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IpAddressRangeTest {

    @Test
    void ipv4WholeBytePrefixesIncludeTheirBoundariesOnly() {
        assertRange("192.0.2.8/32", "192.0.2.8", "192.0.2.8", "192.0.2.7", "192.0.2.9");
        assertRange("192.0.2.8/24", "192.0.2.0", "192.0.2.255", "192.0.1.255", "192.0.3.0");
        assertRange("192.7.2.8/16", "192.7.0.0", "192.7.255.255", "192.6.255.255", "192.8.0.0");
        assertRange("192.7.2.8/8", "192.0.0.0", "192.255.255.255", "191.255.255.255", "193.0.0.0");
    }

    @Test
    void ipv4PartialByteMasksRespectBothBoundaries() {
        assertRange("128.1.2.3/1", "128.0.0.0", "255.255.255.255", "127.255.255.255", "0.0.0.0");
        assertRange("126.1.2.3/7", "126.0.0.0", "127.255.255.255", "125.255.255.255", "128.0.0.0");
        assertRange("128.129.2.3/9", "128.128.0.0", "128.255.255.255", "128.127.255.255", "129.0.0.0");
        assertRange("192.0.2.5/31", "192.0.2.4", "192.0.2.5", "192.0.2.3", "192.0.2.6");
        assertRange("192.0.2.169/28", "192.0.2.160", "192.0.2.175", "192.0.2.159", "192.0.2.176");
    }

    @Test
    void zeroPrefixesCoverTheirEntireAddressFamily() {
        final IpAddressRange ipv4Range = IpAddressRange.parse("192.0.2.1/0");
        final IpAddressRange ipv6Range = IpAddressRange.parse("2001:db8::1/0");

        for (final String address : new String[]{"0.0.0.0", "127.0.0.1", "192.0.2.1", "255.255.255.255"}) {
            assertTrue(ipv4Range.contains(InetAddress.ofLiteral(address)));
        }

        for (final String address : new String[]{"::", "::1", "2001:db8::1", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"}) {
            assertTrue(ipv6Range.contains(InetAddress.ofLiteral(address)));
        }

        assertFalse(ipv4Range.contains(InetAddress.ofLiteral("::1")));
        assertFalse(ipv6Range.contains(InetAddress.ofLiteral("192.0.2.1")));
    }

    @Test
    void ipv6PrefixesIncludeOnlyTheirOwnNetwork() {
        assertRange("2001:db8::8/128", "2001:db8::8", "2001:db8::8", "2001:db8::7", "2001:db8::9");
        assertRange("2001:db8:7:8::9/64", "2001:db8:7:8::", "2001:db8:7:8:ffff:ffff:ffff:ffff", "2001:db8:7:7:ffff:ffff:ffff:ffff", "2001:db8:7:9::");
        assertRange("2001:db8:7:8::9/48", "2001:db8:7::", "2001:db8:7:ffff:ffff:ffff:ffff:ffff", "2001:db8:6:ffff:ffff:ffff:ffff:ffff", "2001:db8:8::");
        assertRange("2001:db8:7:8::9/65", "2001:db8:7:8::", "2001:db8:7:8:7fff:ffff:ffff:ffff", "2001:db8:7:7:ffff:ffff:ffff:ffff", "2001:db8:7:8:8000::");
    }

    @Test
    void literalWithoutPrefixMatchesOnlyThatHost() {
        assertRange("192.0.2.8", "192.0.2.8", "192.0.2.8", "192.0.2.7", "192.0.2.9");
        assertRange("2001:db8::8", "2001:db8::8", "2001:db8::8", "2001:db8::7", "2001:db8::9");
    }

    @Test
    void invalidPrefixLengthsAreRejected() {
        for (final String notation : new String[]{"192.0.2.1/-1", "192.0.2.1/33", "2001:db8::1/-1", "2001:db8::1/129"}) {
            assertThrows(IllegalArgumentException.class, () -> IpAddressRange.parse(notation));
        }
    }

    @Test
    void invalidLiteralsAndNonNumericPrefixesAreRejected() {
        for (final String notation : new String[]{"not-an-address", "not-an-address/24", "192.0.2.1/abc", "2001:db8::1/"}) {
            assertThrows(IllegalArgumentException.class, () -> IpAddressRange.parse(notation));
        }
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertRange(" 192.0.2.129 / 25 ", "192.0.2.128", "192.0.2.255", "192.0.2.127", "192.0.3.0");
        assertTrue(IpAddressRange.parse(" 2001:db8::1 ").contains(InetAddress.ofLiteral("2001:db8::1")));
    }

    private static void assertRange(final String notation, final String first, final String last, final String before, final String after) {
        final IpAddressRange ipAddressRange = IpAddressRange.parse(notation);

        assertTrue(ipAddressRange.contains(InetAddress.ofLiteral(first)), notation);
        assertTrue(ipAddressRange.contains(InetAddress.ofLiteral(last)), notation);
        assertFalse(ipAddressRange.contains(InetAddress.ofLiteral(before)), notation);
        assertFalse(ipAddressRange.contains(InetAddress.ofLiteral(after)), notation);
    }
}