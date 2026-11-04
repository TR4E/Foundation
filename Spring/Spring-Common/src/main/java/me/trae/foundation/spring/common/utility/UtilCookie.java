package me.trae.foundation.spring.common.utility;

import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;
import me.trae.foundation.utilities.UtilBase64;

import java.time.Duration;
import java.util.function.Function;

@UtilityClass
public class UtilCookie {

    private static final Function<String, String> SERIALIZER_FUNCTION = cookieValue -> cookieValue == null ? null : UtilBase64.encodeToString(cookieValue);
    private static final Function<String, String> DESERIALIZER_FUNCTION = encodedCookieValue -> encodedCookieValue == null ? null : UtilBase64.decodeToString(encodedCookieValue);

    public static void setCookie(final HttpServletResponse httpServletResponse, final String cookieName, final String cookieValue, final boolean httpOnly, final Duration maxAge, final boolean serialize) {

    }

    private static String resolveCookieName(final String cookieName) {
    }
}