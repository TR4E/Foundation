package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import com.destroystokyo.paper.event.player.PlayerAttackEntityCooldownResetEvent;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@AddonSingleton
public final class AttackCooldownResetListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.FREEZING) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !CraftPlayer.class.isInstance(damageEvent.getDamager()) || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee())) {
            return;
        }

        final ServerPlayer attacker = CraftPlayer.class.cast(damageEvent.getDamager()).getHandle();

        if (attacker.isUsingItem() && attacker.getUseItem().has(DataComponents.KINETIC_WEAPON)) {
            return;
        }

        if (!new PlayerAttackEntityCooldownResetEvent(attacker.getBukkitEntity(), damageEvent.getDamagee(), attacker.getAttackStrengthScale(0.0F)).callEvent()) {
            return;
        }

        attacker.resetOnlyAttackStrengthTicker();
    }
}