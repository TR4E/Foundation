package me.trae.foundation.injector.extensions.configuration.serializer.json;

import lombok.experimental.UtilityClass;
import me.trae.foundation.injector.extensions.configuration.serializer.comment.CommentMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@UtilityClass
public class JsonCommentProcessor {

    public String strip(final String json) {
        final StringBuilder result = new StringBuilder();

        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < json.length(); index++) {
            final char character = json.charAt(index);

            if (escaped) {
                escaped = false;
            } else if (character == '\\' && inString) {
                escaped = true;
            } else if (character == '"') {
                inString = !inString;
            } else if (!inString && character == '/' && index + 1 < json.length() && json.charAt(index + 1) == '/') {
                while (index < json.length() && json.charAt(index) != '\n') {
                    index++;
                }

                continue;
            }

            result.append(character);
        }

        return result.toString();
    }

    public String inject(final String json, final Class<?> type) {
        final Map<String, String[]> commentMap = CommentMapper.map(type);

        if (commentMap.isEmpty()) {
            return json;
        }

        final StringBuilder result = new StringBuilder();

        final List<String> pathList = new ArrayList<>();

        for (final String line : json.split("\n")) {
            final String trimmed = line.trim();

            if (trimmed.startsWith("}") || trimmed.startsWith("]")) {
                if (!pathList.isEmpty()) {
                    pathList.removeLast();
                }

                result.append(line).append("\n");
                continue;
            }

            final String key = getKey(trimmed);

            if (key != null) {
                final String path = getPath(pathList, key);

                if (path != null && commentMap.containsKey(path)) {
                    final String indent = line.substring(0, line.length() - line.stripLeading().length());

                    for (final String commentLine : commentMap.get(path)) {
                        result.append(indent).append("// ").append(commentLine).append("\n");
                    }
                }
            }

            result.append(line).append("\n");

            if (trimmed.endsWith("{") || trimmed.endsWith("[")) {
                pathList.add(key);
            }
        }

        return result.toString().stripTrailing() + "\n";
    }

    private String getKey(final String trimmed) {
        if (!trimmed.startsWith("\"")) {
            return null;
        }

        int index = 1;

        while (index < trimmed.length() && trimmed.charAt(index) != '"') {
            index += trimmed.charAt(index) == '\\' ? 2 : 1;
        }

        return index + 1 < trimmed.length() && trimmed.charAt(index + 1) == ':' ? trimmed.substring(1, index) : null;
    }

    private String getPath(final List<String> pathList, final String key) {
        final StringBuilder path = new StringBuilder();

        for (int index = 1; index < pathList.size(); index++) {
            if (pathList.get(index) == null) {
                return null;
            }

            path.append(pathList.get(index)).append(".");
        }

        return path.append(key).toString();
    }
}