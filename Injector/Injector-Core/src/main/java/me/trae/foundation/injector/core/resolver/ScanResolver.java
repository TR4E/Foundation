package me.trae.foundation.injector.core.resolver;

import me.trae.foundation.injector.api.exception.ScanException;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class ScanResolver {

    public List<Class<?>> resolve(final Class<?> applicationClass, final Predicate<Class<?>> componentFilter) {
        try {
            final Path location = Path.of(applicationClass.getProtectionDomain().getCodeSource().getLocation().toURI());

            if (Files.isDirectory(location)) {
                return this.walk(location, applicationClass, componentFilter);
            }

            try (final FileSystem fileSystem = FileSystems.newFileSystem(location)) {
                return this.walk(fileSystem.getPath("/"), applicationClass, componentFilter);
            }
        } catch (final IOException | URISyntaxException exception) {
            throw new ScanException("Unable to scan %s".formatted(applicationClass.getName()), exception);
        }
    }

    private List<Class<?>> walk(final Path base, final Class<?> applicationClass, final Predicate<Class<?>> componentFilter) throws IOException {
        try (final Stream<Path> paths = Files.walk(base.resolve(applicationClass.getPackageName().replace('.', '/')))) {
            return paths
                    .map(path -> base.relativize(path).toString())
                    .filter(name -> name.endsWith(".class") && !name.contains("-info"))
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.').replace('\\', '.'))
                    .map(name -> this.load(name, applicationClass.getClassLoader()))
                    .flatMap(Optional::stream)
                    .filter(type -> !Modifier.isAbstract(type.getModifiers()) && componentFilter.test(type))
                    .toList();
        }
    }

    private Optional<Class<?>> load(final String name, final ClassLoader classLoader) {
        try {
            return Optional.of(Class.forName(name, false, classLoader));
        } catch (final ClassNotFoundException | LinkageError exception) {
            return Optional.empty();
        }
    }
}