package me.trae.foundation.spring.pages.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.utility.UtilRequestPath;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import me.trae.foundation.spring.pages.registry.PageRegistry;
import me.trae.foundation.spring.pages.role.PageAccessResolver;
import me.trae.foundation.spring.pages.role.PageRole;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@AllArgsConstructor
public final class StaticResourceFilter extends OncePerRequestFilter {

    private final PageRegistry pageRegistry;
    private final PageAccessResolver pageAccessResolver;

    @Override
    protected void doFilterInternal(final @NonNull HttpServletRequest httpServletRequest, final @NonNull HttpServletResponse httpServletResponse, final @NonNull FilterChain filterChain) throws ServletException, IOException {
        final PageRole pageRole = this.pageRegistry.getAssetRole(UtilRequestPath.getCanonicalPath(httpServletRequest));
        if (pageRole == null) {
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }

        CacheControlData.PRIVATE_NO_STORE.apply(httpServletResponse);

        if (!this.pageAccessResolver.hasRole(httpServletRequest, httpServletResponse, pageRole)) {
            httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }
}