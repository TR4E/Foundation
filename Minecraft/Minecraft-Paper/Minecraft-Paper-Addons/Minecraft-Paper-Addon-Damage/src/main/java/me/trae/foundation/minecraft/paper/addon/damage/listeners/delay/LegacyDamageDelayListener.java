package me.trae.foundation.minecraft.paper.addon.damage.listeners.delay;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import me.trae.foundation.minecraft.paper.addon.damage.enums.DelayMode;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDelayEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.settings.DamageSettings;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@AddonSingleton
public final class LegacyDamageDelayListener implements Listener {

    private final WeakHashMap<CustomDamageEvent, LegacyDelayState> stateMap = new WeakHashMap<>();

    private final Map<UUID, Map<DamageCause, Long>> causeMap = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> damagerMap = new HashMap<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomDamage(final CustomDamageEvent event) {
        if (event.isCancelled() || DamageSettings.getDelayMode() != DelayMode.LEGACY) {
            return;
        }

        final UUID damageeId = event.getDamagee().getUniqueId();

        final UUID damagerId = event.getDamager() != null ? event.getDamager().getUniqueId() : null;

        final long expiry = damagerId != null ? this.damagerMap.getOrDefault(damageeId, Map.of()).getOrDefault(damagerId, 0L) : this.causeMap.getOrDefault(damageeId, Map.of()).getOrDefault(event.getCause(), 0L);

        if (event.getSystemTime() < expiry) {
            event.setCancelled(true);
            return;
        }

        this.stateMap.put(event, new LegacyDelayState(damageeId, damagerId, event.getCause(), event.getSystemTime()));
    }

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.DELAY || DamageSettings.getDelayMode() != DelayMode.LEGACY) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        final LegacyDelayState state = this.stateMap.get(damageEvent);

        if (state == null || damageEvent.isCancelled() || !damageEvent.hasDamage()) {
            return;
        }

        final CustomDelayEvent delayEvent = UtilEvent.supply(new CustomDelayEvent(damageEvent, this.getDefaultDuration(damageEvent.getCause())));
        if (delayEvent.isCancelled()) {
            damageEvent.setFullDamage(true);

            this.stateMap.remove(damageEvent);
            return;
        }

        state.setDurationMillis(Math.max(0L, delayEvent.getDurationMillis()));

        damageEvent.setFullDamage(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageApplied(final DamageAppliedEvent event) {
        final LegacyDelayState state = this.stateMap.remove(event.getDamageEvent());

        if (state == null || event.getDamageEvent().isCancelled()) {
            return;
        }

        final long expiry = state.getDurationMillis() > Long.MAX_VALUE - state.getSystemTime() ? Long.MAX_VALUE : state.getSystemTime() + state.getDurationMillis();

        if (state.getDamagerId() != null) {
            this.damagerMap.computeIfAbsent(state.getDamageeId(), _ -> new HashMap<>()).put(state.getDamagerId(), expiry);
        } else {
            this.causeMap.computeIfAbsent(state.getDamageeId(), _ -> new HashMap<>()).put(state.getCause(), expiry);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(final EntityRemoveFromWorldEvent entityRemoveFromWorldEvent) {
        final UUID entityId = entityRemoveFromWorldEvent.getEntity().getUniqueId();

        this.causeMap.remove(entityId);
        this.damagerMap.remove(entityId);

        this.damagerMap.values().removeIf(expiryMap -> {
            expiryMap.remove(entityId);

            return expiryMap.isEmpty();
        });

        this.stateMap.values().removeIf(legacyDelayState -> {
            return entityId.equals(legacyDelayState.getDamageeId()) || entityId.equals(legacyDelayState.getDamagerId());
        });
    }

    private long getDefaultDuration(final DamageCause damageCause) {
        return switch (damageCause) {
            case STARVATION -> 4000L;
            case WITHER, FREEZE -> 2000L;
            case POISON -> 1250L;
            case FIRE_TICK, DROWNING -> 1000L;
            default -> 500L;
        };
    }
}