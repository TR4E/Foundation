package me.trae.foundation.minecraft.paper.addon.team.listeners;

import me.trae.foundation.minecraft.paper.addon.team.events.TeamUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.team.services.TeamService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

final class TeamListenerTest {

    @Test
    void offlineUpdateDoesNotTouchTeamState() {
        final Player player = mock(Player.class);
        final TeamService teamService = mock(TeamService.class);

        new TeamListener(teamService).onTeamUpdate(new TeamUpdateEvent(player));

        verifyNoInteractions(teamService);
    }
}