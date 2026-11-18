package me.trae.foundation.database.batch;

import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;
import me.trae.foundation.utilities.UtilJava;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class ChangeMerge {

    public static List<Change> merge(final List<Change> existing, final List<Change> incoming) {
        final LinkedHashMap<String, Change> columnMap = new LinkedHashMap<>();

        final List<Change> appended = new ArrayList<>();

        existing.forEach(change -> accept(columnMap, appended, change));
        incoming.forEach(change -> accept(columnMap, appended, change));

        return UtilJava.createCollection(new ArrayList<>(), changes -> {
            changes.addAll(columnMap.values());
            changes.addAll(appended);
        });
    }

    private static void accept(final LinkedHashMap<String, Change> columnMap, final List<Change> appended, final Change change) {
        if (change.getType() == ChangeType.ADD || change.getType() == ChangeType.REMOVE) {
            appended.add(change);
            return;
        }

        columnMap.merge(change.getColumn(), change, ChangeMerge::combine);
    }

    private static Change combine(final Change existing, final Change incoming) {
        if (incoming.getType() == ChangeType.SET) {
            return incoming;
        }

        return new Change(existing.getColumn(), existing.getType(), number(existing.getValue()) + number(incoming.getValue()));
    }

    private static long number(final Object value) {
        return value == null ? 0L : Long.class.cast(value);
    }
}