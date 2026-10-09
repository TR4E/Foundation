package me.trae.foundation.spring.pages;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import me.trae.foundation.spring.common.CommonAutoConfiguration;
import me.trae.foundation.spring.common.constants.FilterOrderConstants;
import me.trae.foundation.spring.pages.asset.AssetProvider;
import me.trae.foundation.spring.pages.filter.PageCacheControlFilter;
import me.trae.foundation.spring.pages.filter.PageTrailingSlashFilter;
import me.trae.foundation.spring.pages.filter.RobotsTagFilter;
import me.trae.foundation.spring.pages.filter.StaticResourceFilter;
import me.trae.foundation.spring.pages.interceptor.PageAccessInterceptor;
import me.trae.foundation.spring.pages.navbar.NavbarService;
import me.trae.foundation.spring.pages.pagination.PaginationService;
import me.trae.foundation.spring.pages.registry.PageRegistrar;
import me.trae.foundation.spring.pages.registry.PageRegistry;
import me.trae.foundation.spring.pages.registry.PageRegistryBuilder;
import me.trae.foundation.spring.pages.role.DenyPageAccessResolver;
import me.trae.foundation.spring.pages.role.PageAccessResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@EnableConfigurationProperties(PageProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@AutoConfiguration(after = CommonAutoConfiguration.class)
public class PagesAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PageAccessResolver pageAccessResolver() {
        return new DenyPageAccessResolver();
    }

    @Bean
    public PageRegistry pageRegistry() {
        return new PageRegistry();
    }

    @Bean
    public NavbarService navbarService(final PageRegistry pageRegistry, final PageAccessResolver pageAccessResolver) {
        return new NavbarService(pageRegistry, pageAccessResolver);
    }

    @Bean
    public PaginationService paginationService(final PageProperties pageProperties) {
        return new PaginationService(pageProperties);
    }

    @Bean
    public ApplicationListener<ContextRefreshedEvent> pageRegistrar(final PageRegistry pageRegistry, final RequestMappingHandlerMapping requestMappingHandlerMapping, final List<Page> pageList, final List<AssetProvider> assetProviderList) {
        final AtomicBoolean registered = new AtomicBoolean();

        return _ -> {
            if (!registered.compareAndSet(false, true)) {
                return;
            }

            pageRegistry.register(PageRegistryBuilder.build(pageList, assetProviderList));

            new PageRegistrar(requestMappingHandlerMapping).register(pageList);
        };
    }

    @Bean
    public WebMvcConfigurer pageWebMvcConfigurer(final PageProperties pageProperties, final PageAccessResolver pageAccessResolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(final InterceptorRegistry interceptorRegistry) {
                interceptorRegistry.addInterceptor(new PageAccessInterceptor(pageProperties, pageAccessResolver));
            }
        };
    }

    @Bean
    public FilterRegistrationBean<RobotsTagFilter> robotsTagFilterRegistration(final PageProperties pageProperties) {
        return registration(new RobotsTagFilter(pageProperties), FilterOrderConstants.ROBOTS_TAG, EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR));
    }

    @Bean
    public FilterRegistrationBean<PageCacheControlFilter> pageCacheControlFilterRegistration(final PageProperties pageProperties, final PageRegistry pageRegistry) {
        return registration(new PageCacheControlFilter(pageProperties, pageRegistry), FilterOrderConstants.PAGE_CACHE_CONTROL, EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR));
    }

    @Bean
    public FilterRegistrationBean<PageTrailingSlashFilter> pageTrailingSlashFilterRegistration(final PageProperties pageProperties) {
        return registration(new PageTrailingSlashFilter(pageProperties), FilterOrderConstants.PAGE_TRAILING_SLASH, EnumSet.of(DispatcherType.REQUEST));
    }

    @Bean
    public FilterRegistrationBean<StaticResourceFilter> staticResourceFilterRegistration(final PageRegistry pageRegistry, final PageAccessResolver pageAccessResolver) {
        return registration(new StaticResourceFilter(pageRegistry, pageAccessResolver), FilterOrderConstants.STATIC_RESOURCE, EnumSet.of(DispatcherType.REQUEST));
    }

    private static <FilterType extends Filter> FilterRegistrationBean<FilterType> registration(final FilterType filterType, final int order, final EnumSet<DispatcherType> dispatcherTypeSet) {
        final FilterRegistrationBean<FilterType> filterRegistrationBean = new FilterRegistrationBean<>(filterType);

        filterRegistrationBean.setOrder(order);
        filterRegistrationBean.setDispatcherTypes(dispatcherTypeSet);
        filterRegistrationBean.addUrlPatterns("/*");

        return filterRegistrationBean;
    }
}