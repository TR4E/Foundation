package me.trae.foundation.minecraft.paper.addon.death.events;

import me.trae.foundation.minecraft.paper.plugin.framework.stash.SoundStash;
import me.trae.foundation.utilities.UtilString;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface DeathEvent {

    LivingEntity getEntity();

    Entity getKiller();

    DamageCause getCause();

    ItemStack getItemStack();

    List<ItemStack> getDrops();

    int getDropExp();

    void setDropExp(final int dropExp);

    SoundStash getSoundStash();

    void setSoundStash(final SoundStash soundStash);

    default Component getCauseName() {
        return Component.text(UtilString.clean(this.getCause().name()), NamedTextColor.YELLOW);
    }

    Component getReasonName();

    boolean isReasonNameWithIndefiniteArticlePrefix();
}