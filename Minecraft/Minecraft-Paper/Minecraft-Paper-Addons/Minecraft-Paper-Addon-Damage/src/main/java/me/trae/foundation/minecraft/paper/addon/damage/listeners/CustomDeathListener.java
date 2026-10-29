package me.trae.foundation.minecraft.paper.addon.damage.listeners;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;

@AddonSingleton
public final class CustomDeathListener implements Listener {

    private static final Method TOTEM_METHOD = getTotemMethod();

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.DEATH) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || !CraftLivingEntity.class.isInstance(damageEvent.getDamagee())) {
            return;
        }

        final LivingEntity entity = CraftLivingEntity.class.cast(damageEvent.getDamagee()).getHandle();

        if (!entity.isDeadOrDying()) {
            return;
        }

        final DamageSource damageSource = CraftDamageSource.class.isInstance(damageEvent.getSource()) ? CraftDamageSource.class.cast(damageEvent.getSource()).getHandle() : entity.level().damageSources().generic();

        try {
            if (Boolean.TRUE.equals(TOTEM_METHOD.invoke(entity, damageSource))) {
                return;
            }
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to check totem death protection", exception);
        }

        final boolean previousSilentDeath = entity.silentDeath;

        entity.silentDeath = previousSilentDeath || !damageEvent.isFullDamage();

        try {
            entity.die(damageSource);
        } finally {
            entity.silentDeath = previousSilentDeath;
        }
    }

    private static Method getTotemMethod() {
        try {
            final Method method = LivingEntity.class.getDeclaredMethod("checkTotemDeathProtection", DamageSource.class);

            method.setAccessible(true);

            return method;
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access totem death protection", exception);
        }
    }
}