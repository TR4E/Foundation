package me.trae.foundation.minecraft.paper.addon.npc;

import com.destroystokyo.paper.entity.ai.MobGoals;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockSettings;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Minimal Paper boundary doubles; NPC methods, events and snapshots run as real code.
 */
public abstract class NpcTestSupport {

    protected final Server server = mock(Server.class);

    protected final PluginManager pluginManager = mock(PluginManager.class);

    protected final MobGoals mobGoals = mock(MobGoals.class);

    protected final World world = mock(World.class);

    protected final List<Event> eventList = new ArrayList<>();

    protected final Queue<Entity> entityQueue = new ArrayDeque<>();

    private MockedStatic<Bukkit> bukkitMockedStatic;

    @BeforeAll
    static void initializePaperRegistries() {
        // NMS brings a server console provider; these tests run without a server.
        if (System.getProperty("slf4j.provider") == null) {
            System.setProperty("slf4j.provider", "org.slf4j.helpers.NOP_FallbackServiceProvider");
        }

        RegistryBootstrap.initialize();
    }

    @BeforeEach
    void initializePaperBoundaries() {
        this.bukkitMockedStatic = mockStatic(Bukkit.class);
        this.bukkitMockedStatic.when(Bukkit::getServer).thenReturn(this.server);
        when(this.server.getPluginManager()).thenReturn(this.pluginManager);
        when(this.server.getMobGoals()).thenReturn(this.mobGoals);
        when(this.world.getUID()).thenReturn(UUID.randomUUID());
        doAnswer(invocationOnMock -> {
            this.eventList.add(invocationOnMock.getArgument(0));
            return null;
        }).when(this.pluginManager).callEvent(any(Event.class));

        doAnswer(invocationOnMock -> {
            final Entity entity = this.entityQueue.remove();
            final Consumer<Entity> consumer = invocationOnMock.getArgument(3);
            consumer.accept(entity);
            return entity;
        }).when(this.world).spawn(
                any(Location.class),
                any(),
                eq(CreatureSpawnEvent.SpawnReason.CUSTOM),
                any()
        );
    }

    @AfterEach
    void closePaperBoundaries() {
        this.bukkitMockedStatic.close();
    }

    protected final Location location(final double x, final double z) {
        return new Location(this.world, x, 64.0D, z);
    }

    protected final Chunk chunk(final World chunkWorld, final int x, final int z) {
        final Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(chunkWorld);
        when(chunk.getX()).thenReturn(x);
        when(chunk.getZ()).thenReturn(z);
        return chunk;
    }

