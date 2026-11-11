package me.trae.foundation.minecraft.paper.addon.team.listeners;

import me.trae.foundation.minecraft.paper.addon.team.Team;
import me.trae.foundation.minecraft.paper.addon.team.events.TeamUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.team.services.TeamService;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

final class TeamListenerTest {

    @BeforeAll
    static void disableInteractiveTerminal() {
        System.setProperty("terminal.jline", "false");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scopedUpdateDoesNothingWithoutAMatchingActiveTeam(final boolean active) {
        final Player player = mock(Player.class);
        final Player viewer = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        final Team activeTeam = mock(Team.class);
        when(activeTeam.getIdentifier()).thenReturn("active");
        final TeamService teamService = mock(TeamService.class);
        when(teamService.getActiveTeam(player, viewer)).thenReturn(active ? Optional.of(activeTeam) : Optional.empty());

        try (final MockedStatic<UtilServer> utilServerMockedStatic = mockStatic(UtilServer.class)) {
            utilServerMockedStatic.when(UtilServer::getOnlinePlayers).thenReturn(List.of(viewer));
            new TeamListener(teamService).onTeamUpdate(new TeamUpdateEvent("other", player));
        }

        verify(teamService).getActiveTeam(player, viewer);
        verifyNoMoreInteractions(teamService);
    }

    @Test
    void unscopedUpdateCanCreateTheEligibleTeamWithoutAnActiveTeam() {
        final Player player = mock(Player.class);
        final Player viewer = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        final Team eligibleTeam = mock(Team.class);
        final TeamService teamService = mock(TeamService.class);
        when(teamService.getEligibleTeam(player, viewer)).thenReturn(Optional.of(eligibleTeam));

        try (final MockedStatic<UtilServer> utilServerMockedStatic = mockStatic(UtilServer.class)) {
            utilServerMockedStatic.when(UtilServer::getOnlinePlayers).thenReturn(List.of(viewer));
            new TeamListener(teamService).onTeamUpdate(new TeamUpdateEvent(player));
        }

        verify(teamService).getEligibleTeam(player, viewer);
        verify(teamService).create(player, viewer, eligibleTeam);
        verifyNoMoreInteractions(teamService);
    }

    @Test
    void offlineUpdateDoesNotTouchTeamState() {
        final Player player = mock(Player.class);
        final TeamService teamService = mock(TeamService.class);

        new TeamListener(teamService).onTeamUpdate(new TeamUpdateEvent(player));

        verifyNoInteractions(teamService);
    }
}