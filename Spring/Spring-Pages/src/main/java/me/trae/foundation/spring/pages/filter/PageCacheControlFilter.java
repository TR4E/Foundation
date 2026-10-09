package me.trae.foundation.spring.pages.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.utility.UtilRequestPath;
import me.trae.foundation.spring.pages.PageProperties;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import me.trae.foundation.spring.pages.registry.PageRegistry;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@AllArgsConstructor
public final class PageCacheControlFilter extends OncePerRequestFilter {

    private final PageProperties pageProperties;
    private final PageRegistry pageRegistry;

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(final @NonNull HttpServletRequest httpServletRequest, final @NonNull HttpServletResponse httpServletResponse, final @NonNull FilterChain filterChain) throws ServletException, IOException {
        this.getCacheControlData(this.getPath(httpServletRequest)).apply(httpServletResponse);

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    private String getPath(final HttpServletRequest httpServletRequest) {
        final Object errorRequestUri = httpServletRequest.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        if (errorRequestUri instanceof final String value) {
            return UtilRequestPath.canonicalise(value);
        }

        return UtilRequestPath.getCanonicalPath(httpServletRequest);
    }

    private CacheControlData getCacheControlData(final String path) {
        for (final String systemPath : this.pageProperties.getSystemPathList()) {
            if (!UtilRequestPath.matchesPrefix(path, UtilRequestPath.canonicalise(systemPath))) {
                continue;
            }

            return CacheControlData.NO_STORE;
        }

        final CacheControlData cacheControlData = this.pageRegistry.getCacheControlData(path);

        return cacheControlData == null ? CacheControlData.PRIVATE_REVALIDATE : cacheControlData;
    }
}