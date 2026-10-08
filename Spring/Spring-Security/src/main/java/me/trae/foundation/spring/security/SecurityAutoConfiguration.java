package me.trae.foundation.spring.security;

import jakarta.servlet.DispatcherType;
import me.trae.foundation.spring.common.CommonAutoConfiguration;
import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.common.constants.FilterOrderConstants;
import me.trae.foundation.spring.security.csrf.CsrfProperties;
import me.trae.foundation.spring.security.csrf.filter.CsrfFilter;
import me.trae.foundation.spring.security.csrf.registry.CsrfRegistry;
import me.trae.foundation.spring.security.csrf.service.CsrfTokenService;
import me.trae.foundation.spring.security.headers.SecurityHeadersFilter;
import me.trae.foundation.spring.security.headers.SecurityHeadersProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.EnumSet;

@EnableConfigurationProperties({SecurityHeadersProperties.class, CsrfProperties.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@AutoConfiguration(after = CommonAutoConfiguration.class)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CsrfTokenService csrfTokenService(final FoundationProperties foundationProperties, final CsrfProperties csrfProperties) {
        return new CsrfTokenService(foundationProperties, csrfProperties);
    }

    @Bean
    public CsrfRegistry csrfRegistry() {
        return new CsrfRegistry();
    }

    @Bean
    public ApplicationListener<ContextRefreshedEvent> csrfRegistrar(final CsrfRegistry csrfRegistry, final RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return _ -> csrfRegistry.register(CsrfRegistry.build(requestMappingHandlerMapping.getHandlerMethods()));
    }

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration(final FoundationProperties foundationProperties, final SecurityHeadersProperties securityHeadersProperties) {
        final FilterRegistrationBean<SecurityHeadersFilter> registrationBean = new FilterRegistrationBean<>(new SecurityHeadersFilter(foundationProperties, securityHeadersProperties));

        registrationBean.setOrder(FilterOrderConstants.SECURITY_HEADERS);
        registrationBean.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC));
        registrationBean.addUrlPatterns("/*");

        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<CsrfFilter> csrfFilterRegistration(final CsrfProperties csrfProperties, final CsrfTokenService csrfTokenService, final CsrfRegistry csrfRegistry) {
        final FilterRegistrationBean<CsrfFilter> registrationBean = new FilterRegistrationBean<>(new CsrfFilter(csrfProperties, csrfTokenService, csrfRegistry));

        registrationBean.setOrder(FilterOrderConstants.CSRF);
        registrationBean.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        registrationBean.addUrlPatterns("/*");

        return registrationBean;
    }
}