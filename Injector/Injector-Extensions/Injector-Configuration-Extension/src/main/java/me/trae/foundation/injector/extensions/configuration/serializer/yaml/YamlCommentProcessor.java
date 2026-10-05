package me.trae.foundation.injector.extensions.configuration.serializer.yaml;

import lombok.experimental.UtilityClass;
import me.trae.foundation.injector.extensions.configuration.serializer.comment.CommentMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@UtilityClass
public class YamlCommentProcessor {

    public String inject(final String yaml, final Class<?> type) {
        final Map<String, String[]> commentMap = CommentMapper.map(type);

        if (commentMap.isEmpty()) {
            return yaml;
        }

        final StringBuilder result = new StringBuilder();

        final List<YamlPathSegment> segmentList = new ArrayList<>();

        for (final String line : yaml.split("\n")) {
            final String trimmed = line.strip();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                result.append(line).append("\n");
                continue;
            }

            final int lineIndent = line.length() - line.stripLeading().length();

            int indent = lineIndent;

            while (!segmentList.isEmpty() && segmentList.getLast().getIndent() >= indent) {
                segmentList.removeLast();
            }

            String content = trimmed;

            while (content.equals("-") || content.startsWith("- ")) {
                segmentList.add(new YamlPathSegment(indent, null));

                final String rest = content.substring(1).stripLeading();

                indent += content.length() - rest.length();

                content = rest;
            }

            final String key = getKey(content);

            if (key != null) {
                final String path = getPath(segmentList, key);

                if (path != null && commentMap.containsKey(path)) {
                    final String commentIndent = line.substring(0, lineIndent);

                    for (final String commentLine : commentMap.get(path)) {
                        result.append(commentIndent).append("# ").append(commentLine).append("\n");
                    }
                }

                segmentList.add(new YamlPathSegment(indent, key));
            }

            result.append(line).append("\n");
        }

        return result.toString();
    }

    private String getKey(final String content) {
        final int separator = content.indexOf(": ");

        if (separator > 0) {
            return content.substring(0, separator);
        }

        return content.length() > 1 && content.endsWith(":") ? content.substring(0, content.length() - 1) : null;
    }

    private String getPath(final List<YamlPathSegment> segmentList, final String key) {
        final StringBuilder path = new StringBuilder();

        for (final YamlPathSegment segment : segmentList) {
            if (segment.getKey() == null) {
                return null;
            }

            path.append(segment.getKey()).append(".");
        }

        return path.append(key).toString();
    }
}