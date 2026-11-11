package me.trae.foundation.minecraft.paper.addon.tablist.services;

import me.trae.foundation.minecraft.paper.addon.tablist.Tablist;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class TablistServiceTest {

    @Test
    void switchingDefinitionsWithIdenticalTextUpdatesActiveStateWithoutResending() {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        final Component headerComponent = Component.text("Header");
        final Component footerComponent = Component.text("Footer");
        final Tablist originalTablist = mock(Tablist.class);
        final Tablist replacementTablist = mock(Tablist.class);
        when(originalTablist.getHeader(player)).thenReturn(headerComponent);
        when(originalTablist.getFooter(player)).thenReturn(footerComponent);
        when(replacementTablist.getHeader(player)).thenReturn(headerComponent);
        when(replacementTablist.getFooter(player)).thenReturn(footerComponent);

        final TablistService tablistService = new TablistService(List.of(originalTablist, replacementTablist));
        tablistService.create(player, originalTablist);
        tablistService.create(player, replacementTablist);

        assertSame(replacementTablist, tablistService.getActiveTablistMap().get(player.getUniqueId()));
        verify(player, times(1)).sendPlayerListHeaderAndFooter(headerComponent, footerComponent);
    }
}