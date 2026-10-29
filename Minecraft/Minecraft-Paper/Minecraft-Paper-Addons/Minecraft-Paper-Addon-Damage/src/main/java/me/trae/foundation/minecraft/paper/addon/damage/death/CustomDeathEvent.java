package me.trae.foundation.minecraft.paper.addon.damage.death;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.death.events.DeathEvent;
import me.trae.foundation.minecraft.paper.addon.death.reason.Reason;
import me.trae.foundation.minecraft.paper.addon.death.settings.DeathAddonSettings;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.stash.SoundStash;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter
public final class CustomDeathEvent extends CustomEvent implements DeathEvent {

    private final long systemTime = System.currentTimeMillis();

    private final DeathAddonSettings settings;

    private final CustomDamageEvent originalDamageEvent, damageEvent;
    private final List<ItemStack> drops;
    private final Reason reason;

    private int dropExp;
    private SoundStash soundStash;

    public CustomDeathEvent(final EntityDeathEvent entityDeathEvent, final CustomDamageEvent originalDamageEvent, final CustomDamageEvent damageEvent, final DeathAddonSettings settings) {
        this(
                settings,
                originalDamageEvent,
                damageEvent,
                new ArrayList<>(entityDeathEvent.getDrops()),
                damageEvent.getReason(),
                entityDeathEvent.getDroppedExp(),
                SoundStash.of(entityDeathEvent.getDeathSound(), entityDeathEvent.getDeathSoundCategory(), entityDeathEvent.getDeathSoundVolume(), entityDeathEvent.getDeathSoundPitch())
        );
    }

    @Override
    public LivingEntity getEntity() {
        return LivingEntity.class.cast(this.damageEvent.getDamagee());
    }

    @Override
    public Entity getKiller() {
        return this.damageEvent.getDamager();
    }

    @Override
    public EntityDamageEvent.DamageCause getCause() {
        return this.damageEvent.getCause();
    }

    @Override
    public ItemStack getItemStack() {
        return this.damageEvent.getItemStack();
    }
}