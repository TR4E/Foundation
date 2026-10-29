package me.trae.foundation.minecraft.paper.addon.damage.testing;

import com.mojang.serialization.Lifecycle;
import io.papermc.paper.registry.PaperRegistryAccess;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.UnsafeValues;
import org.bukkit.World;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.damage.CraftDamageSourceBuilder;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "deprecation", "removal"})
public abstract class DamageTestSupport {

    protected static final Map<String, Set<Object>> tagMap = new HashMap<>();

    protected MockedStatic<UtilEvent> utilEventMock;

    // Bootstrap registries only; no MinecraftServer or CraftServer is constructed.
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        if (Bukkit.getServer() == null) {
            final MappedRegistry<net.minecraft.world.damagesource.DamageType> damageTypeMappedRegistry = new MappedRegistry<>(
                    Registries.DAMAGE_TYPE,
                    Lifecycle.stable()
            );
            final BootstrapContext<net.minecraft.world.damagesource.DamageType> bootstrapContext = mock(BootstrapContext.class);
            when(bootstrapContext.register(any(), any())).thenAnswer((final var invocation) -> damageTypeMappedRegistry.register(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    RegistrationInfo.BUILT_IN
            ));
            DamageTypes.bootstrap(bootstrapContext);
            PaperRegistryAccess.instance().registerRegistry(damageTypeMappedRegistry.freeze());

            final UnsafeValues unsafeValues = mock(UnsafeValues.class);
            when(unsafeValues.createDamageSourceBuilder(any())).thenAnswer((final var invocation) -> new CraftDamageSourceBuilder(invocation.getArgument(0)));
            final Server server = mock(Server.class, (final var invocation) -> {
                if (invocation.getMethod().getName().equals("getTag")) {
                    final String key = ((NamespacedKey) invocation.getArgument(1)).getKey();

                    return mock(Tag.class, (final var tagInvocation) -> {
                        if (tagInvocation.getMethod().getName().equals("isTagged")) {
                            return tagMap.getOrDefault(key, Set.of()).contains(tagInvocation.getArgument(0));
                        }

                        return RETURNS_DEFAULTS.answer(tagInvocation);
                    });
                }

                return RETURNS_DEFAULTS.answer(invocation);
            });
            when(server.getUnsafe()).thenReturn(unsafeValues);
            when(server.getLogger()).thenReturn(Logger.getLogger("DamageTests"));
            Bukkit.setServer(server);
        }
    }

    @BeforeEach
    void openDispatcher() {
        tagMap.clear();
        this.utilEventMock = mockStatic(UtilEvent.class);
        this.utilEventMock.when(() -> UtilEvent.supply(any(Event.class))).thenAnswer((final var invocation) -> invocation.getArgument(0));
    }

    @AfterEach
    void closeDispatcher() {
        this.utilEventMock.close();
    }

    protected static Object field(final Object target, final String name) throws ReflectiveOperationException {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);

        return field.get(target);
    }

    protected static void atTime(final CustomDamageEvent customDamageEvent, final long time) throws ReflectiveOperationException {
        final Field field = CustomDamageEvent.class.getDeclaredField("systemTime");
        field.setAccessible(true);
        field.setLong(customDamageEvent, time);
    }

    protected static void tag(final String key, final Object value) {
        tagMap.computeIfAbsent(key, _ -> new HashSet<>()).add(value);
    }

    protected static CustomDamageEvent damage(
            final Entity damageeEntity,
            final Entity damagerEntity,
            final DamageSource damageSource,
            final DamageCause damageCause,
            final double amount
    ) {
        return damage(damageeEntity, damagerEntity, null, damageSource, damageCause, amount, null, null);
    }

    protected static CustomDamageEvent damage(
            final Entity damageeEntity,
            final Entity damagerEntity,
            final Projectile projectile,
            final DamageSource damageSource,
            final DamageCause damageCause,
            final double amount,
            final ItemStack itemStack,
            final ItemStack[] armourContents
    ) {
        return new CustomDamageEvent(
                damageeEntity,
                damagerEntity,
                projectile,
                damageSource,
                damageCause,
                itemStack,
                armourContents,
                amount,
                amount,
                0.0D,
                0.0D,
                0.0D,
                false,
                true,
                false,
                null,
                null
        );
    }

    protected static Entity entity() {
        final Entity entity = mock(Entity.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());

        return entity;
    }

    protected static CraftLivingEntity craft(final LivingEntity livingEntity, final ServerLevel serverLevel) {
        final CraftLivingEntity craftLivingEntity = mock(CraftLivingEntity.class);
        when(craftLivingEntity.getHandle()).thenReturn(livingEntity);
        when(craftLivingEntity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(craftLivingEntity.getLocation()).thenAnswer(_ -> new Location(null, 0.0D, 0.0D, 0.0D));
        when(livingEntity.level()).thenReturn(serverLevel);

        return craftLivingEntity;
    }

    protected static CraftPlayer player(final ServerPlayer serverPlayer, final ServerLevel serverLevel) {
        final CraftPlayer craftPlayer = mock(CraftPlayer.class);
        when(craftPlayer.getHandle()).thenReturn(serverPlayer);
        when(craftPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        final World world = mock(World.class);
        when(craftPlayer.getWorld()).thenReturn(world);
        when(craftPlayer.getLocation()).thenAnswer(_ -> new Location(world, 0.0D, 0.0D, 0.0D));
        when(serverPlayer.level()).thenReturn(serverLevel);

        return craftPlayer;
    }

    protected static CraftDamageSource source(final net.minecraft.world.damagesource.DamageSource damageSource) {
        final CraftDamageSource craftDamageSource = mock(CraftDamageSource.class);
        when(craftDamageSource.getHandle()).thenReturn(damageSource);
        when(craftDamageSource.getDamageType()).thenReturn(org.bukkit.damage.DamageType.GENERIC);

        return craftDamageSource;
    }
}
