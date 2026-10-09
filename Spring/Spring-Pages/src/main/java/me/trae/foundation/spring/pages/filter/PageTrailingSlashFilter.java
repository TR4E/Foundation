package me.trae.foundation.spring.pages.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.utility.UtilRequestPath;
import me.trae.foundation.spring.pages.PageProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@AllArgsConstructor
public final class PageTrailingSlashFilter extends OncePerRequestFilter {

    private final PageProperties pageProperties;

    @Override
    protected void doFilterInternal(final @NonNull HttpServletRequest httpServletRequest, final @NonNull HttpServletResponse httpServletResponse, final @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!this.pageProperties.isTrailingSlashRedirect()) {
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }

        final String requestUri = httpServletRequest.getRequestURI();

        final String canonical = UtilRequestPath.canonicalise(requestUri);

        if (canonical.equals(requestUri)) {
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }

        final String queryString = httpServletRequest.getQueryString();

        httpServletResponse.setStatus(HttpStatus.PERMANENT_REDIRECT.value());
        httpServletResponse.setHeader(HttpHeaders.LOCATION, queryString == null || queryString.isBlank() ? canonical : "%s?%s".formatted(canonical, queryString));
    }
}