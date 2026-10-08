package me.trae.foundation.spring.common.utility;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.util.UrlPathHelper;

import java.util.regex.Pattern;

@UtilityClass
public class UtilRequestPath {

    private static final String SLASH = "/";
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

        if (collapsed.length() > 1 && collapsed.endsWith(SLASH)) {
            return collapsed.substring(0, collapsed.length() - 1);
        }

        return collapsed;
    }

    public static boolean matchesPrefix(final String path, final String prefix) {
        if (!path.startsWith(prefix)) {
            return false;
        }

        return path.length() == prefix.length() || path.charAt(prefix.length()) == '/';
    }
}