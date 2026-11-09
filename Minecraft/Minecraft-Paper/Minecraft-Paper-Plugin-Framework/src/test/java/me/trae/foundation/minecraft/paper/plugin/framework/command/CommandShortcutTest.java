package me.trae.foundation.minecraft.paper.plugin.framework.command;

import me.trae.foundation.minecraft.paper.plugin.framework.command.wrapper.BukkitCommandWrapper;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.registry.CommandRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class CommandShortcutTest {

    private final MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class);

    private final Server server = mock(Server.class);

    private final CommandSender commandSender = mock(CommandSender.class);

    @BeforeEach
    void configureServer() {
        this.bukkitMockedStatic.when(Bukkit::getServer).thenReturn(this.server);
        when(this.server.getPluginManager()).thenReturn(mock(PluginManager.class));
    }

    @AfterEach
    void closeBukkitMock() {
        this.bukkitMockedStatic.close();
    }

    @Test
    void shortcutExecutesDeepSubcommandWithoutParentArguments() {
        final AbstractCommand<Object, CommandSender> abstractCommand = this.createCommand("admin", 2);
        final BukkitCommandWrapper bukkitCommandWrapper = new BukkitCommandWrapper(abstractCommand, "x");

        assertTrue(bukkitCommandWrapper.execute(this.commandSender, "x", new String[0]));
        assertTrue(bukkitCommandWrapper.execute(this.commandSender, "x", new String[]{"player"}));
        verify(abstractCommand).execute(this.commandSender, new String[0]);
        verify(abstractCommand).execute(this.commandSender, new String[]{"player"});
    }

    @Test
    void shortcutRoutesChildAliasesAndPreservesTheirArguments() {
        final AbstractCommand<Object, CommandSender> abstractCommand = this.createCommand("admin", 1);
        final AbstractCommand<Object, CommandSender> childCommand = this.createCommand("reload", 2);
        abstractCommand.getChildCommandMap().put("r", childCommand);
        when(childCommand.getTabComplete(this.commandSender, new String[]{"f"})).thenReturn(List.of("force"));

        final BukkitCommandWrapper bukkitCommandWrapper = new BukkitCommandWrapper(abstractCommand, "x");

        assertTrue(bukkitCommandWrapper.execute(this.commandSender, "x", new String[]{"r", "force"}));
        verify(childCommand).execute(this.commandSender, new String[]{"force"});
        assertEquals(List.of("force"), bukkitCommandWrapper.tabComplete(this.commandSender, "x", new String[]{"r", "f"}));
    }

    @Test
    void shortcutCompletesTheTargetWithoutConsumingThePartialArgument() {
        final AbstractCommand<Object, CommandSender> abstractCommand = this.createCommand("admin", 2);
        when(abstractCommand.getTabComplete(this.commandSender, new String[]{"r"})).thenReturn(List.of("reload"));

        final BukkitCommandWrapper bukkitCommandWrapper = new BukkitCommandWrapper(abstractCommand, "x");

        assertEquals(List.of("reload"), bukkitCommandWrapper.tabComplete(this.commandSender, "x", new String[]{"r"}));
    }

    @Test
    void normalRootCommandStillRoutesSubcommands() {
        final AbstractCommand<Object, CommandSender> abstractCommand = this.createCommand("account", 0);
        final AbstractCommand<Object, CommandSender> childCommand = this.createCommand("admin", 1);
        abstractCommand.getChildCommandMap().put("admin", childCommand);

        final BukkitCommandWrapper bukkitCommandWrapper = new BukkitCommandWrapper(abstractCommand);

        assertTrue(bukkitCommandWrapper.execute(this.commandSender, "account", new String[]{"admin", "player"}));
        verify(childCommand).execute(this.commandSender, new String[]{"player"});
    }

    @Test
    void shortcutUsesTargetPermissionsForVisibilityExecutionAndCompletion() {
        final AbstractCommand<Object, CommandSender> abstractCommand = this.createCommand("admin", 1);
        when(abstractCommand.hasPermission(this.commandSender, false)).thenReturn(false);
        when(abstractCommand.hasPermission(this.commandSender, true)).thenReturn(false);

        final BukkitCommandWrapper bukkitCommandWrapper = new BukkitCommandWrapper(abstractCommand, "x");

        assertFalse(bukkitCommandWrapper.testPermissionSilent(this.commandSender));
        assertFalse(bukkitCommandWrapper.execute(this.commandSender, "x", new String[0]));
        assertEquals(List.of(), bukkitCommandWrapper.tabComplete(this.commandSender, "x", new String[]{""}));
        verify(abstractCommand, never()).execute(any(), any());
        verify(abstractCommand, never()).getTabComplete(any(), any());
    }

    @Test
    void shortcutUsesTargetSenderRestrictions() {
        final AbstractCommand<Object, CommandSender> abstractCommand = this.createCommand("admin", 1);
        when(abstractCommand.isValidSender(this.commandSender, false)).thenReturn(false);
        when(abstractCommand.isValidSender(this.commandSender, true)).thenReturn(false);

        final BukkitCommandWrapper bukkitCommandWrapper = new BukkitCommandWrapper(abstractCommand, "x");

        assertFalse(bukkitCommandWrapper.testPermissionSilent(this.commandSender));
        assertFalse(bukkitCommandWrapper.execute(this.commandSender, "x", new String[0]));
        assertEquals(List.of(), bukkitCommandWrapper.tabComplete(this.commandSender, "x", new String[]{""}));
        verify(abstractCommand, never()).execute(any(), any());
    }

    @Test
    void registrationPublishesShortcutsAndCleanupRemovesNamespacedEntries() {
        final SimpleCommandMap simpleCommandMap = this.createCommandMap();
        final Plugin plugin = this.createPlugin();
        final AbstractCommand<Object, CommandSender> abstractCommand = this.createCommand("account", 0);
        final AbstractCommand<Object, CommandSender> childCommand = this.createCommand("admin", 1);
        doReturn(abstractCommand).when(childCommand).getParentCommand();
        when(childCommand.getAliases()).thenReturn(List.of("a"));
        when(childCommand.getShortcuts()).thenReturn(Set.of("x", "manage"));

        CommandRegistry.registerCommand(plugin, abstractCommand);
        CommandRegistry.registerCommand(plugin, childCommand);

        assertSame(childCommand, abstractCommand.getChildCommandMap().get("admin"));
        assertSame(childCommand, abstractCommand.getChildCommandMap().get("a"));
        assertSame(childCommand, ((BukkitCommandWrapper) simpleCommandMap.getCommand("x")).getAbstractCommand());
        assertSame(simpleCommandMap.getCommand("x"), simpleCommandMap.getCommand("test:x"));
        assertNotNull(simpleCommandMap.getCommand("manage"));

        CommandRegistry.unregisterCommand(childCommand);

        assertTrue(abstractCommand.getChildCommandMap().isEmpty());
        assertNull(simpleCommandMap.getCommand("x"));
        assertNull(simpleCommandMap.getCommand("test:x"));
        assertNull(simpleCommandMap.getCommand("manage"));
        assertNull(simpleCommandMap.getCommand("test:manage"));
        assertNotNull(simpleCommandMap.getCommand("account"));
    }

    @Test
    void rootCommandsIgnoreShortcuts() {
        final SimpleCommandMap simpleCommandMap = this.createCommandMap();
        final AbstractCommand<Object, CommandSender> abstractCommand = this.createCommand("account", 0);
        when(abstractCommand.getAliases()).thenReturn(List.of("acc"));
        when(abstractCommand.getShortcuts()).thenReturn(Set.of("x"));

        CommandRegistry.registerCommand(this.createPlugin(), abstractCommand);

        assertNotNull(simpleCommandMap.getCommand("account"));
        assertNotNull(simpleCommandMap.getCommand("acc"));
        assertNull(simpleCommandMap.getCommand("x"));
    }

    @Test
    void unregisteringAnOldShortcutPreservesItsReplacement() {
        final SimpleCommandMap simpleCommandMap = this.createCommandMap();
        final Plugin plugin = this.createPlugin();
        final AbstractCommand<Object, CommandSender> abstractCommand = this.createCommand("account", 0);
        final AbstractCommand<Object, CommandSender> childCommand = this.createCommand("admin", 1);
        doReturn(abstractCommand).when(childCommand).getParentCommand();
        when(childCommand.getShortcuts()).thenReturn(Set.of("x"));
        CommandRegistry.registerCommand(plugin, childCommand);

        final AbstractCommand<Object, CommandSender> replacementCommand = this.createCommand("x", 0);
        CommandRegistry.registerCommand(plugin, replacementCommand);
        CommandRegistry.unregisterCommand(childCommand);

        assertSame(replacementCommand, ((BukkitCommandWrapper) simpleCommandMap.getCommand("x")).getAbstractCommand());
    }

    @SuppressWarnings("unchecked")
    private AbstractCommand<Object, CommandSender> createCommand(final String label, final int argStart) {
        final AbstractCommand<Object, CommandSender> abstractCommand = mock(AbstractCommand.class);
        when(abstractCommand.getLabel()).thenReturn(label);
        when(abstractCommand.getDescription()).thenReturn("");
        when(abstractCommand.getUsage()).thenReturn("/%s".formatted(label));
        when(abstractCommand.getAliases()).thenReturn(List.of());
        when(abstractCommand.isRoot()).thenReturn(argStart == 0);
        when(abstractCommand.getArgStart()).thenReturn(argStart);
        when(abstractCommand.getChildCommandMap()).thenReturn(new LinkedHashMap<>());
        when(abstractCommand.getSenderType()).thenReturn(CommandSender.class);
        when(abstractCommand.isValidSender(this.commandSender, false)).thenReturn(true);
        when(abstractCommand.isValidSender(this.commandSender, true)).thenReturn(true);
        when(abstractCommand.hasPermission(this.commandSender, false)).thenReturn(true);
        when(abstractCommand.hasPermission(this.commandSender, true)).thenReturn(true);

        return abstractCommand;
    }

    private SimpleCommandMap createCommandMap() {
        final SimpleCommandMap simpleCommandMap = new SimpleCommandMap(this.server, new LinkedHashMap<>());
        when(this.server.getCommandMap()).thenReturn(simpleCommandMap);

        return simpleCommandMap;
    }

    private Plugin createPlugin() {
        final Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("test");

        return plugin;
    }
}
