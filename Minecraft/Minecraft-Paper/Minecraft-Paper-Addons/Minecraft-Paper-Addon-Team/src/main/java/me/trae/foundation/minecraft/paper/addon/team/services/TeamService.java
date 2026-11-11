package me.trae.foundation.minecraft.paper.addon.team.services;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.minecraft.paper.addon.team.Team;
import me.trae.foundation.minecraft.paper.addon.team.belowname.BelowName;
import me.trae.foundation.minecraft.paper.addon.team.belowname.BelowNameState;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilNms;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilPacket;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.scoreboard.CraftScoreboard;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
@AddonSingleton
public final class TeamService implements Lifecycle {

    private final ConcurrentHashMap<String, Team> activeTeamMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BelowNameState> belowNameStateMap = new ConcurrentHashMap<>();

    private final List<Team> teamList;

    @Override
    public void onComponentShutdown() {
        if (Bukkit.getServer().isStopping()) {
            this.belowNameStateMap.values().forEach(belowNameState -> belowNameState.getObjective().unregister());
            return;
        }

        final List<Player> playerList = UtilServer.getOnlinePlayers();

        for (final Player player : playerList) {
            for (final Player viewer : playerList) {
                this.remove(player, viewer);
            }
        }

        this.activeTeamMap.clear();
        this.belowNameStateMap.clear();
    }

    public void create(final Player player, final Player viewer, final Team team) {
        final String teamName = this.getTeamName(player, viewer);

        final PlayerTeam playerTeam = this.buildPlayerTeam(teamName, player, viewer, team);

        UtilPacket.send(viewer, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true));
        UtilPacket.send(viewer, ClientboundSetPlayerTeamPacket.createPlayerPacket(playerTeam, player.getName(), ClientboundSetPlayerTeamPacket.Action.ADD));

        this.activeTeamMap.put(teamName, team);

