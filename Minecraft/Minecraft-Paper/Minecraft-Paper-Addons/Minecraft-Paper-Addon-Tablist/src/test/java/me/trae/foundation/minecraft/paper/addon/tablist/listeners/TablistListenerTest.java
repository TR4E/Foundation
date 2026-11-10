package me.trae.foundation.minecraft.paper.addon.tablist.listeners;

import me.trae.foundation.minecraft.paper.addon.tablist.Tablist;
import me.trae.foundation.minecraft.paper.addon.tablist.events.TablistUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.tablist.services.TablistService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

final class TablistListenerTest {

    @Test
    void offlineUpdateDoesNotTouchTablistState() {
        final Player player = mock(Player.class);
        final TablistService tablistService = mock(TablistService.class);

        new TablistListener(tablistService).onTablistUpdate(new TablistUpdateEvent(player));

        verifyNoInteractions(tablistService);
    }

    @Test
    void unchangedHeaderAndFooterAreSentAgainAfterRejoin() {
        final UUID playerId = UUID.randomUUID();
        final Player originalPlayer = mock(Player.class);
        when(originalPlayer.getUniqueId()).thenReturn(playerId);

        final Component headerComponent = Component.text("Header");
        final Component footerComponent = Component.text("Footer");
        final Tablist tablist = mock(Tablist.class);
        when(tablist.getHeader(originalPlayer)).thenReturn(headerComponent);
        when(tablist.getFooter(originalPlayer)).thenReturn(footerComponent);

        final TablistService tablistService = new TablistService(List.of(tablist));
        tablistService.create(originalPlayer, tablist);
        assertFalse(tablistService.getCachedHeaderFooterMap().isEmpty());

        final PlayerQuitEvent playerQuitEvent = mock(PlayerQuitEvent.class);
        when(playerQuitEvent.getPlayer()).thenReturn(originalPlayer);
        new TablistListener(tablistService).onPlayerQuit(playerQuitEvent);
        assertTrue(tablistService.getActiveTablistMap().isEmpty());
        assertTrue(tablistService.getCachedHeaderFooterMap().isEmpty());

        final Player rejoiningPlayer = mock(Player.class);
        when(rejoiningPlayer.getUniqueId()).thenReturn(playerId);
        when(tablist.getHeader(rejoiningPlayer)).thenReturn(headerComponent);
        when(tablist.getFooter(rejoiningPlayer)).thenReturn(footerComponent);
        tablistService.create(rejoiningPlayer, tablist);

        verify(rejoiningPlayer).sendPlayerListHeaderAndFooter(headerComponent, footerComponent);
        assertFalse(tablistService.getActiveTablistMap().isEmpty());
    }
}