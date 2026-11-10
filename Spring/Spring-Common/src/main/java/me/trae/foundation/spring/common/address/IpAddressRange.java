package me.trae.foundation.spring.common.address;

import lombok.AllArgsConstructor;

import java.net.InetAddress;

@AllArgsConstructor
public final class IpAddressRange {

    private final byte[] networkBytes;
    private final int prefixLength;

    public boolean contains(final InetAddress address) {
        final byte[] bytes = address.getAddress();

        if (bytes.length != this.networkBytes.length) {
            return false;
        }

        if (!this.matchesFullBytes(bytes)) {
            return false;
        }

        return this.matchesRemainingBits(bytes);
    }

    private boolean matchesFullBytes(final byte[] bytes) {
        for (int index = 0; index < this.prefixLength / 8; index++) {
            if (bytes[index] != this.networkBytes[index]) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesRemainingBits(final byte[] bytes) {
        final int remainingBits = this.prefixLength % 8;

        if (remainingBits == 0) {
            return true;
        }

        final int index = this.prefixLength / 8;
        final int mask = 0xFF << (8 - remainingBits);

        return (bytes[index] & mask) == (this.networkBytes[index] & mask);
    }

    public static IpAddressRange parse(final String notation) {
        final int index = notation.indexOf('/');

        final byte[] bytes = InetAddress.ofLiteral((index < 0 ? notation : notation.substring(0, index)).trim()).getAddress();

        if (index < 0) {
            return new IpAddressRange(bytes, bytes.length * 8);
        }

        final int prefixLength = Integer.parseInt(notation.substring(index + 1).trim());

        if (prefixLength < 0 || prefixLength > bytes.length * 8) {
            throw new IllegalArgumentException("Invalid prefix length in CIDR notation: %s".formatted(notation));
        }

        return new IpAddressRange(bytes, prefixLength);
    }
}