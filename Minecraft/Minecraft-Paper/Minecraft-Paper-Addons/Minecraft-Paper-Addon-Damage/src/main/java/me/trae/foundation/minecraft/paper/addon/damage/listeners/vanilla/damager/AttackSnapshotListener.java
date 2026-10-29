package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.world.entity.player.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.UUID;
import java.util.WeakHashMap;

@AddonSingleton
public final class AttackSnapshotListener implements Listener {

    private static final Method SWEEP_METHOD = getSweepMethod();

    private static final long MAXIMUM_AGE = 50L;

    private static final float STRONG_STRENGTH = 0.9F;

    private final WeakHashMap<CustomDamageEvent, AttackSnapshot> snapshotMap = new WeakHashMap<>();

    private final HashMap<UUID, PendingAttack> pendingMap = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreAttack(final PrePlayerAttackEntityEvent event) {
        if (!event.willAttack()) {
            return;
        }

        final org.bukkit.entity.Player player = event.getPlayer();

        final float strength = CraftPlayer.class.cast(player).getHandle().getAttackStrengthScale(0.5F);

        this.pendingMap.put(player.getUniqueId(), new PendingAttack(event.getAttacked().getUniqueId(), new AttackSnapshot(strength, player.isSprinting(), player.isOnGround(), false), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomDamage(final CustomDamageEvent event) {
        if (event.getProjectile() != null || !event.isMelee() || !org.bukkit.entity.Player.class.isInstance(event.getDamager())) {
            return;
        }

        final PendingAttack pendingAttack = this.pendingMap.get(event.getDamager().getUniqueId());

        if (pendingAttack == null) {
            return;
        }

        if (!pendingAttack.targetId().equals(event.getDamagee().getUniqueId()) || event.getSystemTime() - pendingAttack.systemTime() > MAXIMUM_AGE) {
            this.pendingMap.remove(event.getDamager().getUniqueId());
            return;
        }

        this.pendingMap.remove(event.getDamager().getUniqueId());

        final AttackSnapshot attackSnapshot = pendingAttack.snapshot();

        final Player player = CraftPlayer.class.cast(event.getDamager()).getHandle();

        try {
            final boolean sweeping = Boolean.TRUE.equals(SWEEP_METHOD.invoke(player, attackSnapshot.strength() > STRONG_STRENGTH, event.isCritical(), attackSnapshot.sprinting() && attackSnapshot.strength() > STRONG_STRENGTH));

            this.snapshotMap.put(event, new AttackSnapshot(attackSnapshot.strength(), attackSnapshot.sprinting(), attackSnapshot.onGround(), sweeping));
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to check sweeping attack eligibility", exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(final EntityRemoveFromWorldEvent event) {
        final UUID entityId = event.getEntity().getUniqueId();

        this.pendingMap.remove(entityId);

        this.pendingMap.values().removeIf(pendingAttack -> entityId.equals(pendingAttack.targetId()));
    }

    public AttackSnapshot getSnapshot(final CustomDamageEvent damageEvent) {
        return this.snapshotMap.get(damageEvent);
    }

    private static Method getSweepMethod() {
        try {
            final Method method = Player.class.getDeclaredMethod("isSweepAttack", boolean.class, boolean.class, boolean.class);

            method.setAccessible(true);

            return method;
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access sweeping attack eligibility", exception);
        }
    }

    public record AttackSnapshot(float strength, boolean sprinting, boolean onGround, boolean sweeping) {
    }

    private record PendingAttack(UUID targetId, AttackSnapshot snapshot, long systemTime) {
    }
}