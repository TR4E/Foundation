package me.trae.foundation.spring.pages.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.PageProperties;
import me.trae.foundation.spring.pages.role.PageAccessResolver;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@AllArgsConstructor
public final class PageAccessInterceptor implements HandlerInterceptor {

    private final PageProperties pageProperties;
    private final PageAccessResolver pageAccessResolver;

    @Override
    public boolean preHandle(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse, final Object handler) throws IOException {
        if (!(handler instanceof final HandlerMethod handlerMethod)) {
            return true;
        }

        if (!(handlerMethod.getBean() instanceof final Page page) || page.isPublic()) {
            return true;
        }

        if (!this.pageAccessResolver.isAuthenticated(httpServletRequest, httpServletResponse)) {
            httpServletResponse.sendRedirect("%s?%s=%s".formatted(this.pageProperties.getLoginPath(), this.pageProperties.getRedirectParameter(), URLEncoder.encode(this.getRequestPath(httpServletRequest), StandardCharsets.UTF_8)));
            return false;
        }

        if (!this.pageAccessResolver.hasRole(httpServletRequest, httpServletResponse, page.getRequiredRole())) {
            httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        return true;
    }

    private String getRequestPath(final HttpServletRequest httpServletRequest) {
        final String path = httpServletRequest.getRequestURI();

        final String queryString = httpServletRequest.getQueryString();

        return queryString == null || queryString.isBlank() ? path : "%s?%s".formatted(path, queryString);
    }
}