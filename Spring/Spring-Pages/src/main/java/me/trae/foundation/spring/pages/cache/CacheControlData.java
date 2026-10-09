package me.trae.foundation.spring.pages.cache;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpHeaders;

@AllArgsConstructor
@Getter
public final class CacheControlData {

    public static final CacheControlData NO_STORE = new CacheControlData("private, no-store, no-cache, must-revalidate");
    public static final CacheControlData IMMUTABLE = new CacheControlData("public, max-age=31536000, immutable");
    public static final CacheControlData SHARED_SHORT = new CacheControlData("public, max-age=300, must-revalidate");
    public static final CacheControlData PRIVATE_REVALIDATE = new CacheControlData("private, no-cache, must-revalidate");
    public static final CacheControlData PRIVATE_NO_STORE = new CacheControlData("private, no-store");

    private final String value;

    public void apply(final HttpServletResponse httpServletResponse) {
        httpServletResponse.setHeader(HttpHeaders.CACHE_CONTROL, this.value);
    }
}