        this.updateBelowName(player, viewer, team);
    }

    public void remove(final Player player, final Player viewer) {
        final String teamName = this.getTeamName(player, viewer);

        if (this.activeTeamMap.remove(teamName) == null) {
            return;
        }

        final PlayerTeam playerTeam = new PlayerTeam(new Scoreboard(), teamName);

        UtilPacket.send(viewer, ClientboundSetPlayerTeamPacket.createPlayerPacket(playerTeam, player.getName(), ClientboundSetPlayerTeamPacket.Action.REMOVE));
        UtilPacket.send(viewer, ClientboundSetPlayerTeamPacket.createRemovePacket(playerTeam));

        this.removeBelowName(player, viewer);
    }

    public void refresh(final Player player, final Player viewer) {
        this.getEligibleTeam(player, viewer).ifPresentOrElse(team -> this.create(player, viewer, team), () -> this.remove(player, viewer));
    }

    public Optional<Team> getActiveTeam(final Player player, final Player viewer) {
        return Optional.ofNullable(this.activeTeamMap.get(this.getTeamName(player, viewer)));
    }

    public void cleanup(final Player player) {
        for (final Player viewer : UtilServer.getOnlinePlayers()) {
            this.remove(player, viewer);

            if (!player.equals(viewer)) {
                this.remove(viewer, player);
            }
        }

        this.clearBelowNameForViewer(player);
    }

    public Optional<Team> getEligibleTeam(final Player player, final Player viewer) {
        if (!viewer.canSee(player)) {
            return Optional.empty();
        }

        return this.teamList.stream()
                .filter(team -> team.canDisplay() && team.canDisplay(player, viewer))
                .min(Comparator.comparingInt(Team::getPriority));
    }

    public void clearBelowNameForViewer(final Player viewer) {
        final BelowNameState belowNameState = this.belowNameStateMap.remove(viewer.getUniqueId());
        if (belowNameState == null) {
            return;
        }

        belowNameState.getObjective().unregister();
    }

    private void updateBelowName(final Player player, final Player viewer, final Team team) {
        final BelowName belowName = team.getBelowName(player, viewer);

        if (belowName == null) {
            this.removeBelowName(player, viewer);
            return;
        }

        final BelowNameState belowNameState = this.belowNameStateMap.get(viewer.getUniqueId());

        if (belowNameState != null) {
            final BelowName previousBelowName = belowNameState.getBelowNameMap().get(player.getUniqueId());

            if (belowNameState.getObjective().getScoreboard() != viewer.getScoreboard() || (belowName.getCriteria() != null && !this.matchesObjective(belowNameState.getObjective(), this.getObjectiveBelowName(belowName))) || (previousBelowName != null && !this.matchesCriteria(previousBelowName.getCriteria(), belowName.getCriteria()))) {
                final Map<UUID, BelowName> belowNameMap = this.getCurrentBelowNameMap(viewer, belowNameState, player.getUniqueId());

                belowNameMap.put(player.getUniqueId(), belowName);

                this.rebuildBelowName(viewer, belowNameMap);

                return;
            }
        }

        final BelowNameState activeBelowNameState = this.belowNameStateMap.computeIfAbsent(viewer.getUniqueId(), _ -> this.createBelowNameState(viewer, this.getObjectiveBelowName(belowName)));

        this.applyBelowName(player, activeBelowNameState, belowName);
    }

    private BelowName getObjectiveBelowName(final BelowName belowName) {
        return belowName.getCriteria() == null ? BelowName.asCriteria(Criteria.DUMMY) : belowName;
    }

    private Component getObjectiveComponent(final BelowName belowName) {
        return belowName.getComponent() == null ? Component.empty() : belowName.getComponent();
    }

    private void applyBelowName(final Player player, final BelowNameState belowNameState, final BelowName belowName) {
        belowNameState.getBelowNameMap().put(player.getUniqueId(), belowName);

        belowNameState.getObjective().getScore(player.getName()).numberFormat(belowName.getCriteria() == null ? NumberFormat.fixed(belowName.getComponent()) : NumberFormat.noStyle());
    }

    private Map<UUID, BelowName> getCurrentBelowNameMap(final Player viewer, final BelowNameState belowNameState, final UUID excludedPlayerId) {
        return belowNameState.getBelowNameMap().keySet().stream()
                .filter(playerId -> !playerId.equals(excludedPlayerId))
                .map(UtilServer::getOnlinePlayerById)
                .flatMap(Optional::stream)
                .flatMap(player -> this.getEligibleTeam(player, viewer)
                        .map(team -> team.getBelowName(player, viewer))
                        .map(belowName -> Map.entry(player.getUniqueId(), belowName))
                        .stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void rebuildBelowName(final Player viewer, final Map<UUID, BelowName> belowNameMap) {
        if (belowNameMap.isEmpty()) {
            this.clearBelowNameForViewer(viewer);
            return;
        }

        final BelowName objectiveBelowName = belowNameMap.values().stream()
                .filter(belowName -> belowName.getCriteria() != null)
                .map(this::getObjectiveBelowName)
                .findFirst()
                .orElseGet(() -> BelowName.asCriteria(Criteria.DUMMY));

        if (belowNameMap.values().stream().filter(belowName -> belowName.getCriteria() != null).map(this::getObjectiveBelowName).anyMatch(belowName -> !this.matchesBelowName(objectiveBelowName, belowName))) {
            throw new IllegalStateException("Viewer %s requested conflicting below-name criteria, display names or render types.".formatted(viewer.getName()));
        }

        final BelowNameState belowNameState = this.belowNameStateMap.get(viewer.getUniqueId());

        if (belowNameState != null && (belowNameState.getObjective().getScoreboard() != viewer.getScoreboard() || !this.matchesCriteria(belowNameState.getObjective().getTrackedCriteria(), objectiveBelowName.getCriteria()))) {
            this.clearBelowNameForViewer(viewer);
        }

        final BelowNameState activeBelowNameState = this.belowNameStateMap.computeIfAbsent(viewer.getUniqueId(), _ -> this.createBelowNameState(viewer, objectiveBelowName));

        this.updateBelowNameObjective(activeBelowNameState.getObjective(), objectiveBelowName);

        for (final UUID playerId : activeBelowNameState.getBelowNameMap().keySet()) {
            if (belowNameMap.containsKey(playerId)) {
                continue;
            }

            final Player player = UtilServer.getOnlinePlayerById(playerId).orElse(null);
            if (player == null) {
                continue;
            }

            activeBelowNameState.getObjective().getScore(player.getName()).resetScore();
        }

        activeBelowNameState.getBelowNameMap().clear();

        for (final Map.Entry<UUID, BelowName> entry : belowNameMap.entrySet()) {
            final Player player = UtilServer.getOnlinePlayerById(entry.getKey()).orElse(null);
            if (player == null) {
                continue;
            }

            this.applyBelowName(player, activeBelowNameState, entry.getValue());
        }
    }

    private void updateBelowNameObjective(final org.bukkit.scoreboard.Objective objective, final BelowName belowName) {
        final Component component = this.getObjectiveComponent(belowName);

        if (!Objects.equals(objective.displayName(), component)) {
            objective.displayName(component);
        }

        if (objective.getRenderType() != belowName.getRenderType()) {
            objective.setRenderType(belowName.getRenderType());
        }
    }

    private boolean matchesObjective(final org.bukkit.scoreboard.Objective objective, final BelowName belowName) {
        return this.matchesCriteria(objective.getTrackedCriteria(), belowName.getCriteria())
                && Objects.equals(objective.displayName(), this.getObjectiveComponent(belowName))
                && objective.getRenderType() == belowName.getRenderType();
    }

    private boolean matchesBelowName(final BelowName firstBelowName, final BelowName secondBelowName) {
        return this.matchesCriteria(firstBelowName.getCriteria(), secondBelowName.getCriteria())
                && Objects.equals(this.getObjectiveComponent(firstBelowName), this.getObjectiveComponent(secondBelowName))
                && firstBelowName.getRenderType() == secondBelowName.getRenderType();
    }

    private boolean matchesCriteria(final Criteria firstCriteria, final Criteria secondCriteria) {
        return firstCriteria == secondCriteria || firstCriteria != null && secondCriteria != null && firstCriteria.getName().equals(secondCriteria.getName());
    }

    private void removeBelowName(final Player player, final Player viewer) {
        final BelowNameState belowNameState = this.belowNameStateMap.get(viewer.getUniqueId());
        if (belowNameState == null) {
            return;
        }

        final BelowName belowName = belowNameState.getBelowNameMap().remove(player.getUniqueId());
        if (belowName == null) {
            return;
        }

        belowNameState.getObjective().getScore(player.getName()).resetScore();

        if (belowNameState.getBelowNameMap().isEmpty()) {
            this.clearBelowNameForViewer(viewer);
        } else if (belowName.getCriteria() != null && belowNameState.getBelowNameMap().values().stream().noneMatch(entryBelowName -> entryBelowName.getCriteria() != null)) {
            this.rebuildBelowName(viewer, this.getCurrentBelowNameMap(viewer, belowNameState, player.getUniqueId()));
        }
    }

    private BelowNameState createBelowNameState(final Player viewer, final BelowName belowName) {
        final org.bukkit.scoreboard.Scoreboard scoreboard = viewer.getScoreboard();

        final String identifier = "%s_%s".formatted("below_name", viewer.getUniqueId());

        final org.bukkit.scoreboard.Objective existingObjective = scoreboard.getObjective(identifier);

        if (existingObjective != null) {
            existingObjective.unregister();
        }

        final org.bukkit.scoreboard.Objective objective = scoreboard.registerNewObjective(
                identifier,
                belowName.getCriteria(),
                this.getObjectiveComponent(belowName),
                belowName.getRenderType()
        );

        objective.numberFormat(NumberFormat.blank());

        final ServerScoreboard serverScoreboard = (ServerScoreboard) ((CraftScoreboard) scoreboard).getHandle();

        final Objective belowNameObjective = serverScoreboard.getObjective(identifier);

        serverScoreboard.startTrackingObjective(belowNameObjective);

        UtilPacket.send(viewer, new ClientboundSetDisplayObjectivePacket(DisplaySlot.BELOW_NAME, belowNameObjective));

        return new BelowNameState(objective);
    }

    private PlayerTeam buildPlayerTeam(final String teamName, final Player player, final Player viewer, final Team team) {
        final PlayerTeam playerTeam = new PlayerTeam(new Scoreboard(), teamName);

        // Display Name
        Optional.ofNullable(team.getDisplayName(player, viewer)).ifPresent(component -> playerTeam.setDisplayName(UtilNms.toComponent(component)));

        // Prefix & Suffix
        Optional.ofNullable(team.getPrefix(player, viewer)).ifPresent(component -> playerTeam.setPlayerPrefix(UtilNms.toComponent(component)));
        Optional.ofNullable(team.getSuffix(player, viewer)).ifPresent(component -> playerTeam.setPlayerSuffix(UtilNms.toComponent(component)));

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