    protected final <EntityType extends Entity> EntityType entity(final Class<EntityType> entityClass) {
        final EntityType entity = mock(entityClass);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.getWorld()).thenReturn(this.world);
        when(entity.getLocation()).thenReturn(this.location(0.0D, 0.0D));
        final PersistentDataContainer persistentDataContainer = this.persistentDataContainer();
        when(entity.getPersistentDataContainer()).thenReturn(persistentDataContainer);
        return entity;
    }

    protected final LivingEntity livingEntity() {
        final LivingEntity livingEntity = this.entity(LivingEntity.class);
        when(livingEntity.getHealth()).thenReturn(20.0D);
        when(livingEntity.getActivePotionEffects()).thenReturn(List.of());
        return livingEntity;
    }

    protected final AttributeInstance maxHealth(final LivingEntity livingEntity, final double maximum) {
        final AttributeInstance attributeInstance = mock(AttributeInstance.class);
        when(attributeInstance.getValue()).thenReturn(maximum);
        when(livingEntity.getAttribute(Attribute.MAX_HEALTH)).thenReturn(attributeInstance);
        return attributeInstance;
    }

    @SuppressWarnings("unchecked")
    protected final <EntityType extends Entity> Npc<EntityType> npc(
            final Class<EntityType> entityClass,
            final String identifier,
            final Class<?>... capabilityClasses
    ) {
        final MockSettings mockSettings = withSettings()
                .useConstructor(entityClass, identifier)
                .defaultAnswer(CALLS_REAL_METHODS);

        if (capabilityClasses.length > 0) {
            mockSettings.extraInterfaces(capabilityClasses);
        }

        return mock(Npc.class, mockSettings);
    }

    protected final void cancelEvents(final Class<? extends Event> eventClass) {
        doAnswer(invocationOnMock -> {
            final Event event = invocationOnMock.getArgument(0);
            this.eventList.add(event);

            if (eventClass.isInstance(event)) {
                ((org.bukkit.event.Cancellable) event).setCancelled(true);
            }

            return null;
        }).when(this.pluginManager).callEvent(any(Event.class));
    }

    protected final PersistentDataContainer persistentDataContainer() {
        final Map<NamespacedKey, Object> valueMap = new HashMap<>();
        final PersistentDataContainer persistentDataContainer = mock(PersistentDataContainer.class);
        final PersistentDataAdapterContext persistentDataAdapterContext = mock(PersistentDataAdapterContext.class);
        when(persistentDataContainer.getAdapterContext()).thenReturn(persistentDataAdapterContext);
        when(persistentDataAdapterContext.newPersistentDataContainer()).thenAnswer(_ -> this.persistentDataContainer());
        doAnswer(invocationOnMock -> {
            valueMap.put(invocationOnMock.getArgument(0), invocationOnMock.getArgument(2));
            return null;
        }).when(persistentDataContainer).set(any(), any(), any());
        when(persistentDataContainer.get(any(), any())).thenAnswer(invocationOnMock ->
                valueMap.get(invocationOnMock.getArgument(0)));
        doAnswer(invocationOnMock -> {
            final PersistentDataContainer targetPersistentDataContainer = invocationOnMock.getArgument(0);
            final boolean replace = invocationOnMock.getArgument(1);
            valueMap.forEach((namespacedKey, value) -> {
                if (replace || targetPersistentDataContainer.get(namespacedKey, PersistentDataType.STRING) == null) {
                    targetPersistentDataContainer.set(namespacedKey, PersistentDataType.STRING, (String) value);
                }
            });
            return null;
        }).when(persistentDataContainer).copyTo(any(), any(Boolean.class));
        return persistentDataContainer;
    }

    @SuppressWarnings("unchecked")
    protected final <Value> Value field(final Object object, final Class<?> declaringClass, final String name)
            throws ReflectiveOperationException {
        final Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        return (Value) field.get(object);
    }

    protected final void setField(final Object object, final String name, final Object value)
            throws ReflectiveOperationException {
        final Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    protected final boolean isWithin(final Npc<?> npc, final Location location, final Chunk chunk)
            throws ReflectiveOperationException {
        final Method method = Npc.class.getDeclaredMethod("isWithin", Location.class, Chunk.class);
        method.setAccessible(true);
        return (boolean) method.invoke(npc, location, chunk);
    }

    /**
     * Paper API constants require registries even when there is no running server.
     */
    private static final class RegistryBootstrap {

        private static final Map<Object, Registry<?>> registryMap = new HashMap<>();

        private static boolean initialized;

        private static void initialize() {
            if (initialized) {
                return;
            }

            try (final MockedStatic<RegistryAccess> registryAccessMockedStatic = mockStatic(RegistryAccess.class)) {
                final RegistryAccess registryAccess = mock(RegistryAccess.class, invocationOnMock ->
                        registryMap.computeIfAbsent(invocationOnMock.getArgument(0), RegistryBootstrap::createRegistry));
                registryAccessMockedStatic.when(RegistryAccess::registryAccess).thenReturn(registryAccess);
                // Initialize the constants while the registry provider is installed.
                Registry.ATTRIBUTE.size();
                Attribute.MAX_HEALTH.getKey();
                PotionEffectType.SPEED.getKey();
                initialized = true;
            }
        }

        private static Registry<?> createRegistry(final Object registryKey) {
            final Map<Object, Keyed> keyedMap = new HashMap<>();
            return mock(Registry.class, invocationOnMock -> {
                final String name = invocationOnMock.getMethod().getName();

                if (name.equals("get") || name.equals("getOrThrow")) {
                    return keyedMap.computeIfAbsent(invocationOnMock.getArgument(0), key -> {
                        final NamespacedKey namespacedKey = NamespacedKey.fromString(key.toString());
                        final Answer<Object> answer = keyedInvocationOnMock -> {
                            if (keyedInvocationOnMock.getMethod().getName().equals("getKey")) {
                                return namespacedKey;
                            }

                            return org.mockito.Answers.RETURNS_DEFAULTS.answer(keyedInvocationOnMock);
                        };

                        if (registryKey.equals(RegistryKey.ATTRIBUTE)) {
                            return mock(Attribute.class, answer);
                        }

                        if (registryKey.equals(RegistryKey.MOB_EFFECT)) {
                            return mock(PotionEffectType.class, answer);
                        }

                        return null;
                    });
                }

                if (name.equals("iterator")) {
                    return List.copyOf(keyedMap.values()).iterator();
                }

                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocationOnMock);
            });
        }
    }
}