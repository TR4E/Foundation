package me.trae.foundation.minecraft.paper.addon.damage.listeners;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageKnockbackEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackKnockbackListener;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackSnapshotListener;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackSnapshotListener.AttackSnapshot;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.TreeMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class DamageKnockbackListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @EnumSource(value = Stage.class, names = "KNOCKBACK", mode = EnumSource.Mode.EXCLUDE)
    void skipsEveryOtherStage(final Stage stage) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(ServerLevel.class)), null,
                source(mock(DamageSource.class)), DamageCause.CUSTOM, 4.0D
        );
        new DamageKnockbackListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, stage));

        verify(livingEntity, never()).knockback(anyDouble(), anyDouble(), anyDouble(), any(), any());
        verify(livingEntity, never()).indicateDamage(anyDouble(), anyDouble());
        this.utilEventMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,false"})
    void skipsCancelledAndPartialHits(final boolean cancelled, final boolean fullDamage) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(ServerLevel.class)), null,
                source(mock(DamageSource.class)), DamageCause.CUSTOM, 4.0D
        );
        customDamageEvent.setCancelled(cancelled);
        customDamageEvent.setFullDamage(fullDamage);
        new DamageKnockbackListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.KNOCKBACK));

        verify(livingEntity, never()).knockback(anyDouble(), anyDouble(), anyDouble(), any(), any());
        verify(livingEntity, never()).indicateDamage(anyDouble(), anyDouble());
        this.utilEventMock.verifyNoInteractions();
    }

    @Test
    void noKnockbackTagSkipsTheKnockbackCallAndEvent() {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.is(DamageTypeTags.NO_KNOCKBACK)).thenReturn(true);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(ServerLevel.class)), null, source(damageSource), DamageCause.CUSTOM, 4.0D
        );
        new DamageKnockbackListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.KNOCKBACK));

        verify(livingEntity, never()).knockback(anyDouble(), anyDouble(), anyDouble(), any(), any());
        verify(livingEntity, never()).indicateDamage(anyDouble(), anyDouble());
        this.utilEventMock.verifyNoInteractions();
    }

    @Test
    void projectileDirectionNegatesBothComponentsAndTakesPrecedenceOverSourcePosition() {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final DamageSource damageSource = mock(DamageSource.class);
        final Projectile projectile = mock(Projectile.class);
        when(damageSource.getDirectEntity()).thenReturn(projectile);
        when(damageSource.getSourcePosition()).thenReturn(new Vec3(30.0D, 40.0D, 50.0D));
        when(projectile.calculateHorizontalHurtKnockbackDirection(livingEntity, damageSource))
                .thenReturn(new DoubleDoubleImmutablePair(0.25D, -0.75D));
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(ServerLevel.class)), null, source(damageSource), DamageCause.PROJECTILE, 4.0D
        );
        new DamageKnockbackListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.KNOCKBACK));

        verify(projectile).calculateHorizontalHurtKnockbackDirection(livingEntity, damageSource);
        verify(livingEntity).knockback(0.4D, -0.25D, 0.75D, projectile, EntityKnockbackEvent.Cause.ENTITY_ATTACK);
        verify(livingEntity).indicateDamage(-0.25D, 0.75D);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sourcePositionSubtractsTargetCoordinatesAndDirectEntitySelectsCause(final boolean hasDirectEntity) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        when(livingEntity.getX()).thenReturn(10.0D);
        when(livingEntity.getZ()).thenReturn(-4.0D);
        final DamageSource damageSource = mock(DamageSource.class);
        final net.minecraft.world.entity.Entity directEntity = hasDirectEntity ? mock(net.minecraft.world.entity.Entity.class) : null;
        when(damageSource.getDirectEntity()).thenReturn(directEntity);
        when(damageSource.getSourcePosition()).thenReturn(new Vec3(16.0D, 100.0D, 9.0D));
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(ServerLevel.class)), null, source(damageSource), DamageCause.CUSTOM, 4.0D
        );
        new DamageKnockbackListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.KNOCKBACK));

        verify(livingEntity).knockback(
                0.4D, 6.0D, 13.0D, directEntity,
                hasDirectEntity ? EntityKnockbackEvent.Cause.ENTITY_ATTACK : EntityKnockbackEvent.Cause.DAMAGE
        );
        verify(livingEntity).indicateDamage(6.0D, 13.0D);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void absentProjectileAndSourcePositionLeaveBothDirectionsExactlyZero(final boolean hasDirectEntity) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        when(livingEntity.getX()).thenReturn(19.0D);
        when(livingEntity.getZ()).thenReturn(-23.0D);
        final DamageSource damageSource = mock(DamageSource.class);
        final net.minecraft.world.entity.Entity directEntity = hasDirectEntity ? mock(net.minecraft.world.entity.Entity.class) : null;
        when(damageSource.getDirectEntity()).thenReturn(directEntity);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(ServerLevel.class)), null, source(damageSource), DamageCause.CUSTOM, 4.0D
        );
        new DamageKnockbackListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.KNOCKBACK));

        verify(livingEntity).knockback(
                0.4D, 0.0D, 0.0D, directEntity,
                hasDirectEntity ? EntityKnockbackEvent.Cause.ENTITY_ATTACK : EntityKnockbackEvent.Cause.DAMAGE
        );
        verify(livingEntity).indicateDamage(0.0D, 0.0D);
    }

    @ParameterizedTest
    @CsvSource({"201,12", "-201,12", "12,201", "12,-201", "201,-201", "200,-200", "-200,200"})
    void replacesOnlyAxesWhoseAbsoluteDirectionExceedsTwoHundred(final double directionX, final double directionZ) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(ServerLevel.class)), null,
                source(mock(DamageSource.class)), DamageCause.CUSTOM, 4.0D
        );
        this.utilEventMock.when(() -> UtilEvent.supply(any(DamageKnockbackEvent.class))).thenAnswer((final var invocation) -> {
            final DamageKnockbackEvent damageKnockbackEvent = invocation.getArgument(0);
            damageKnockbackEvent.setDirectionX(directionX);
            damageKnockbackEvent.setDirectionZ(directionZ);

            return damageKnockbackEvent;
        });
        new DamageKnockbackListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.KNOCKBACK));

        final ArgumentCaptor<Double> directionXArgumentCaptor = ArgumentCaptor.forClass(Double.class);
        final ArgumentCaptor<Double> directionZArgumentCaptor = ArgumentCaptor.forClass(Double.class);
        verify(livingEntity).knockback(
                eq(0.4D), directionXArgumentCaptor.capture(), directionZArgumentCaptor.capture(),
                isNull(), eq(EntityKnockbackEvent.Cause.DAMAGE)
        );

        if (Math.abs(directionX) > 200.0D) {
            assertTrue(Math.abs(directionXArgumentCaptor.getValue()) < 1.0D);
        } else {
            assertEquals(directionX, directionXArgumentCaptor.getValue());
        }

        if (Math.abs(directionZ) > 200.0D) {
            assertTrue(Math.abs(directionZArgumentCaptor.getValue()) < 1.0D);
        } else {
            assertEquals(directionZ, directionZArgumentCaptor.getValue());
        }

        verify(livingEntity).indicateDamage(directionXArgumentCaptor.getValue(), directionZArgumentCaptor.getValue());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0D, 0.0D, 1.0D})
    void indicatesDamageOnlyWhenBlockedDamageIsNonpositive(final double blockedDamage) {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, mock(ServerLevel.class)), null,
                source(mock(DamageSource.class)), DamageCause.CUSTOM, 4.0D
        );
        customDamageEvent.setBlockedDamage(blockedDamage);
        new DamageKnockbackListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.KNOCKBACK));

        verify(livingEntity).knockback(0.4D, 0.0D, 0.0D, null, EntityKnockbackEvent.Cause.DAMAGE);
        verify(livingEntity, times(blockedDamage <= 0.0D ? 1 : 0)).indicateDamage(anyDouble(), anyDouble());
    }

    @Test
    void cancelledKnockbackEventSuppressesKnockbackIndicationAndSprintReset() {
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final CraftPlayer craftPlayer = player(mock(ServerPlayer.class), serverLevel);
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, serverLevel), craftPlayer,
                source(mock(DamageSource.class)), DamageCause.ENTITY_ATTACK, 4.0D
        );
        this.utilEventMock.when(() -> UtilEvent.supply(any(DamageKnockbackEvent.class))).thenAnswer((final var invocation) -> {
            final DamageKnockbackEvent damageKnockbackEvent = invocation.getArgument(0);
            damageKnockbackEvent.setSprintReset(true);
            damageKnockbackEvent.setCancelled(true);

            return damageKnockbackEvent;
        });
        new DamageKnockbackListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.KNOCKBACK));

        verify(livingEntity, never()).knockback(anyDouble(), anyDouble(), anyDouble(), any(), any());
        verify(livingEntity, never()).indicateDamage(anyDouble(), anyDouble());
        verify(craftPlayer, never()).setSprinting(false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sprintResetRequiresTheFinalFlag(final boolean sprintReset) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final CraftPlayer craftPlayer = player(mock(ServerPlayer.class), serverLevel);
        final CustomDamageEvent customDamageEvent = damage(
                craft(mock(LivingEntity.class), serverLevel), craftPlayer,
                source(mock(DamageSource.class)), DamageCause.ENTITY_ATTACK, 4.0D
        );
        this.utilEventMock.when(() -> UtilEvent.supply(any(DamageKnockbackEvent.class))).thenAnswer((final var invocation) -> {
            final DamageKnockbackEvent damageKnockbackEvent = invocation.getArgument(0);
            assertEquals(0.4D, damageKnockbackEvent.getStrength());
            assertFalse(damageKnockbackEvent.isSprintReset());
            damageKnockbackEvent.setSprintReset(sprintReset);

            return damageKnockbackEvent;
        });
        new DamageKnockbackListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.KNOCKBACK));

        verify(craftPlayer, times(sprintReset ? 1 : 0)).setSprinting(false);
    }

    @ParameterizedTest
    @CsvSource({"NORMAL,true", "HIGH,true", "HIGHEST,true", "NORMAL,false"})
    void normalOrHigherPriorityPluginCanPreventSprintReset(final EventPriority pluginPriority, final boolean override) throws Exception {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final CraftPlayer craftPlayer = player(mock(ServerPlayer.class), serverLevel);
        final CustomDamageEvent customDamageEvent = damage(
                craft(mock(LivingEntity.class), serverLevel), craftPlayer,
                source(mock(DamageSource.class)), DamageCause.ENTITY_ATTACK, 4.0D
        );
        final AttackSnapshotListener attackSnapshotListener = mock(AttackSnapshotListener.class);
        when(attackSnapshotListener.getSnapshot(customDamageEvent)).thenReturn(new AttackSnapshot(1.0F, true, true, false));
        final AttackKnockbackListener attackKnockbackListener = new AttackKnockbackListener(attackSnapshotListener);
        final EventPriority defaultPriority = AttackKnockbackListener.class
                .getMethod("onDamageKnockback", DamageKnockbackEvent.class)
                .getAnnotation(EventHandler.class)
                .priority();
        assertEquals(EventPriority.LOW, defaultPriority);
        final TreeMap<EventPriority, Consumer<DamageKnockbackEvent>> eventHandlerTreeMap = new TreeMap<>();
        eventHandlerTreeMap.put(defaultPriority, attackKnockbackListener::onDamageKnockback);
        eventHandlerTreeMap.put(pluginPriority, (final DamageKnockbackEvent damageKnockbackEvent) -> {
            assertTrue(damageKnockbackEvent.isSprintReset());
            assertEquals(0.4D, damageKnockbackEvent.getStrength());

            if (override) {
                damageKnockbackEvent.setSprintReset(false);
            }
        });
        this.utilEventMock.when(() -> UtilEvent.supply(any(DamageKnockbackEvent.class))).thenAnswer((final var invocation) -> {
            final DamageKnockbackEvent damageKnockbackEvent = invocation.getArgument(0);
            eventHandlerTreeMap.values().forEach((final var consumer) -> consumer.accept(damageKnockbackEvent));

            return damageKnockbackEvent;
        });

        try (final MockedStatic<EnchantmentHelper> enchantmentHelperMockedStatic = mockStatic(EnchantmentHelper.class)) {
            new DamageKnockbackListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.KNOCKBACK));
        }

        verify(craftPlayer, times(override ? 0 : 1)).setSprinting(false);
    }
}
