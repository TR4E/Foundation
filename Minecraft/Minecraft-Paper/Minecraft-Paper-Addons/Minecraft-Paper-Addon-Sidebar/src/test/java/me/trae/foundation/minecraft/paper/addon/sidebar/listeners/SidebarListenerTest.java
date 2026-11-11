package me.trae.foundation.minecraft.paper.addon.sidebar.listeners;

import me.trae.foundation.minecraft.paper.addon.sidebar.Sidebar;
import me.trae.foundation.minecraft.paper.addon.sidebar.events.SidebarUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.sidebar.services.SidebarService;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

final class SidebarListenerTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void onlyUnscopedUpdatesCanReplaceTheActiveDefinitionWithAFallback(final boolean scoped) {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);

        final Sidebar originalSidebar = mock(Sidebar.class);
        final Sidebar fallbackSidebar = mock(Sidebar.class);
        when(originalSidebar.getIdentifier()).thenReturn("original");
        when(fallbackSidebar.getIdentifier()).thenReturn("fallback");
        final ConcurrentHashMap<UUID, Sidebar> activeSidebarMap = new ConcurrentHashMap<>();
        activeSidebarMap.put(player.getUniqueId(), originalSidebar);
        final SidebarService sidebarService = mock(SidebarService.class);
        when(sidebarService.getActiveSidebarMap()).thenReturn(activeSidebarMap);
        when(sidebarService.getEligibleSidebar(player)).thenReturn(Optional.of(fallbackSidebar));

        new SidebarListener(sidebarService).onSidebarUpdate(new SidebarUpdateEvent(scoped ? "original" : null, player));

        verify(sidebarService).clear(player);

        if (scoped) {
            verify(sidebarService, never()).create(player, fallbackSidebar);
        } else {
            verify(sidebarService).create(player, fallbackSidebar);
            assertSame(fallbackSidebar, activeSidebarMap.get(player.getUniqueId()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scopedCancellationDoesNothingWithoutAMatchingActiveSidebar(final boolean active) {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        final Sidebar activeSidebar = mock(Sidebar.class);
        when(activeSidebar.getIdentifier()).thenReturn("active");
        final ConcurrentHashMap<UUID, Sidebar> activeSidebarMap = new ConcurrentHashMap<>();

        if (active) {
            activeSidebarMap.put(player.getUniqueId(), activeSidebar);
        }

        final SidebarService sidebarService = mock(SidebarService.class);
        when(sidebarService.getActiveSidebarMap()).thenReturn(activeSidebarMap);
        final SidebarUpdateEvent sidebarUpdateEvent = new SidebarUpdateEvent("other", player);
        sidebarUpdateEvent.setCancelled(true);
        new SidebarListener(sidebarService).onSidebarUpdate(sidebarUpdateEvent);

        verify(sidebarService).getActiveSidebarMap();
        verifyNoMoreInteractions(sidebarService);
    }

    @Test
    void offlineUpdateDoesNotTouchSidebarState() {
        final Player player = mock(Player.class);
        final SidebarService sidebarService = mock(SidebarService.class);

        new SidebarListener(sidebarService).onSidebarUpdate(new SidebarUpdateEvent(player));

        verifyNoInteractions(sidebarService);
    }

    @Test
    void queuedUpdateAfterQuitDoesNotPreventCreationOnRejoin() {
        final UUID playerId = UUID.randomUUID();
        final Player originalPlayer = mock(Player.class);
        when(originalPlayer.getUniqueId()).thenReturn(playerId);
        when(originalPlayer.isOnline()).thenReturn(true);

        final Sidebar sidebar = mock(Sidebar.class);
        when(sidebar.getIdentifier()).thenReturn("DEFAULT");

        final ConcurrentHashMap<UUID, Sidebar> activeSidebarMap = new ConcurrentHashMap<>();
        final SidebarService sidebarService = mock(SidebarService.class);
        when(sidebarService.getActiveSidebarMap()).thenReturn(activeSidebarMap);
        when(sidebarService.getEligibleSidebar(originalPlayer)).thenReturn(Optional.of(sidebar));
        doAnswer(_ -> {
            activeSidebarMap.remove(playerId);
            return null;
        }).when(sidebarService).clear(originalPlayer);

        final SidebarListener sidebarListener = new SidebarListener(sidebarService);
        sidebarListener.onSidebarUpdate(new SidebarUpdateEvent(originalPlayer));
        assertSame(sidebar, activeSidebarMap.get(playerId));

        final PlayerQuitEvent playerQuitEvent = mock(PlayerQuitEvent.class);
        when(playerQuitEvent.getPlayer()).thenReturn(originalPlayer);
        when(originalPlayer.isOnline()).thenReturn(false);
        sidebarListener.onPlayerQuit(playerQuitEvent);
        sidebarListener.onSidebarUpdate(new SidebarUpdateEvent("DEFAULT", originalPlayer));
        assertTrue(activeSidebarMap.isEmpty());

        final Player rejoiningPlayer = mock(Player.class);
        when(rejoiningPlayer.getUniqueId()).thenReturn(playerId);
        when(rejoiningPlayer.isOnline()).thenReturn(true);
        when(sidebarService.getEligibleSidebar(rejoiningPlayer)).thenReturn(Optional.of(sidebar));
        sidebarListener.onSidebarUpdate(new SidebarUpdateEvent(rejoiningPlayer));

        verify(sidebarService).create(rejoiningPlayer, sidebar);
        verify(sidebarService, never()).refreshTitle(rejoiningPlayer, sidebar);
        assertSame(sidebar, activeSidebarMap.get(playerId));
    }
}