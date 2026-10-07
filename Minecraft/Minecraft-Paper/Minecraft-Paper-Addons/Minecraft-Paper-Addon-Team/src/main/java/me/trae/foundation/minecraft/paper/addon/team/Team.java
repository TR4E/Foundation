package me.trae.foundation.minecraft.paper.addon.team;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.minecraft.ChatFormatting;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public abstract class Team {

    private final String identifier;
    private final int priority;

    public boolean canDisplay() {
        return true;
    }

    public boolean canDisplay(final Player player, final Player viewer) {
        return true;
    }

    public Component getDisplayName(final Player player, final Player viewer) {
        return null;
    }

    public Component getPrefix(final Player player, final Player viewer) {
        return null;
    }

    public Component getSuffix(final Player player, final Player viewer) {
        return null;
    }

    public Boolean allowFriendlyFire(final Player player, final Player viewer) {
        return null;
    }

    public Boolean seeFriendlyInvisibles(final Player player, final Player viewer) {
        return null;
    }

    public net.minecraft.world.scores.Team.Visibility getNameTagVisibility(final Player player, final Player viewer) {
        return null;
    }

    public net.minecraft.world.scores.Team.Visibility getDeathMessageVisibility(final Player player, final Player viewer) {
        return null;
    }

    public net.minecraft.world.scores.Team.CollisionRule getCollisionRule(final Player player, final Player viewer) {
        return null;
    }

    public ChatFormatting getColor(final Player player, final Player viewer) {
        return null;
    }
}