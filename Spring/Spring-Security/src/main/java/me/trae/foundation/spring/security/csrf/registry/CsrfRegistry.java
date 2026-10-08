package me.trae.foundation.spring.security.csrf.registry;

import me.trae.foundation.spring.security.csrf.annotation.CsrfExclude;
import org.springframework.http.server.PathContainer;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CsrfRegistry {

    private volatile List<PathPattern> excludedPatternList = Collections.emptyList();

    public void register(final List<PathPattern> excludedPatternList) {
        this.excludedPatternList = List.copyOf(excludedPatternList);
    }

    public boolean isExcluded(final String path) {
        if (this.excludedPatternList.isEmpty()) {
            return false;
        }

        final PathContainer pathContainer = PathContainer.parsePath(path);
        final PathContainer slashedContainer = PathContainer.parsePath(path.endsWith("/") ? path : path + "/");

        for (final PathPattern pathPattern : this.excludedPatternList) {
            if (pathPattern.matches(pathContainer) || pathPattern.matches(slashedContainer)) {
                return true;
            }
        }

        return false;
    }

    public static List<PathPattern> build(final Map<RequestMappingInfo, HandlerMethod> handlerMethodMap) {
        final List<PathPattern> patternList = new ArrayList<>();

        for (final Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethodMap.entrySet()) {
            final HandlerMethod handlerMethod = entry.getValue();

            if (handlerMethod.getMethod().getAnnotation(CsrfExclude.class) == null && handlerMethod.getBeanType().getAnnotation(CsrfExclude.class) == null) {
                continue;
            }

            if (entry.getKey().getPathPatternsCondition() == null) {
                continue;
            }

            patternList.addAll(entry.getKey().getPathPatternsCondition().getPatterns());
        }

        return patternList;
    }
}