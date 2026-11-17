package me.trae.foundation.database.verification;

import me.trae.foundation.database.api.declaration.DeclarationLoader;
import me.trae.foundation.database.api.entity.EntityInstantiator;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.postgres.driver.PostgresDriver;
import me.trae.foundation.database.postgres.driver.PostgresSettings;
import me.trae.foundation.database.postgres.schema.PostgresSynchronizer;
import me.trae.foundation.database.postgres.schema.SchemaDefinition;
import me.trae.foundation.database.redis.codec.EntityCodec;
import me.trae.foundation.database.redis.driver.RedisDriver;
import me.trae.foundation.database.redis.driver.RedisSettings;
import me.trae.foundation.database.redis.storage.RedisEntityStorage;
import me.trae.foundation.database.redis.write.RedisEntityWriter;
import me.trae.foundation.database.tenant.TenantScope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;

import static me.trae.foundation.database.verification.DatabaseFixture.Account;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountDeclaration;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountRepository;
import static me.trae.foundation.database.verification.DatabaseFixture.account;
import static me.trae.foundation.database.verification.DatabaseFixture.namespace;
import static me.trae.foundation.database.verification.DatabaseFixture.set;
import static me.trae.foundation.database.verification.DatabaseFixture.write;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
public abstract class ServiceFixture {

    @Container
    protected static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withEnv(Map.of("POSTGRES_DB", "verification", "POSTGRES_USER", "verification", "POSTGRES_PASSWORD", "verification"))
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    @Container
    protected static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    protected static PostgresDriver postgresDriver;

    protected static RedisDriver redisDriver;

    protected static AccountRepository accountRepository;

    @BeforeAll
    static void connectServices() {
        DeclarationLoader.load(AccountDeclaration.class);

        postgresDriver = new PostgresDriver(new PostgresSettings(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(5432),
                "verification",
                "verification",
                "verification",
                8
        ));
        redisDriver = new RedisDriver(new RedisSettings(REDIS.getHost(), REDIS.getMappedPort(6379), null, 0, Duration.ofSeconds(5)));

        postgresDriver.connect();
        redisDriver.connect();

        final TenantScope tenantScope = mock(TenantScope.class);

        when(tenantScope.getTenant()).thenReturn(java.util.Optional.empty());
        accountRepository = new AccountRepository(postgresDriver, tenantScope);

        new PostgresSynchronizer(postgresDriver).synchronize(SchemaDefinition.statements(
                "accounts",
                false,
                PropertyRegistry.getProperties(Account.class),
                Map.of(AccountDeclaration.NAME, me.trae.foundation.database.postgres.index.PostgresIndexType.UNIQUE)
        ));
    }

    @AfterAll
    static void disconnectServices() {
        if (redisDriver != null) {
            redisDriver.disconnect();
        }

        if (postgresDriver != null) {
            postgresDriver.disconnect();
        }
    }

    @BeforeEach
    void resetServices() {
        postgresDriver.getDslContext().execute("TRUNCATE accounts");
        redisDriver.getCommands().flushdb();
    }

    protected RedisEntityStorage<Account> entityStorage() {
        return new RedisEntityStorage<>(
                namespace("accounts"),
                redisDriver,
                new EntityCodec<>(new EntityInstantiator<>(Account.class), PropertyRegistry.getProperties(Account.class))
        );
    }

    protected RedisEntityWriter entityWriter() {
        return new RedisEntityWriter(redisDriver, PropertyRegistry.getProperties(Account.class));
    }

    protected Account persistedAccount() {
        final Account account = account();

        accountRepository.apply(write(
                account.getId(),
                me.trae.foundation.database.api.write.WriteKind.SAVE,
                0L,
                set("name", account.getName()),
                set("account_group", account.getGroup()),
                set("counter", 0L)
        ));

        return account;
    }
}