package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class AttackSnapshotListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {"matched", "old", "differentTarget"})
    void consumesPendingSnapshotAndRejectsAgeOrTargetMismatch(final String condition) throws Exception {
        final ServerPlayer serverPlayer = mock(ServerPlayer.class);
        final CraftPlayer craftPlayer = player(serverPlayer, mock(ServerLevel.class));
        when(serverPlayer.getAttackStrengthScale(0.5F)).thenReturn(0.8F);
        final Entity targetEntity = entity();
        final AttackSnapshotListener attackSnapshotListener = new AttackSnapshotListener();
        final PrePlayerAttackEntityEvent prePlayerAttackEntityEvent = mock(PrePlayerAttackEntityEvent.class);
        when(prePlayerAttackEntityEvent.willAttack()).thenReturn(true);
        when(prePlayerAttackEntityEvent.getPlayer()).thenReturn(craftPlayer);
        when(prePlayerAttackEntityEvent.getAttacked()).thenReturn(targetEntity);
        attackSnapshotListener.onPreAttack(prePlayerAttackEntityEvent);
        final CustomDamageEvent customDamageEvent = damage(
                condition.equals("differentTarget") ? entity() : targetEntity,
                craftPlayer,
                source(mock(DamageSource.class)),
                DamageCause.ENTITY_ATTACK,
                4.0D
        );
        final Object pendingAttack = ((Map<?, ?>) field(attackSnapshotListener, "pendingMap")).get(craftPlayer.getUniqueId());
        atTime(customDamageEvent, (long) field(pendingAttack, "systemTime") + (condition.equals("old") ? 51L : 50L));
        attackSnapshotListener.onCustomDamage(customDamageEvent);

        assertEquals(condition.equals("matched"), attackSnapshotListener.getSnapshot(customDamageEvent) != null);
        assertTrue(((Map<?, ?>) field(attackSnapshotListener, "pendingMap")).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void removalCleansPendingAttackerAndTargetReferences(final boolean removeAttacker) throws Exception {
        final CraftPlayer craftPlayer = player(mock(ServerPlayer.class), mock(ServerLevel.class));
        final Entity targetEntity = entity();
        final AttackSnapshotListener attackSnapshotListener = new AttackSnapshotListener();
        final PrePlayerAttackEntityEvent prePlayerAttackEntityEvent = mock(PrePlayerAttackEntityEvent.class);
        when(prePlayerAttackEntityEvent.willAttack()).thenReturn(true);
        when(prePlayerAttackEntityEvent.getPlayer()).thenReturn(craftPlayer);
        when(prePlayerAttackEntityEvent.getAttacked()).thenReturn(targetEntity);
        attackSnapshotListener.onPreAttack(prePlayerAttackEntityEvent);
        assertEquals(1, ((Map<?, ?>) field(attackSnapshotListener, "pendingMap")).size());
        final EntityRemoveFromWorldEvent entityRemoveFromWorldEvent = mock(EntityRemoveFromWorldEvent.class);
        when(entityRemoveFromWorldEvent.getEntity()).thenReturn(removeAttacker ? craftPlayer : targetEntity);
        attackSnapshotListener.onEntityRemove(entityRemoveFromWorldEvent);

        assertTrue(((Map<?, ?>) field(attackSnapshotListener, "pendingMap")).isEmpty());
    }
}