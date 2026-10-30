package me.trae.foundation.minecraft.paper.addon.team;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.team.events.TeamUpdateEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import net.kyori.adventure.text.Component;
import net.minecraft.ChatFormatting;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public abstract class Team {

    private final String identifier;
    private final int priority;

    public final void updatePlayer(final Player player) {
        UtilEvent.dispatchSynchronously(new TeamUpdateEvent(this.identifier, player));
    }

    public final void updateAllPlayers() {
        UtilServer.getOnlinePlayers().forEach(this::updatePlayer);
    }

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