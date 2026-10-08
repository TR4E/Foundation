package me.trae.foundation.spring.common.address;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "foundation.ip-address")
public class IpAddressProperties {

    private List<String> proxyHeaderList = Collections.singletonList("CF-Connecting-IP");
    private List<String> headerList = List.of("X-Forwarded-For", "X-Real-IP", "X-Client-IP", "Forwarded");
}