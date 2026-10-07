package me.trae.foundation.utilities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
public class UtilFile {

    @Setter
    private static long maxCacheableInMegaBytes = 512L;

    private static final ConcurrentHashMap<Path, Cache> CACHED_FILE_MAP = new ConcurrentHashMap<>();

    public static List<String> read(final Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null.");
        }

        final Path normalizedPath = path.toAbsolutePath().normalize();

        try {
            final BasicFileAttributes attributes = Files.readAttributes(normalizedPath, BasicFileAttributes.class);

            final long size = attributes.size();
            final long lastModified = attributes.lastModifiedTime().toMillis();

            if (size > maxCacheableInMegaBytes * 1024 * 1024) {
                CACHED_FILE_MAP.remove(normalizedPath);

                return readFromDisk(normalizedPath, size);
            }

            final Cache cache = CACHED_FILE_MAP.get(normalizedPath);

            if (cache != null && cache.getSize() == size && cache.getLastModified() == lastModified) {
                return cache.getLines();
            }

            final List<String> lines = readFromDisk(normalizedPath, size);

            CACHED_FILE_MAP.put(normalizedPath, new Cache(lines, size, lastModified));

            return lines;
        } catch (final IOException exception) {
            CACHED_FILE_MAP.remove(normalizedPath);

            throw new UncheckedIOException("Could not read file: %s".formatted(normalizedPath), exception);
        }
    }

    public static List<String> read(final File file) {
        return read(file.toPath());
    }

    public static List<String> read(final String filePath) {
        return read(Paths.get(filePath));
    }

    private static List<String> readFromDisk(final Path path, final long size) throws IOException {
        return size > Integer.MAX_VALUE - 8 ? List.copyOf(Files.readAllLines(path)) : Files.readString(path).lines().toList();
    }

    @AllArgsConstructor
    @Getter
    private static class Cache {

        private final List<String> lines;
        private final long size;
        private final long lastModified;
    }
}