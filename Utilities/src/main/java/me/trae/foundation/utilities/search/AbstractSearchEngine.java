package me.trae.foundation.utilities.search;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.utilities.UtilJava;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

@CustomLog
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractSearchEngine<Type, Receiver> {

    private static final String NOT_FOUND_MESSAGE = "No matches found [%s].";
    private static final String FOUND_MESSAGE = "%s matches found [%s].";

    protected static final String MATCH_SEPARATOR = ", ";

    @Getter
    @Setter
    private static int maxResultLimit = 100;

    private final String name;
    private final Supplier<Collection<? extends Type>> collectionSupplier;

    public final Optional<Type> find(final Receiver receiver, final String input, final boolean inform, final Predicate<Type> predicate) {
        final List<Type> resultList = new ArrayList<>();

        for (final Type type : this.collectionSupplier.get()) {
            if (predicate != null && !predicate.test(type)) {
                continue;
            }

            if (this.isExact(type, input)) {
                return Optional.of(type);
            }

            if (this.isMatching(type, input)) {
                resultList.add(type);
            }
        }

        this.updateList(resultList, receiver, input);

        if (resultList.size() == 1) {
            return Optional.of(resultList.getFirst());
        }

        if (inform) {
            String message = NOT_FOUND_MESSAGE.formatted(this.getInputFormat(input));

            if (!resultList.isEmpty()) {
                final List<String> matchList = UtilJava.createCollection(new ArrayList<>(), list -> {
                    for (int index = 0; index < Math.min(maxResultLimit, resultList.size()); index++) {
                        final Type type = resultList.get(index);

                        list.add(this.formatMatch(type, this.getMatchName(type, receiver), receiver));
                    }
                });

                message = FOUND_MESSAGE.formatted(this.getInputFormat(String.valueOf(resultList.size())), String.join(this.getMatchSeparator(), matchList));
            }

            this.message(receiver, this.name, message);
        }

        return Optional.empty();
    }

    protected void updateList(final List<Type> resultList, final Receiver receiver, final String input) {
    }

    protected void message(final Receiver receiver, final String prefix, final String message) {
        String format = message;

        if (prefix != null) {
            format = "[%s] %s".formatted(prefix, message);
        }

        LOGGER.info(format);
    }

    protected String getMatchSeparator() {
        return MATCH_SEPARATOR;
    }

    protected String getInputFormat(final String string) {
        return string;
    }

    protected abstract String getMatchName(final Type type, final Receiver receiver);

    protected abstract String formatMatch(final Type type, final String name, final Receiver receiver);

    protected abstract boolean isExact(final Type type, final String result);

    protected abstract boolean isMatching(final Type type, final String result);
}