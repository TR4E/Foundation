package me.trae.foundation.spring.ratelimit;

import me.trae.foundation.spring.common.address.IpAddressResolver;
import me.trae.foundation.spring.ratelimit.account.EmptyRateLimitAccountResolver;
import me.trae.foundation.spring.ratelimit.account.RateLimitAccountResolver;
import me.trae.foundation.spring.ratelimit.interceptor.RateLimitInterceptor;
import me.trae.foundation.spring.ratelimit.registry.RateLimitRegistry;
import me.trae.foundation.spring.ratelimit.store.MemoryRateLimitStore;
import me.trae.foundation.spring.ratelimit.store.RateLimitStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfiguration
public class RateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RateLimitStore rateLimitStore() {
        return new MemoryRateLimitStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAccountResolver rateLimitAccountResolver() {
        return new EmptyRateLimitAccountResolver();
    }

    @Bean
    public RateLimitRegistry rateLimitRegistry() {
        return new RateLimitRegistry();
    }

    @Bean
    public ApplicationListener<ContextRefreshedEvent> rateLimitRegistrar(final RateLimitRegistry rateLimitRegistry, final RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return _ -> rateLimitRegistry.register(RateLimitRegistry.build(requestMappingHandlerMapping.getHandlerMethods()));
    }

    @Bean
    public WebMvcConfigurer rateLimitWebMvcConfigurer(final RateLimitRegistry rateLimitRegistry, final RateLimitStore rateLimitStore, final IpAddressResolver ipAddressResolver, final RateLimitAccountResolver rateLimitAccountResolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(final InterceptorRegistry interceptorRegistry) {
                interceptorRegistry.addInterceptor(new RateLimitInterceptor(rateLimitRegistry, rateLimitStore, ipAddressResolver, rateLimitAccountResolver));
            }
        };
    }
}