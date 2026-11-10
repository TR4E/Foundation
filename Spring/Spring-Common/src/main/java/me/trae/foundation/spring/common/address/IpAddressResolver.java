package me.trae.foundation.spring.common.address;

import jakarta.servlet.http.HttpServletRequest;
import me.trae.foundation.spring.common.FoundationProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;

public final class IpAddressResolver {

    private final FoundationProperties foundationProperties;
    private final List<String> headerList;
    private final List<IpAddressRange> trustedProxyList;

    public IpAddressResolver(final FoundationProperties foundationProperties, final IpAddressProperties ipAddressProperties) {
        this.foundationProperties = foundationProperties;
        this.headerList = List.copyOf(this.foundationProperties.isProduction() ? ipAddressProperties.getProxyHeaderList() : ipAddressProperties.getHeaderList());
        this.trustedProxyList = ipAddressProperties.getTrustedProxyList().stream().map(IpAddressRange::parse).toList();
    }

    public Optional<String> getIpAddressByRequest(final HttpServletRequest httpServletRequest) {
        if (!this.isTrustedPeer(httpServletRequest)) {
            return this.getPeerAddress(httpServletRequest);
        }

        for (final String header : this.headerList) {
            final String value = httpServletRequest.getHeader(header);

            if (value == null || value.isBlank()) {
                continue;
            }

            for (final String entry : value.split(",")) {
                final String address = entry.trim();

                if (!this.isAcceptable(address)) {
                    continue;
                }

                return Optional.of(address);
            }
        }

        return Optional.empty();
    }

    private boolean isTrustedPeer(final HttpServletRequest httpServletRequest) {
        if (this.trustedProxyList.isEmpty()) {
            return true;
        }

        return this.matchesTrustedProxy(this.parseLiteral(httpServletRequest.getRemoteAddr()));
    }

    private boolean matchesTrustedProxy(final InetAddress peer) {
        if (peer == null) {
            return false;
        }

        for (final IpAddressRange range : this.trustedProxyList) {
            if (range.contains(peer)) {
                return true;
            }
        }

        return false;
    }

    private Optional<String> getPeerAddress(final HttpServletRequest httpServletRequest) {
        final String address = httpServletRequest.getRemoteAddr();

        if (address == null || !this.isAcceptable(address)) {
            return Optional.empty();
        }

        return Optional.of(address);
    }

    private boolean isAcceptable(final String address) {
        final InetAddress inetAddress = this.parseLiteral(address);
        if (inetAddress == null) {
            return false;
        }

        return !this.foundationProperties.isProduction() || this.isPublic(inetAddress);
    }

    private InetAddress parseLiteral(final String address) {
        try {
            return InetAddress.ofLiteral(address);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isPublic(final InetAddress address) {
        final InetAddress resolved = this.unmap(address);

        if (resolved.isAnyLocalAddress()) {
            return false;
        }

        if (resolved.isLoopbackAddress()) {
            return false;
        }

        if (resolved.isSiteLocalAddress()) {
            return false;
        }

        if (resolved.isLinkLocalAddress()) {
            return false;
        }

        if (resolved.isMulticastAddress()) {
            return false;
        }

        if (this.isUniqueLocal(resolved)) {
            return false;
        }

        return true;
    }

    private boolean isUniqueLocal(final InetAddress address) {
        final byte[] bytes = address.getAddress();

        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private InetAddress unmap(final InetAddress address) {
        final byte[] bytes = address.getAddress();

        if (bytes.length != 16) {
            return address;
        }

        for (int index = 0; index < 10; index++) {
            if (bytes[index] == 0) {
                continue;
            }

            return address;
        }

        if ((bytes[10] & 0xFF) != 0xFF || (bytes[11] & 0xFF) != 0xFF) {
            return address;
        }

        try {
            return InetAddress.getByAddress(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
        } catch (final UnknownHostException exception) {
            return address;
        }
    }
}