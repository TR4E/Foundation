package me.trae.foundation.minecraft.paper.addon.sidebar.services;

import me.trae.foundation.minecraft.paper.addon.sidebar.Sidebar;
import me.trae.foundation.minecraft.paper.addon.sidebar.events.SidebarUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.sidebar.listeners.SidebarListener;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilNms;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilPacket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.Bootstrap;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

final class SidebarServiceTest {

    @BeforeAll
    static void bootstrap() {
        System.setProperty("terminal.jline", "false");

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void changingTheSameLineListStillSendsUpdatesAndKeepsACachedSnapshot() {
        final Player player = this.player();
        final List<Component> lineList = new ArrayList<>(List.of(Component.text("First")));
        final Sidebar sidebar = this.sidebar(player, lineList);
        final SidebarService sidebarService = new SidebarService(List.of(sidebar));

        try (final MockedStatic<UtilNms> utilNmsMockedStatic = this.mockComponents();
             final MockedStatic<UtilPacket> utilPacketMockedStatic = mockStatic(UtilPacket.class)) {
            sidebarService.create(player, sidebar);
            lineList.set(0, Component.text("Second"));
            sidebarService.updateLines(player, sidebar);
            lineList.set(0, Component.text("Third"));

            assertEquals(List.of(Component.text("Second")), sidebarService.getCachedLinesMap().get(player.getUniqueId()));

            sidebarService.updateLines(player, sidebar);

            utilPacketMockedStatic.verify(() -> UtilPacket.send(eq(player), any(ClientboundSetScorePacket.class)), times(3));
            assertEquals(List.of(Component.text("Third")), sidebarService.getCachedLinesMap().get(player.getUniqueId()));
        }
    }

    @Test
    void cancelledUpdateClearsTheActiveDefinitionAndBothCaches() {
        final Player player = this.player();
        final Sidebar sidebar = this.sidebar(player, List.of(Component.text("Line")));
        final SidebarService sidebarService = new SidebarService(List.of(sidebar));
        final SidebarListener sidebarListener = new SidebarListener(sidebarService);

        try (final MockedStatic<UtilNms> utilNmsMockedStatic = this.mockComponents();
             final MockedStatic<UtilPacket> utilPacketMockedStatic = mockStatic(UtilPacket.class)) {
            sidebarListener.onSidebarUpdate(new SidebarUpdateEvent(player));

            assertEquals(sidebar, sidebarService.getActiveSidebarMap().get(player.getUniqueId()));

            final SidebarUpdateEvent sidebarUpdateEvent = new SidebarUpdateEvent(player);
            sidebarUpdateEvent.setCancelled(true);
            sidebarListener.onSidebarUpdate(sidebarUpdateEvent);

            assertTrue(sidebarService.getActiveSidebarMap().isEmpty());
            assertTrue(sidebarService.getCachedTitleMap().isEmpty());
            assertTrue(sidebarService.getCachedLinesMap().isEmpty());
        }
    }

    private Player player() {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);

        return player;
    }

    private Sidebar sidebar(final Player player, final List<Component> lineList) {
        final Sidebar sidebar = mock(Sidebar.class);
        when(sidebar.getIdentifier()).thenReturn("test");
        when(sidebar.getTitle(player)).thenReturn(Component.text("Title"));
        when(sidebar.getLines(player)).thenReturn(lineList);
        when(sidebar.canDisplay()).thenReturn(true);
        when(sidebar.canDisplay(player)).thenReturn(true);

        return sidebar;
    }

    private MockedStatic<UtilNms> mockComponents() {
        final MockedStatic<UtilNms> utilNmsMockedStatic = mockStatic(UtilNms.class);
        utilNmsMockedStatic.when(() -> UtilNms.toComponent(any(Component.class))).thenAnswer(
                invocationOnMock -> net.minecraft.network.chat.Component.literal(
                        PlainTextComponentSerializer.plainText().serialize(invocationOnMock.getArgument(0))
                )
        );

        return utilNmsMockedStatic;
    }
}