package me.trae.foundation.spring.security.headers;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.StringJoiner;

@UtilityClass
public class ContentSecurityPolicyBuilder {

    public String build(final SecurityHeadersProperties properties) {
        final StringJoiner joiner = new StringJoiner("; ");

        append(joiner, "default-src", properties.getDefaultSrc());
        append(joiner, "base-uri", properties.getBaseUri());
        append(joiner, "object-src", properties.getObjectSrc());
        append(joiner, "frame-ancestors", properties.getFrameAncestors());
        append(joiner, "form-action", properties.getFormAction());
        append(joiner, "script-src", properties.getScriptSrc());
        append(joiner, "style-src", properties.getStyleSrc());
        append(joiner, "font-src", properties.getFontSrc());
        append(joiner, "img-src", properties.getImgSrc());
        append(joiner, "connect-src", properties.getConnectSrc());
        append(joiner, "frame-src", properties.getFrameSrc());
        append(joiner, "child-src", properties.getChildSrc());

        return joiner.toString();
    }

    private void append(final StringJoiner stringJoiner, final String directive, final List<String> valueList) {
        if (valueList == null || valueList.isEmpty()) {
            return;
        }

        stringJoiner.add(directive + " " + String.join(" ", valueList));
    }
}