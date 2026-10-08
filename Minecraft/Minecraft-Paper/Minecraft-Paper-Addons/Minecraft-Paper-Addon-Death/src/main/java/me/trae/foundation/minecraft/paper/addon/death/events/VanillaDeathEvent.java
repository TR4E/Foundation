package me.trae.foundation.minecraft.paper.addon.death.events;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.stash.SoundStash;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import me.trae.foundation.utilities.UtilString;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter
public final class VanillaDeathEvent extends CustomEvent implements DeathEvent {

    private final LivingEntity entity;
    private final Entity killer;
    private final DamageCause cause;
    private final ItemStack itemStack;
    private final List<ItemStack> drops;

    private int dropExp;
    private SoundStash soundStash;

    public VanillaDeathEvent(final EntityDeathEvent entityDeathEvent) {
        final LivingEntity entity = entityDeathEvent.getEntity();
        final Entity killer = entityDeathEvent.getDamageSource().getCausingEntity();

        this(
                entity,
                killer,
                entity.getLastDamageCause() != null ? entity.getLastDamageCause().getCause() : EntityDamageEvent.DamageCause.CUSTOM,
                UtilItemStack.getItemInMainHand(killer).orElse(null),
                new ArrayList<>(entityDeathEvent.getDrops()),
                entityDeathEvent.getDroppedExp(),
                SoundStash.of(entityDeathEvent.getDeathSound(), entityDeathEvent.getDeathSoundCategory(), entityDeathEvent.getDeathSoundVolume(), entityDeathEvent.getDeathSoundPitch())
        );
    }

    @Override
    public Component getCauseName() {
        return Component.text(UtilString.clean(this.cause.name()), NamedTextColor.YELLOW);
    }

    @Override
    public Component getReasonName() {
        return UtilItemStack.getDisplayName(this.itemStack, true).colorIfAbsent(NamedTextColor.GREEN);
    }

    @Override
    public boolean isReasonNameWithIndefiniteArticlePrefix() {
        return true;
    }
}