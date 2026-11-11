package me.trae.foundation.minecraft.paper.addon.team.services;

import io.papermc.paper.scoreboard.numbers.FixedFormat;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import me.trae.foundation.minecraft.paper.addon.team.Team;
import me.trae.foundation.minecraft.paper.addon.team.belowname.BelowName;
import me.trae.foundation.minecraft.paper.addon.team.events.TeamUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.team.listeners.TeamListener;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilPacket;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.craftbukkit.scoreboard.CraftCriteria;
import org.bukkit.craftbukkit.scoreboard.CraftScoreboard;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerHideEntityEvent;
import org.bukkit.event.player.PlayerShowEntityEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

final class TeamServiceTest {

    private final Map<UUID, org.bukkit.scoreboard.Objective> objectiveMap = new HashMap<>();
    private final Map<UUID, List<Packet<?>>> packetListMap = new HashMap<>();

    private final List<Player> playerList = new ArrayList<>();

    private MockedStatic<UtilPacket> utilPacketMockedStatic;
    private MockedStatic<UtilServer> utilServerMockedStatic;

    @BeforeAll
    static void bootstrap() {
        System.setProperty("terminal.jline", "false");

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        try (final MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class)) {
            bukkitMockedStatic.when(() -> Bukkit.getScoreboardCriteria(anyString())).thenAnswer(
                    invocationOnMock -> CraftCriteria.getFromBukkit(invocationOnMock.getArgument(0))
            );

            assertNotNull(Criteria.HEALTH);
        }
    }

    @BeforeEach
    void setUp() {
        this.utilPacketMockedStatic = mockStatic(UtilPacket.class);
        this.utilPacketMockedStatic.when(() -> UtilPacket.send(any(), any())).thenAnswer(invocationOnMock -> {
            final Player player = invocationOnMock.getArgument(0);

            this.packetListMap.computeIfAbsent(player.getUniqueId(), _ -> new ArrayList<>()).add(invocationOnMock.getArgument(1));

            return null;
        });

        this.utilServerMockedStatic = mockStatic(UtilServer.class);
        this.utilServerMockedStatic.when(UtilServer::getOnlinePlayers).thenAnswer(_ -> List.copyOf(this.playerList));
        this.utilServerMockedStatic.when(() -> UtilServer.getOnlinePlayerById(any(UUID.class))).thenAnswer(invocationOnMock -> this.playerList.stream()
                .filter(player -> player.getUniqueId().equals(invocationOnMock.getArgument(0)))
                .findFirst());
    }

    @AfterEach
    void tearDown() {
        this.utilServerMockedStatic.close();
        this.utilPacketMockedStatic.close();
    }

    @Test
    void disabledBelowNameLeavesTheScoreboardUntouched() {
        final Player player = this.player("Target");
        final Player viewer = this.player("Viewer");
        final TeamService teamService = new TeamService(List.of());
        teamService.create(player, viewer, mock(Team.class));

        verifyNoInteractions(viewer.getScoreboard());
        assertTrue(this.packetListMap.get(viewer.getUniqueId()).stream().noneMatch(ClientboundSetDisplayObjectivePacket.class::isInstance));
    }

    @Test
    void healthUsesARegisteredTrackedHealthObjectiveWithoutReadingOrWritingHealth() {
        final Player firstPlayer = this.player("First");
        final Player secondPlayer = this.player("Second");
        final Player viewer = this.player("Viewer");
        final Team team = this.healthTeam();
        final TeamService teamService = new TeamService(List.of(team));
        teamService.create(firstPlayer, viewer, team);
        teamService.create(secondPlayer, viewer, team);

        verify(viewer.getScoreboard()).registerNewObjective(
                anyString(),
                eq(Criteria.HEALTH),
                eq(Component.empty()),
                eq(RenderType.INTEGER)
        );
        verify((ServerScoreboard) ((CraftScoreboard) viewer.getScoreboard()).getHandle()).startTrackingObjective(any(Objective.class));
        verify(this.objective(viewer)).numberFormat(NumberFormat.blank());
        verify(this.objective(viewer).getScore("First")).numberFormat(NumberFormat.noStyle());
        verify(this.objective(viewer).getScore("Second")).numberFormat(NumberFormat.noStyle());
        verify(this.objective(viewer).getScore("First"), never()).setScore(anyInt());
        verify(firstPlayer, never()).getHealth();
        verify(firstPlayer, never()).getAbsorptionAmount();
        assertEquals(DisplaySlot.BELOW_NAME, this.displayPacket(viewer).getSlot());
    }

    @Test
    void customTextUsesAFixedFormatInsteadOfAHealthValue() {
        final Player player = this.player("Target");
        final Player viewer = this.player("Viewer");
        final Team team = mock(Team.class);
        when(team.getBelowName(player, viewer)).thenReturn(BelowName.asComponent(Component.text("Level 25")));
        final TeamService teamService = new TeamService(List.of(team));
        teamService.create(player, viewer, team);

        final ArgumentCaptor<NumberFormat> numberFormatArgumentCaptor = ArgumentCaptor.forClass(NumberFormat.class);
        verify(this.objective(viewer).getScore("Target")).numberFormat(numberFormatArgumentCaptor.capture());

        assertEquals(Component.text("Level 25"), assertInstanceOf(FixedFormat.class, numberFormatArgumentCaptor.getValue()).asComponent());
    }

    @Test
    void healthCanSwitchToComponentOnRefresh() {
        final Player player = this.player("Target");
        final Player viewer = this.player("Viewer");
        final Team team = this.healthTeam();
        final TeamService teamService = new TeamService(List.of(team));
        teamService.create(player, viewer, team);

        verify(this.objective(viewer).getScore("Target")).numberFormat(NumberFormat.noStyle());

        when(team.getBelowName(player, viewer)).thenReturn(BelowName.asComponent(Component.text("Level 25")));
        teamService.create(player, viewer, team);

        final ArgumentCaptor<NumberFormat> numberFormatArgumentCaptor = ArgumentCaptor.forClass(NumberFormat.class);
        verify(this.objective(viewer).getScore("Target")).numberFormat(numberFormatArgumentCaptor.capture());

        assertInstanceOf(FixedFormat.class, numberFormatArgumentCaptor.getValue());
    }

    @Test
    void removingTheLastTargetUnregistersTheObjectiveAndRecreationRegistersAgain() {
        final Player firstPlayer = this.player("First");
        final Player secondPlayer = this.player("Second");
        final Player viewer = this.player("Viewer");
        final Team team = this.healthTeam();
        final TeamService teamService = new TeamService(List.of(team));
        teamService.create(firstPlayer, viewer, team);
        teamService.create(secondPlayer, viewer, team);
        teamService.remove(firstPlayer, viewer);

        verify(this.objective(viewer).getScore("First")).resetScore();
        verify(this.objective(viewer), never()).unregister();

        teamService.remove(secondPlayer, viewer);

        verify(this.objective(viewer)).unregister();

        teamService.create(firstPlayer, viewer, team);

        verify(viewer.getScoreboard(), times(2)).registerNewObjective(
                anyString(),
                eq(Criteria.HEALTH),
                eq(Component.empty()),
                eq(RenderType.INTEGER)
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shutdownUnregistersTheNativeObjectiveEvenWhenTheServerIsStopping(final boolean stopping) {
        final Player player = this.player("Target");
        final Player viewer = this.player("Viewer");
        final Team team = this.healthTeam();
        final TeamService teamService = new TeamService(List.of(team));
        teamService.create(player, viewer, team);

        try (final MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class)) {
            final Server server = mock(Server.class);
            when(server.isStopping()).thenReturn(stopping);
            bukkitMockedStatic.when(Bukkit::getServer).thenReturn(server);
            teamService.onComponentShutdown();
        }

        verify(this.objective(viewer)).unregister();
    }

    @Test
    void filteredUpdateRemovesIneligibleActiveTeamWithoutCreatingFallback() {
        final Player player = this.player("Target");
        final Player viewer = this.player("Viewer");
        final Team originalTeam = this.healthTeam();
        when(originalTeam.getIdentifier()).thenReturn("original");
        final Team fallbackTeam = this.healthTeam();
        when(fallbackTeam.getIdentifier()).thenReturn("fallback");
        when(fallbackTeam.getPriority()).thenReturn(10);
        final TeamService teamService = new TeamService(List.of(originalTeam, fallbackTeam));
        teamService.refresh(player, viewer);

        when(originalTeam.canDisplay()).thenReturn(false);
        new TeamListener(teamService).onTeamUpdate(new TeamUpdateEvent("original", player));

        assertTrue(teamService.getActiveTeamMap().isEmpty());
        assertTrue(teamService.getBelowNameStateMap().isEmpty());
        verify(this.objective(viewer)).unregister();
    }

    @Test
    void hidingAndShowingATargetRemovesAndRestoresItsViewerState() {
        final Player player = this.player("Target");
        final Player viewer = this.player("Viewer");
        final Team team = this.healthTeam();
        final TeamService teamService = new TeamService(List.of(team));
        final TeamListener teamListener = new TeamListener(teamService);
        teamService.refresh(player, viewer);

        final PlayerHideEntityEvent playerHideEntityEvent = mock(PlayerHideEntityEvent.class);
        when(playerHideEntityEvent.getPlayer()).thenReturn(viewer);
        when(playerHideEntityEvent.getEntity()).thenReturn(player);
        when(viewer.canSee(player)).thenReturn(false);
        teamListener.onPlayerHideEntity(playerHideEntityEvent);

        assertTrue(teamService.getActiveTeamMap().isEmpty());
        assertTrue(teamService.getBelowNameStateMap().isEmpty());
        verify(this.objective(viewer)).unregister();

        final PlayerShowEntityEvent playerShowEntityEvent = mock(PlayerShowEntityEvent.class);
        when(playerShowEntityEvent.getPlayer()).thenReturn(viewer);
        when(playerShowEntityEvent.getEntity()).thenReturn(player);
        teamListener.onPlayerShowEntity(playerShowEntityEvent);

        assertTrue(teamService.getActiveTeamMap().isEmpty());
        assertTrue(teamService.getBelowNameStateMap().isEmpty());

        when(viewer.canSee(player)).thenReturn(true);
        teamListener.onPlayerShowEntity(playerShowEntityEvent);

        assertEquals(1, teamService.getActiveTeamMap().size());
        assertTrue(teamService.getBelowNameStateMap().get(viewer.getUniqueId()).getBelowNameMap().containsKey(player.getUniqueId()));
    }

    @Test
    void joeCanSeeLevelWhileJohnSeesHealthForTheSamePlayer() {
        final Player player = this.player("Target");
        final Player joe = this.player("Joe");
        final Player john = this.player("John");
        final Team team = this.healthTeam();
        when(team.getBelowName(player, joe)).thenReturn(BelowName.asCriteria(Criteria.LEVEL));
        final TeamService teamService = new TeamService(List.of(team));
        teamService.create(player, joe, team);
        teamService.create(player, john, team);

        assertEquals(Criteria.LEVEL, this.objective(joe).getTrackedCriteria());
        assertEquals(Criteria.HEALTH, this.objective(john).getTrackedCriteria());
        assertNotSame(this.objective(joe), this.objective(john));
        verify(this.objective(joe).getScore("Target")).numberFormat(NumberFormat.noStyle());
        verify(this.objective(john).getScore("Target")).numberFormat(NumberFormat.noStyle());
    }

    @Test
    void changingAViewersCriterionRestoresOtherPlayersAndCustomText() {
        final Player firstPlayer = this.player("First");
        final Player secondPlayer = this.player("Second");
        final Player textPlayer = this.player("Text");
        final Player viewer = this.player("Viewer");
        final Team team = this.healthTeam();
        when(team.getBelowName(textPlayer, viewer)).thenReturn(BelowName.asComponent(Component.text("Member")));
        final TeamService teamService = new TeamService(List.of(team));
        teamService.create(firstPlayer, viewer, team);
        teamService.create(secondPlayer, viewer, team);
        teamService.create(textPlayer, viewer, team);

        final org.bukkit.scoreboard.Objective previousObjective = this.objective(viewer);
        when(team.getBelowName(firstPlayer, viewer)).thenReturn(BelowName.asCriteria(Criteria.LEVEL));
        when(team.getBelowName(secondPlayer, viewer)).thenReturn(BelowName.asCriteria(Criteria.LEVEL));
        teamService.create(firstPlayer, viewer, team);

        verify(previousObjective).unregister();
        assertNotSame(previousObjective, this.objective(viewer));
        assertEquals(Criteria.LEVEL, this.objective(viewer).getTrackedCriteria());
        verify(this.objective(viewer).getScore("First")).numberFormat(NumberFormat.noStyle());
        verify(this.objective(viewer).getScore("Second")).numberFormat(NumberFormat.noStyle());

        final ArgumentCaptor<NumberFormat> numberFormatArgumentCaptor = ArgumentCaptor.forClass(NumberFormat.class);
        verify(this.objective(viewer).getScore("Text")).numberFormat(numberFormatArgumentCaptor.capture());

        assertEquals(Component.text("Member"), assertInstanceOf(FixedFormat.class, numberFormatArgumentCaptor.getValue()).asComponent());
    }

    @Test
    void disablingTheLastNumericEntryKeepsTextAndSwitchesToDummy() {
        final Player player = this.player("Target");
        final Player textPlayer = this.player("Text");
        final Player viewer = this.player("Viewer");
        final Team team = this.healthTeam();
        when(team.getBelowName(textPlayer, viewer)).thenReturn(BelowName.asComponent(Component.text("Member")));
        final TeamService teamService = new TeamService(List.of(team));
        teamService.create(player, viewer, team);
        teamService.create(textPlayer, viewer, team);

        final org.bukkit.scoreboard.Objective previousObjective = this.objective(viewer);
        when(team.getBelowName(player, viewer)).thenReturn(null);
        teamService.create(player, viewer, team);

        verify(previousObjective.getScore("Target")).resetScore();
        verify(previousObjective).unregister();
        assertEquals(Criteria.DUMMY, this.objective(viewer).getTrackedCriteria());
        verify(this.objective(viewer).getScore("Text")).numberFormat(any(FixedFormat.class));
    }

    @Test
    void incompatibleCriteriaForOneViewerDoNotSilentlyReplaceTheExistingCriterion() {
        final Player firstPlayer = this.player("First");
        final Player secondPlayer = this.player("Second");
        final Player viewer = this.player("Viewer");
        final Team team = this.healthTeam();
        when(team.getBelowName(secondPlayer, viewer)).thenReturn(BelowName.asCriteria(Criteria.LEVEL));
        final TeamService teamService = new TeamService(List.of(team));
        teamService.create(firstPlayer, viewer, team);

        assertThrows(IllegalStateException.class, () -> teamService.create(secondPlayer, viewer, team));
        assertEquals(Criteria.HEALTH, this.objective(viewer).getTrackedCriteria());
        verify(this.objective(viewer), never()).unregister();
    }

    @Test
    void healthPresentationOnlyAppliesToHealthObjectives() {
        final Player player = this.player("Target");
        final Player healthViewer = this.player("HealthViewer");
        final Player levelViewer = this.player("LevelViewer");
        final Player textViewer = this.player("TextViewer");
        final Team team = this.healthTeam();
        when(team.getBelowName(player, levelViewer)).thenReturn(BelowName.asCriteria(Criteria.LEVEL));
        when(team.getBelowName(player, textViewer)).thenReturn(BelowName.asComponent(Component.text("Member")));

        final Component component = Component.text("HP", NamedTextColor.GOLD);
        when(team.getBelowName(player, healthViewer)).thenReturn(BelowName.asHealth(component, RenderType.HEARTS));
        final TeamService teamService = new TeamService(List.of(team));
        teamService.create(player, healthViewer, team);
        teamService.create(player, levelViewer, team);
        teamService.create(player, textViewer, team);

        verify(healthViewer.getScoreboard()).registerNewObjective(
                anyString(),
                eq(Criteria.HEALTH),
                eq(component),
                eq(RenderType.HEARTS)
        );
        verify(levelViewer.getScoreboard()).registerNewObjective(
                anyString(),
                eq(Criteria.LEVEL),
                eq(Component.empty()),
                eq(RenderType.INTEGER)
        );
        verify(textViewer.getScoreboard()).registerNewObjective(
                anyString(),
                eq(Criteria.DUMMY),
                eq(Component.empty()),
                eq(RenderType.INTEGER)
        );
        verify(this.objective(healthViewer).getScore("Target"), never()).setScore(anyInt());
    }

    @Test
    void explicitHealthPresentationKeepsNativeTracking() {
        final Player player = this.player("Target");
        final Player viewer = this.player("Viewer");
        final Team team = this.healthTeam();
        final Component component = Component.text("HP", NamedTextColor.GOLD);
        when(team.getBelowName(player, viewer)).thenReturn(BelowName.asHealth(component, RenderType.HEARTS));
        final TeamService teamService = new TeamService(List.of(team));
        teamService.create(player, viewer, team);

        verify(viewer.getScoreboard()).registerNewObjective(
                anyString(),
                eq(Criteria.HEALTH),
                eq(component),
                eq(RenderType.HEARTS)
        );
        verify((ServerScoreboard) ((CraftScoreboard) viewer.getScoreboard()).getHandle()).startTrackingObjective(any(Objective.class));
        verify(this.objective(viewer).getScore("Target"), never()).setScore(anyInt());
    }

    @Test
    void changingOnlyHealthPresentationKeepsTheObjectiveAndItsScores() {
        final Player player = this.player("Target");
        final Player viewer = this.player("Viewer");
        final Team team = this.healthTeam();
        final TeamService teamService = new TeamService(List.of(team));
        teamService.create(player, viewer, team);

        final org.bukkit.scoreboard.Objective previousObjective = this.objective(viewer);
        final Score score = previousObjective.getScore("Target");
        when(score.getScore()).thenReturn(17);

        final Component component = Component.text("HP", NamedTextColor.GOLD);
        when(team.getBelowName(player, viewer)).thenReturn(BelowName.asHealth(component, RenderType.HEARTS));
        teamService.create(player, viewer, team);
        teamService.create(player, viewer, team);

        assertSame(previousObjective, this.objective(viewer));
        assertEquals(17, this.objective(viewer).getScore("Target").getScore());
        verify(previousObjective).displayName(component);
        verify(previousObjective).setRenderType(RenderType.HEARTS);
        verify(previousObjective, never()).unregister();
        verify(score, never()).setScore(anyInt());
    }

    private Player player(final String name) {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        when(player.canSee(any(Player.class))).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        this.playerList.add(player);

        final CraftScoreboard craftScoreboard = mock(CraftScoreboard.class);
        final ServerScoreboard serverScoreboard = mock(ServerScoreboard.class);
        when(player.getScoreboard()).thenReturn(craftScoreboard);
        when(craftScoreboard.getHandle()).thenReturn(serverScoreboard);
        when(serverScoreboard.getObjective(anyString())).thenReturn(mock(Objective.class));

        when(craftScoreboard.registerNewObjective(anyString(), any(Criteria.class), any(Component.class), any(RenderType.class)))
                .thenAnswer(invocationOnMock -> {
                    final org.bukkit.scoreboard.Objective objective = mock(org.bukkit.scoreboard.Objective.class);
                    when(objective.getScoreboard()).thenReturn(craftScoreboard);
                    when(objective.getTrackedCriteria()).thenReturn(invocationOnMock.getArgument(1));
                    when(objective.displayName()).thenReturn(invocationOnMock.getArgument(2));
                    when(objective.getRenderType()).thenReturn(invocationOnMock.getArgument(3));
                    doAnswer(displayNameInvocationOnMock -> {
                        when(objective.displayName()).thenReturn(displayNameInvocationOnMock.getArgument(0));

                        return null;
                    }).when(objective).displayName(any(Component.class));
                    doAnswer(renderTypeInvocationOnMock -> {
                        when(objective.getRenderType()).thenReturn(renderTypeInvocationOnMock.getArgument(0));

                        return null;
                    }).when(objective).setRenderType(any(RenderType.class));
                    this.objectiveMap.put(player.getUniqueId(), objective);

                    final Map<String, Score> scoreMap = new HashMap<>();
                    when(objective.getScore(anyString())).thenAnswer(scoreInvocationOnMock -> scoreMap.computeIfAbsent(
                            scoreInvocationOnMock.getArgument(0),
                            _ -> mock(Score.class)
                    ));

                    return objective;
                });

        return player;
    }

    private Team healthTeam() {
        final Team team = mock(Team.class);
        when(team.getBelowName(any(), any())).thenReturn(BelowName.asHealth());
        when(team.canDisplay()).thenReturn(true);
        when(team.canDisplay(any(), any())).thenReturn(true);

        return team;
    }

    private org.bukkit.scoreboard.Objective objective(final Player viewer) {
        return this.objectiveMap.get(viewer.getUniqueId());
    }

    private ClientboundSetDisplayObjectivePacket displayPacket(final Player viewer) {
        return this.packetListMap.get(viewer.getUniqueId()).stream()
                .filter(ClientboundSetDisplayObjectivePacket.class::isInstance)
                .map(ClientboundSetDisplayObjectivePacket.class::cast)
                .findFirst()
                .orElseThrow();
    }
}