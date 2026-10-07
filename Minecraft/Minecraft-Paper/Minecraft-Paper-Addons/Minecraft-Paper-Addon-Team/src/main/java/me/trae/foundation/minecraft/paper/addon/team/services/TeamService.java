package me.trae.foundation.minecraft.paper.addon.team.services;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.team.Team;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilNms;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
@AddonSingleton
public final class TeamService {

    private final Set<String> activeTeamSet = ConcurrentHashMap.newKeySet();

    private final List<Team> teamList;

    public void create(final Player player, final Player viewer, final Team team) {
        final String teamName = this.getTeamName(player, viewer);

        final PlayerTeam playerTeam = this.buildPlayerTeam(teamName, player, viewer, team);

        UtilNms.sendPacket(viewer, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true));
        UtilNms.sendPacket(viewer, ClientboundSetPlayerTeamPacket.createPlayerPacket(playerTeam, player.getName(), ClientboundSetPlayerTeamPacket.Action.ADD));

        this.activeTeamSet.add(teamName);
    }

    public void remove(final Player player, final Player viewer) {
        final String teamName = this.getTeamName(player, viewer);

        if (!this.activeTeamSet.remove(teamName)) {
            return;
        }

        final PlayerTeam playerTeam = new PlayerTeam(new Scoreboard(), teamName);

        UtilNms.sendPacket(viewer, ClientboundSetPlayerTeamPacket.createPlayerPacket(playerTeam, player.getName(), ClientboundSetPlayerTeamPacket.Action.REMOVE));
        UtilNms.sendPacket(viewer, ClientboundSetPlayerTeamPacket.createRemovePacket(playerTeam));
    }

    public void refresh(final Player player, final Player viewer) {
        this.getEligibleTeam(player, viewer).ifPresentOrElse(team -> this.create(player, viewer, team), () -> this.remove(player, viewer));
    }

    public Optional<Team> getEligibleTeam(final Player player, final Player viewer) {
        return this.teamList.stream().filter(team -> team.canDisplay() && team.canDisplay(player, viewer)).findFirst();
    }

    private PlayerTeam buildPlayerTeam(final String teamName, final Player player, final Player viewer, final Team team) {
        final PlayerTeam playerTeam = new PlayerTeam(new Scoreboard(), teamName);

        // Display Name
        Optional.ofNullable(team.getDisplayName(player, viewer)).ifPresent(component -> playerTeam.setDisplayName(UtilNms.toNms(component)));

        // Prefix & Suffix
        Optional.ofNullable(team.getPrefix(player, viewer)).ifPresent(component -> playerTeam.setPlayerPrefix(UtilNms.toNms(component)));
        Optional.ofNullable(team.getSuffix(player, viewer)).ifPresent(component -> playerTeam.setPlayerSuffix(UtilNms.toNms(component)));

        // Allow Friendly Fire & See Friendly Invisibles
        Optional.ofNullable(team.allowFriendlyFire(player, viewer)).ifPresent(playerTeam::setAllowFriendlyFire);
        Optional.ofNullable(team.seeFriendlyInvisibles(player, viewer)).ifPresent(playerTeam::setSeeFriendlyInvisibles);

        // Name Tag Visibility & Death Message Visibility
        Optional.ofNullable(team.getNameTagVisibility(player, viewer)).ifPresent(playerTeam::setNameTagVisibility);
        Optional.ofNullable(team.getDeathMessageVisibility(player, viewer)).ifPresent(playerTeam::setDeathMessageVisibility);

        // Collision Rule
        Optional.ofNullable(team.getCollisionRule(player, viewer)).ifPresent(playerTeam::setCollisionRule);

        // Color
        Optional.ofNullable(team.getColor(player, viewer)).ifPresent(playerTeam::setColor);

        return playerTeam;
    }

    private String getTeamName(final Player player, final Player viewer) {
        return "%s:%s".formatted(player.getUniqueId(), viewer.getUniqueId());
    }
}