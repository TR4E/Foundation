package me.trae.foundation.minecraft.paper.plugin.framework.addon.scanner;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.Addon;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

@UtilityClass
public class AddonScanner {

    public static List<Class<?>> scan(final Class<? extends Addon> type) {
        final String packagePath = "%s/".formatted(type.getPackageName().replace('.', '/'));

        return getEntries(type).stream()
                .filter(entry -> entry.startsWith(packagePath) && entry.endsWith(".class"))
                .<Class<?>>map(entry -> load(type, entry))
                .filter(clazz -> clazz.isAnnotationPresent(AddonSingleton.class))
                .toList();
    }

    private static List<String> getEntries(final Class<?> type) {
        try {
            final Path source = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());

            if (Files.isDirectory(source)) {
                try (final Stream<Path> stream = Files.walk(source)) {
                    return stream.filter(Files::isRegularFile).map(path -> source.relativize(path).toString().replace(File.separatorChar, '/')).toList();
                }
            }

            try (final JarFile jarFile = new JarFile(source.toFile())) {
                return jarFile.stream().map(JarEntry::getName).toList();
            }
        } catch (final IOException | URISyntaxException exception) {
            throw new IllegalStateException("Failed to scan addon %s".formatted(type.getName()), exception);
        }
    }

    private static Class<?> load(final Class<?> type, final String entry) {
        final String className = entry.substring(0, entry.length() - ".class".length()).replace('/', '.');

        try {
            return Class.forName(className, false, type.getClassLoader());
        } catch (final ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to load %s".formatted(className), exception);
        }
    }
}