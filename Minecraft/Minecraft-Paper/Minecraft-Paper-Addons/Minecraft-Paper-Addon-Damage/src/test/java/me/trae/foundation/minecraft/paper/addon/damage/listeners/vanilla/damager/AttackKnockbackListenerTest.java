package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageKnockbackEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackSnapshotListener.AttackSnapshot;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class AttackKnockbackListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({
            "ENTITY_ATTACK,true,1,2,1.5,true,90",
            "ENTITY_ATTACK,false,1,2,1,true,0",
            "ENTITY_ATTACK,true,0.9,2,1,true,180",
            "ENTITY_ATTACK,false,1,0,0,false,45",
            "ENTITY_SWEEP_ATTACK,true,1,2,0,false,90"
    })
    void attackKnockbackIsSeparateFromBaseStrengthAndDampsAttacker(
            final DamageCause damageCause,
            final boolean sprint,
            final float strength,
            final float modifiedKnockback,
            final double expectedKnockback,
            final boolean reset,
            final float yaw
    ) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final DamageSource damageSource = mock(DamageSource.class);
        final ItemStack itemStack = mock(ItemStack.class);
        when(serverPlayer.getWeaponItem()).thenReturn(itemStack);
        when(serverPlayer.getAttributeValue(Attributes.ATTACK_KNOCKBACK)).thenReturn(1.0D);
        when(serverPlayer.getYRot()).thenReturn(yaw);
        when(serverPlayer.getDeltaMovement()).thenReturn(new Vec3(5.0D, 2.0D, -5.0D));
        final CustomDamageEvent customDamageEvent = damage(
                craft(livingEntity, serverLevel),
                player(serverPlayer, serverLevel),
                source(damageSource),
                damageCause,
                4.0D
        );
        final AttackSnapshotListener attackSnapshotListener = mock(AttackSnapshotListener.class);
        when(attackSnapshotListener.getSnapshot(customDamageEvent)).thenReturn(new AttackSnapshot(strength, sprint, true, false));
        final AttackKnockbackListener attackKnockbackListener = new AttackKnockbackListener(attackSnapshotListener);
        final DamageKnockbackEvent damageKnockbackEvent = new DamageKnockbackEvent(customDamageEvent, 0.0D, 0.0D, 0.4D, false);

        try (final MockedStatic<EnchantmentHelper> enchantmentHelperMockedStatic = mockStatic(EnchantmentHelper.class)) {
            enchantmentHelperMockedStatic.when(() -> EnchantmentHelper.modifyKnockback(
                    serverLevel, itemStack, livingEntity, damageSource, 1.0F
            )).thenReturn(modifiedKnockback);
            attackKnockbackListener.onCustomDamage(customDamageEvent);
            attackKnockbackListener.onDamageKnockback(damageKnockbackEvent);
            assertEquals(0.4D, damageKnockbackEvent.getStrength());
            assertEquals(reset, damageKnockbackEvent.isSprintReset());
            attackKnockbackListener.onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.KNOCKBACK));

            if (expectedKnockback > 0.0D) {
                verify(livingEntity).knockback(
                        eq(expectedKnockback),
                        doubleThat((final var direction) -> Math.abs(direction - Math.sin(Math.toRadians(yaw))) < 0.00001D),
                        doubleThat((final var direction) -> Math.abs(direction + Math.cos(Math.toRadians(yaw))) < 0.00001D),
                        eq(serverPlayer),
                        eq(EntityKnockbackEvent.Cause.ENTITY_ATTACK)
                );
                verify(serverPlayer).setDeltaMovement(new Vec3(3.0D, 2.0D, -3.0D));
            } else {
                verify(livingEntity, never()).knockback(anyDouble(), anyDouble(), anyDouble(), any(), any());
                verify(serverPlayer, never()).setDeltaMovement(any(Vec3.class));
            }

            enchantmentHelperMockedStatic.verify(
                    () -> EnchantmentHelper.modifyKnockback(serverLevel, itemStack, livingEntity, damageSource, 1.0F),
                    times(damageCause == DamageCause.ENTITY_ATTACK ? 1 : 0)
            );
        }
    }

    @Test
    void markedPlayerResyncRestoresMovementCapturedBeforeHit() {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final ServerPlayer attackerServerPlayer = mock(ServerPlayer.class);
        final ServerPlayer targetServerPlayer = mock(ServerPlayer.class);
        targetServerPlayer.connection = mock(ServerGamePacketListenerImpl.class);
        final Vec3 previousMovementVec3 = new Vec3(0.2D, 0.3D, 0.4D);
        when(targetServerPlayer.getDeltaMovement()).thenReturn(previousMovementVec3);
        when(attackerServerPlayer.getDeltaMovement()).thenReturn(Vec3.ZERO);
        final CustomDamageEvent customDamageEvent = damage(
                player(targetServerPlayer, serverLevel), player(attackerServerPlayer, serverLevel),
                source(mock(DamageSource.class)), DamageCause.ENTITY_ATTACK, 4.0D
        );
        final AttackSnapshotListener attackSnapshotListener = mock(AttackSnapshotListener.class);
        when(attackSnapshotListener.getSnapshot(customDamageEvent)).thenReturn(new AttackSnapshot(1.0F, true, true, false));
        final AttackKnockbackListener attackKnockbackListener = new AttackKnockbackListener(attackSnapshotListener);
        attackKnockbackListener.onCustomDamage(customDamageEvent);
        when(targetServerPlayer.getDeltaMovement()).thenReturn(new Vec3(1.0D, 2.0D, 3.0D));
        targetServerPlayer.hurtMarked = true;

        try (final MockedStatic<EnchantmentHelper> enchantmentHelperMockedStatic = mockStatic(EnchantmentHelper.class)) {
            attackKnockbackListener.onDamageKnockback(new DamageKnockbackEvent(customDamageEvent, 0.0D, 0.0D, 0.4D, false));
            attackKnockbackListener.onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.KNOCKBACK));
        }

        final InOrder inOrder = inOrder(targetServerPlayer, targetServerPlayer.connection);
        inOrder.verify(targetServerPlayer).knockback(0.5D, 0.0D, -1.0D, attackerServerPlayer, EntityKnockbackEvent.Cause.ENTITY_ATTACK);
        inOrder.verify(targetServerPlayer.connection).send(any(ClientboundSetEntityMotionPacket.class));
        inOrder.verify(targetServerPlayer).setDeltaMovement(previousMovementVec3);
        assertFalse(targetServerPlayer.hurtMarked);
    }

    @Test
    void sprintResetDefaultIsWrittenAtLowPriority() throws Exception {
        assertEquals(
                EventPriority.LOW,
                AttackKnockbackListener.class.getMethod("onDamageKnockback", DamageKnockbackEvent.class).getAnnotation(EventHandler.class).priority()
        );
    }
}
