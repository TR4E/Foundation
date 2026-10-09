package me.trae.foundation.spring.pages.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.utility.UtilRequestPath;
import me.trae.foundation.spring.pages.PageProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@AllArgsConstructor
public final class RobotsTagFilter extends OncePerRequestFilter {

    private static final String ROBOTS_TAG_HEADER = "X-Robots-Tag";

    private final PageProperties pageProperties;

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(final @NonNull HttpServletRequest httpServletRequest, final @NonNull HttpServletResponse httpServletResponse, final @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String robotsTag = this.pageProperties.getRobotsTag();

        if (robotsTag != null && !robotsTag.isBlank() && !this.isExcluded(UtilRequestPath.getCanonicalPath(httpServletRequest))) {
            httpServletResponse.setHeader(ROBOTS_TAG_HEADER, robotsTag);
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    private boolean isExcluded(final String path) {
        for (final String excludedPath : this.pageProperties.getRobotsTagExcludedPathList()) {
            if (!UtilRequestPath.matchesPrefix(path, UtilRequestPath.canonicalise(excludedPath))) {
                continue;
            }

            return true;
        }

        return false;
    }
}