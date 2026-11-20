package me.trae.foundation.database.verification;

import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.WriteGate;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.batch.BatchQueue;
import me.trae.foundation.database.holder.context.HolderContext;
import me.trae.foundation.database.holder.context.HolderContextFactory;
import me.trae.foundation.database.holder.entity.EntityHolder;
import me.trae.foundation.database.local.LocalMarker;
import me.trae.foundation.database.local.LocalStorageProvider;
import me.trae.foundation.database.tenant.Tenant;
import me.trae.foundation.database.tenant.TenantScope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static me.trae.foundation.database.verification.DatabaseFixture.Account;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountRepository;
import static me.trae.foundation.database.verification.DatabaseFixture.repository;
import static me.trae.foundation.database.verification.DatabaseFixture.set;
import static me.trae.foundation.database.verification.DatabaseFixture.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class CoreIsolationTest {

    @TempDir
    private static Path compilationDirectory;

    private static URLClassLoader classLoader;

    @BeforeAll
    static void compileUnblockedCoreClasses() throws Exception {
        final StringWriter stringWriter = new StringWriter();

        assertTrue(compile(false, compilationDirectory, stringWriter), stringWriter.toString());
        classLoader = new URLClassLoader(new java.net.URL[]{compilationDirectory.toUri().toURL()}, CoreIsolationTest.class.getClassLoader());
    }

    @AfterAll
    static void closeClassLoader() throws Exception {
        if (classLoader != null) {
            classLoader.close();
        }
    }

    @Test
    void fullCoreCompilesAgainstHolderApi() throws Exception {
        final StringWriter stringWriter = new StringWriter();

        assertTrue(compile(true, Files.createTempDirectory(compilationDirectory, "full"), stringWriter), stringWriter.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisAbsentDoesNotDoubleWritePostgres() throws Exception {
        final AccountRepository accountRepository = repository();
        final Object contextFactory = this.factory();
        final HolderContext<Account> holderContext = (HolderContext<Account>) contextFactory.getClass().getMethod(
                "create",
                Repository.class,
                Set.class
        ).invoke(contextFactory, accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES));
        final var write = write(UUID.randomUUID(), WriteKind.UPDATE, 0L, set("name", "value"));

        holderContext.getAuthority().apply(write);
        holderContext.getPersistence().accept(write);

        verify(accountRepository, times(1)).apply(write);
    }

    @Test
    void coreFactoryImplementsHolderConstructorContract() throws Exception {
        assertInstanceOf(HolderContextFactory.class, this.factory(), "Core factory must satisfy the EntityHolder constructor contract");
    }

    @Test
    void perHolderTiersAreIndependent() throws Exception {
        final AccountRepository accountRepository = repository();
        final HolderContextFactory holderContextFactory = assertInstanceOf(HolderContextFactory.class, this.factory());

        assertEquals(Set.of(Tier.LOCAL, Tier.POSTGRES), holderContextFactory.create(accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES)).getTiers());
        assertEquals(Set.of(Tier.REDIS, Tier.POSTGRES), holderContextFactory.create(accountRepository, Set.of(Tier.REDIS, Tier.POSTGRES)).getTiers());
    }

    @Test
    void batchWritesRouteByTable() throws Exception {
        final Class<?> registryType = classLoader.loadClass("me.trae.foundation.database.core.registry.EntityRegistry");
        final Object entityRegistry = registryType.getConstructor().newInstance();
        final AccountRepository firstRepository = repository();
        final AccountRepository secondRepository = repository();

        when(firstRepository.getTable()).thenReturn("first");
        when(secondRepository.getTable()).thenReturn("second");
        registryType.getMethod("register", Repository.class, EntityHolder.class).invoke(entityRegistry, firstRepository, mock(EntityHolder.class));
        registryType.getMethod("register", Repository.class, EntityHolder.class).invoke(entityRegistry, secondRepository, mock(EntityHolder.class));

        final var firstWrite = new me.trae.foundation.database.api.write.Write(
                new me.trae.foundation.database.api.key.EntityKey("first", null, UUID.randomUUID()),
                WriteKind.DELETE,
                List.of(),
                0L
        );
        final var secondWrite = new me.trae.foundation.database.api.write.Write(
                new me.trae.foundation.database.api.key.EntityKey("second", null, UUID.randomUUID()),
                WriteKind.DELETE,
                List.of(),
                0L
        );

        registryType.getMethod("apply", me.trae.foundation.database.api.write.Write.class).invoke(entityRegistry, firstWrite);
        registryType.getMethod("apply", me.trae.foundation.database.api.write.Write.class).invoke(entityRegistry, secondWrite);

        verify(firstRepository).apply(firstWrite);
        verify(firstRepository, never()).apply(secondWrite);
        verify(secondRepository).apply(secondWrite);
        verify(secondRepository, never()).apply(firstWrite);
    }

    private Object factory() throws Exception {
        final Class<?> settingsType = classLoader.loadClass("me.trae.foundation.database.core.settings.DatabaseSettings");
        final Object databaseSettings = settingsType.getConstructors()[0].newInstance(
                Tier.ALL,
                "test",
                Duration.ofMinutes(1),
                Duration.ofSeconds(10),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                Duration.ofSeconds(2),
                Duration.ZERO,
                0,
                0
        );
        final TenantScope tenantScope = new TenantScope(Tenant.of("test"));
        final Class<?> namespaceType = classLoader.loadClass("me.trae.foundation.database.core.namespace.NamespaceFactory");
        final Object namespaceFactory = namespaceType.getConstructors()[0].newInstance(databaseSettings, tenantScope);

        return classLoader.loadClass("me.trae.foundation.database.core.context.ContextFactory").getConstructors()[0].newInstance(
                databaseSettings,
                tenantScope,
                namespaceFactory,
                List.of(new LocalStorageProvider()),
                new LocalMarker(),
                new BatchQueue(0),
                new WriteGate(),
                null,
                classLoader.loadClass("me.trae.foundation.database.core.registry.EntityRegistry").getConstructor().newInstance()
        );
    }

    private static boolean compile(final boolean includeDatabase, final Path destination, final StringWriter stringWriter) throws Exception {
        final Path sourceDirectory = Path.of(System.getProperty("user.dir")).getParent().resolve("Database-Core/src/main/java");

        try (final var fileManager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null);
             final var pathStream = Files.walk(sourceDirectory)) {
            final List<Path> sourceList = pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> includeDatabase || !path.getFileName().toString().equals("CoreDatabase.java"))
                    .toList();

            return ToolProvider.getSystemJavaCompiler().getTask(
                    stringWriter,
                    fileManager,
                    null,
                    List.of(
                            "--release",
                            "25",
                            "-classpath",
                            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                            "-d",
                            destination.toString(),
                            "-processor",
                            "lombok.launch.AnnotationProcessorHider$AnnotationProcessor"
                    ),
                    null,
                    fileManager.getJavaFileObjectsFromPaths(sourceList)
            ).call();
        }
    }
}