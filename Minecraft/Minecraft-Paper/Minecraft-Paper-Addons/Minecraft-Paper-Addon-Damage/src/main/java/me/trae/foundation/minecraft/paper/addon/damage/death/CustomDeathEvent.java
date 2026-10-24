package me.trae.foundation.minecraft.paper.addon.damage.death;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.death.events.DeathEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.stash.SoundStash;
import net.kyori.adventure.text.Component;
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

    private final CustomDamageEvent damageEvent;
    private final List<ItemStack> drops;

    private int dropExp;
    private SoundStash soundStash;

    public CustomDeathEvent(final EntityDeathEvent entityDeathEvent, final CustomDamageEvent customDamageEvent) {
        this(
                customDamageEvent,
                new ArrayList<>(entityDeathEvent.getDrops()),
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

    @Override
    public Component getReasonName() {
        return null;
    }

    @Override
    public boolean isReasonNameWithIndefiniteArticlePrefix() {
        return false;
    }
}