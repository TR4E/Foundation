package me.trae.foundation.minecraft.paper.addon.damage.listeners.delay;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.damage.enums.DelayMode;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDelayEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.services.DamageService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.WeakHashMap;

@AllArgsConstructor
@AddonSingleton
public final class ModernDamageDelayListener implements Listener {

    private static final long MILLISECONDS_PER_TICK = 50L;

    private final WeakHashMap<CustomDamageEvent, ModernDelayState> stateMap = new WeakHashMap<>();

    private final WeakHashMap<LivingEntity, Integer> durationMap = new WeakHashMap<>();

    private final DamageService damageService;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomDamage(final CustomDamageEvent event) {
        if (event.isCancelled() || this.damageService.getSettings().getDelayMode() != DelayMode.MODERN) {
            return;
        }

        if (!LivingEntity.class.isInstance(event.getDamagee())) {
            return;
        }

        final LivingEntity entity = LivingEntity.class.cast(event.getDamagee());

        final int previousDuration = this.durationMap.getOrDefault(entity, entity.getMaximumNoDamageTicks());

        this.stateMap.put(event, new ModernDelayState((float) entity.getLastDamage(), previousDuration, entity.getNoDamageTicks()));
    }

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.DELAY || this.damageService.getSettings().getDelayMode() != DelayMode.MODERN) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        final ModernDelayState modernDelayState = this.stateMap.get(damageEvent);
        if (modernDelayState == null || damageEvent.isCancelled()) {
            return;
        }

        final LivingEntity entity = LivingEntity.class.cast(damageEvent.getDamagee());

        final long defaultDuration = entity.getMaximumNoDamageTicks() * MILLISECONDS_PER_TICK;

        final CustomDelayEvent delayEvent = UtilEvent.supply(new CustomDelayEvent(damageEvent, defaultDuration));
        if (delayEvent.isCancelled()) {
            damageEvent.setFullDamage(true);

            this.stateMap.remove(damageEvent);
            return;
        }

        final long durationMillis = Math.max(0L, delayEvent.getDurationMillis());

        final int durationTicks = (int) Math.min(Integer.MAX_VALUE, Math.ceilDiv(durationMillis, MILLISECONDS_PER_TICK));

        final float damage = (float) damageEvent.getDamage();

        final boolean bypassCooldown = CraftDamageSource.class.cast(damageEvent.getSource()).getHandle().is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN);

        if (modernDelayState.getRemainingTicks() > (float) modernDelayState.getPreviousDuration() / 2.0F && !bypassCooldown) {
            if (damage <= modernDelayState.getLastDamage()) {
                damageEvent.setCancelled(true);

                this.stateMap.remove(damageEvent);
                return;
            }

            damageEvent.setDamage(damage - modernDelayState.getLastDamage());

            damageEvent.setFullDamage(false);
        } else {
            damageEvent.setFullDamage(true);
        }

        modernDelayState.setDurationTicks(durationTicks);

        modernDelayState.setIncomingDamage(damage);

        modernDelayState.setPrepared(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageApplied(final DamageAppliedEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        final ModernDelayState state = this.stateMap.remove(damageEvent);
        if (state == null || !state.isPrepared() || damageEvent.isCancelled()) {
            return;
        }

        final CraftLivingEntity entity = CraftLivingEntity.class.cast(damageEvent.getDamagee());

        entity.setLastDamage(Math.max(state.getIncomingDamage() - (float) damageEvent.getBlockedDamage(), 0.0F));

        if (!damageEvent.isFullDamage()) {
            return;
        }

        entity.setNoDamageTicks(state.getDurationTicks());

        this.durationMap.put(entity, state.getDurationTicks());

        final net.minecraft.world.entity.LivingEntity handle = entity.getHandle();

        handle.hurtDuration = 10;

        handle.hurtTime = handle.hurtDuration;
    }
}