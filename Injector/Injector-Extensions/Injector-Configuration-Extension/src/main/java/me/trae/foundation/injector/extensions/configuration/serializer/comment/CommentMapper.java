package me.trae.foundation.injector.extensions.configuration.serializer.comment;

import lombok.experimental.UtilityClass;
import me.trae.foundation.injector.extensions.configuration.annotation.Comment;
import me.trae.foundation.injector.extensions.configuration.field.FieldResolver;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@UtilityClass
public class CommentMapper {

    public Map<String, String[]> map(final Class<?> type) {
        final Map<String, String[]> commentMap = new LinkedHashMap<>();

        collect(type, "", commentMap, new HashSet<>());

        return commentMap;
    }

    private void collect(final Class<?> type, final String prefix, final Map<String, String[]> commentMap, final Set<Class<?>> visitingSet) {
        if (!visitingSet.add(type)) {
            return;
        }

        for (final Field field : FieldResolver.getFields(type)) {
            final Comment comment = field.getAnnotation(Comment.class);
            if (comment != null) {
                commentMap.put(prefix + field.getName(), comment.value());
            }

            if (FieldResolver.isNested(field.getType())) {
                collect(field.getType(), prefix + field.getName() + ".", commentMap, visitingSet);
            }
        }

        visitingSet.remove(type);
    }
}