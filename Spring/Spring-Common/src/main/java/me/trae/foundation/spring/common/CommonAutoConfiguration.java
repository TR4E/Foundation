package me.trae.foundation.spring.common;

import me.trae.foundation.spring.common.address.IpAddressProperties;
import me.trae.foundation.spring.common.address.IpAddressResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(IpAddressProperties.class)
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IpAddressResolver ipAddressResolver(final IpAddressProperties ipAddressProperties) {
        return new IpAddressResolver(ipAddressProperties);
    }
}