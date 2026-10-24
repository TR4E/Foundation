package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damagee.ProtectionReductionEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.tag.DamageTypeTags;

@AddonSingleton
public final class ProtectionReductionListener implements Listener {

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.PROTECTION) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || damageEvent.getDamage() <= 0.0D) {
            return;
        }

        if (DamageTypeTags.BYPASSES_EFFECTS.isTagged(damageEvent.getSource().getDamageType()) || DamageTypeTags.BYPASSES_ENCHANTMENTS.isTagged(damageEvent.getSource().getDamageType())) {
            return;
        }

        if (!CraftLivingEntity.class.isInstance(damageEvent.getDamagee()) || !CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        final DamageSource damageSource = CraftDamageSource.class.cast(damageEvent.getSource()).getHandle();

        final ServerLevel serverLevel = ServerLevel.class.cast(entity.level());

        final float protection = EnchantmentHelper.getDamageProtection(serverLevel, entity, damageSource);

        final ProtectionReductionEvent protectionReductionEvent = UtilEvent.supply(new ProtectionReductionEvent(damageEvent, damageEvent.getDamage(), protection));

        if (protectionReductionEvent.isCancelled()) {
            return;
        }

        damageEvent.setDamage(CombatRules.getDamageAfterMagicAbsorb((float) damageEvent.getDamage(), protectionReductionEvent.getProtection()));
    }
}