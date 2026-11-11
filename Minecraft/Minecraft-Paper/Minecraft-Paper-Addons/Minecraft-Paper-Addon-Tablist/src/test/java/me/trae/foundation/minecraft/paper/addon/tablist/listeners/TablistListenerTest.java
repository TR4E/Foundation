package me.trae.foundation.minecraft.paper.addon.tablist.listeners;

import me.trae.foundation.minecraft.paper.addon.tablist.Tablist;
import me.trae.foundation.minecraft.paper.addon.tablist.events.TablistUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.tablist.services.TablistService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

final class TablistListenerTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void onlyUnscopedUpdatesCanReplaceTheActiveDefinitionWithAFallback(final boolean scoped) {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);

        final Tablist originalTablist = mock(Tablist.class);
        final Tablist fallbackTablist = mock(Tablist.class);
        when(originalTablist.getIdentifier()).thenReturn("original");
        when(fallbackTablist.getIdentifier()).thenReturn("fallback");
        final ConcurrentHashMap<UUID, Tablist> activeTablistMap = new ConcurrentHashMap<>();
        activeTablistMap.put(player.getUniqueId(), originalTablist);
        final TablistService tablistService = mock(TablistService.class);
        when(tablistService.getActiveTablistMap()).thenReturn(activeTablistMap);
        when(tablistService.getEligibleTablist(player)).thenReturn(Optional.of(fallbackTablist));

        new TablistListener(tablistService).onTablistUpdate(new TablistUpdateEvent(scoped ? "original" : null, player));

        if (scoped) {
            verify(tablistService).remove(player);
            verify(tablistService, never()).create(player, fallbackTablist);
        } else {
            verify(tablistService).create(player, fallbackTablist);
            verify(tablistService, never()).remove(player);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scopedCancellationDoesNothingWithoutAMatchingActiveTablist(final boolean active) {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        final Tablist activeTablist = mock(Tablist.class);
        when(activeTablist.getIdentifier()).thenReturn("active");
        final ConcurrentHashMap<UUID, Tablist> activeTablistMap = new ConcurrentHashMap<>();

        if (active) {
            activeTablistMap.put(player.getUniqueId(), activeTablist);
        }

        final TablistService tablistService = mock(TablistService.class);
        when(tablistService.getActiveTablistMap()).thenReturn(activeTablistMap);
        final TablistUpdateEvent tablistUpdateEvent = new TablistUpdateEvent("other", player);
        tablistUpdateEvent.setCancelled(true);
        new TablistListener(tablistService).onTablistUpdate(tablistUpdateEvent);

        verify(tablistService).getActiveTablistMap();
        verifyNoMoreInteractions(tablistService);
    }

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