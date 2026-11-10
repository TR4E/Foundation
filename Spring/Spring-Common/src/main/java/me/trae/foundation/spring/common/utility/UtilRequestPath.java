package me.trae.foundation.spring.common.utility;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.util.UrlPathHelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

@UtilityClass
public class UtilRequestPath {

    private static final String SLASH = "/";
    private static final String DOT = ".";
    private static final String DOUBLE_DOT = "..";
    private static final Pattern SLASH_RUN_PATTERN = Pattern.compile("/{2,}");

    public static String getCanonicalPath(final HttpServletRequest httpServletRequest) {
        return canonicalise(UrlPathHelper.defaultInstance.getPathWithinApplication(httpServletRequest));
    }

    public static String canonicalise(final String path) {
        if (path == null || path.isBlank()) {
            return SLASH;
        }

        final String prefixed = path.startsWith(SLASH) ? path : SLASH + path;
        final String collapsed = SLASH_RUN_PATTERN.matcher(prefixed).replaceAll(SLASH);
        final String resolved = resolveDotSegments(collapsed);

        if (resolved.length() > 1 && resolved.endsWith(SLASH)) {
            return resolved.substring(0, resolved.length() - 1);
        }

        return resolved;
    }

    public static boolean matchesPrefix(final String path, final String prefix) {
        if (!path.startsWith(prefix)) {
            return false;
        }

        return SLASH.equals(prefix) || path.length() == prefix.length() || path.charAt(prefix.length()) == '/';
    }

    private static String resolveDotSegments(final String path) {
        if (!path.contains(DOT)) {
            return path;
        }

        final Deque<String> segmentDeque = new ArrayDeque<>();

        for (final String segment : path.split(SLASH, -1)) {
            if (segment.isEmpty() || DOT.equals(segment)) {
                continue;
            }

            if (DOUBLE_DOT.equals(segment)) {
                segmentDeque.pollLast();
                continue;
            }

            segmentDeque.addLast(segment);
        }

        return SLASH + String.join(SLASH, segmentDeque);
    }
}