package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damagee.ResistanceReductionEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.tag.DamageTypeTags;

@AddonSingleton
public final class ResistanceReductionListener implements Listener {

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.RESISTANCE) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || DamageTypeTags.BYPASSES_EFFECTS.isTagged(damageEvent.getSource().getDamageType()) || DamageTypeTags.BYPASSES_RESISTANCE.isTagged(damageEvent.getSource().getDamageType())) {
            return;
        }

        if (!LivingEntity.class.isInstance(damageEvent.getDamagee())) {
            return;
        }

        final LivingEntity entity = LivingEntity.class.cast(damageEvent.getDamagee());

        final PotionEffect resistance = entity.getPotionEffect(PotionEffectType.RESISTANCE);
        if (resistance == null) {
            return;
        }

        final int amplifier = resistance.getAmplifier();

        final float damage = (float) damageEvent.getDamage();

        final float reducedDamage = Math.max(damage * (25 - (amplifier + 1) * 5) / 25.0F, 0.0F);

        final ResistanceReductionEvent resistanceReductionEvent = UtilEvent.supply(new ResistanceReductionEvent(damageEvent, amplifier, reducedDamage));

        if (resistanceReductionEvent.isCancelled()) {
            return;
        }

        damageEvent.setDamage(Math.max(0.0D, resistanceReductionEvent.getReducedDamage()));
    }
}