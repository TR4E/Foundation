package me.trae.foundation.minecraft.paper.addon.death.events;

import me.trae.foundation.minecraft.common.adventure.UtilAdventure;
import me.trae.foundation.minecraft.paper.addon.death.reason.Reason;
import me.trae.foundation.minecraft.paper.addon.death.settings.DeathAddonSettings;
import me.trae.foundation.minecraft.paper.plugin.framework.stash.SoundStash;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilColor;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import me.trae.foundation.utilities.UtilString;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface DeathEvent {

    DeathAddonSettings getSettings();

    LivingEntity getEntity();

    Entity getKiller();

    DamageCause getCause();

    ItemStack getItemStack();

    List<ItemStack> getDrops();

    Reason getReason();

    int getDropExp();

    void setDropExp(final int dropExp);

    SoundStash getSoundStash();

    void setSoundStash(final SoundStash soundStash);

    default Component getCauseComponent() {
        return Component.text(UtilString.clean(this.getCause().name()), UtilColor.toTextColor(this.getSettings().getDefaultCauseNameColor()));
    }

    default Component getReasonComponent() {
        final Reason reason = this.getReason();

        if (reason == null || reason.getName() == null || reason.hasExpired()) {
            final Component displayName = UtilItemStack.getDisplayName(this.getItemStack(), true);

            return displayName == null ? null : displayName.colorIfAbsent(UtilColor.toTextColor(this.getSettings().getDefaultReasonNameColor()));
        }

        return UtilAdventure.joinWithEmpty(reason.getPrefix(), reason.getName().colorIfAbsent(UtilColor.toTextColor(this.getSettings().getDefaultReasonNameColor())), reason.getSuffix());
    }

    default boolean isReasonNameWithIndefiniteArticlePrefix() {
        final Reason reason = this.getReason();

        return reason == null || (reason.getPrefix() == null && reason.getName() != null);
    }
}