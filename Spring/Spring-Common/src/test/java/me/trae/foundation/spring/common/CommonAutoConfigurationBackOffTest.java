package me.trae.foundation.spring.common;

import me.trae.foundation.spring.common.address.IpAddressProperties;
import me.trae.foundation.spring.common.address.IpAddressResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest
final class CommonAutoConfigurationBackOffTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private IpAddressResolver ipAddressResolver;

    @Test
    void anApplicationBeanReplacesTheDefault() {
        assertEquals(1, this.applicationContext.getBeansOfType(IpAddressResolver.class).size());
        assertSame(TestConfiguration.RESOLVER, this.ipAddressResolver);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfiguration {

        static final IpAddressResolver RESOLVER = new IpAddressResolver(new FoundationProperties(), new IpAddressProperties());

        @Bean
        public IpAddressResolver ipAddressResolver() {
            return RESOLVER;
        }
    }
}
