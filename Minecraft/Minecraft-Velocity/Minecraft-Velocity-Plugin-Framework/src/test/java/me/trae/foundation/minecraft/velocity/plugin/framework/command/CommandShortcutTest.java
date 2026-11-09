package me.trae.foundation.minecraft.velocity.plugin.framework.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.proxy.ProxyServer;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.minecraft.velocity.plugin.framework.VelocityPlugin;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.wrapper.BrigadierCommandWrapper;
import me.trae.foundation.minecraft.velocity.plugin.framework.event.internal.Event;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.registry.CommandRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class CommandShortcutTest {

    private final ProxyServer proxyServer = mock(ProxyServer.class);

    private final VelocityPlugin velocityPlugin = mock(VelocityPlugin.class);

    private final CommandSource commandSource = mock(CommandSource.class);

    private final CommandManager commandManager = mock(CommandManager.class);

    private final Map<String, CommandMeta> commandMetaMap = new LinkedHashMap<>();
    private final Map<String, BrigadierCommand> brigadierCommandMap = new LinkedHashMap<>();

    private final List<AbstractCommand<Object, CommandSource>> abstractCommandList = new ArrayList<>();

    @BeforeEach
    void configureProxy() {
        final EventManager eventManager = mock(EventManager.class);
        when(this.proxyServer.getEventManager()).thenReturn(eventManager);
        when(this.proxyServer.getCommandManager()).thenReturn(this.commandManager);
        when(this.velocityPlugin.getProxyServer()).thenReturn(this.proxyServer);
        when(eventManager.fire(any(Event.class))).thenAnswer(invocationOnMock -> CompletableFuture.completedFuture(invocationOnMock.getArgument(0)));
        Injector.INSTANCE.provide(ProxyServer.class, this.proxyServer);

        when(this.commandManager.getCommandMeta(anyString())).thenAnswer(invocationOnMock -> this.commandMetaMap.get(invocationOnMock.getArgument(0)));
        doAnswer(invocationOnMock -> {
            final CommandMeta commandMeta = invocationOnMock.getArgument(0);
            final BrigadierCommand brigadierCommand = invocationOnMock.getArgument(1);
            commandMeta.getAliases().forEach(alias -> {
                this.commandMetaMap.put(alias, commandMeta);
                this.brigadierCommandMap.put(alias, brigadierCommand);
            });

            return null;
        }).when(this.commandManager).register(any(CommandMeta.class), any(BrigadierCommand.class));
        doAnswer(invocationOnMock -> {
            final String alias = invocationOnMock.getArgument(0);
            this.commandMetaMap.remove(alias);
            this.brigadierCommandMap.remove(alias);

            return null;
        }).when(this.commandManager).unregister(anyString());
    }

    @AfterEach
    void cleanup() {
        this.abstractCommandList.forEach(abstractCommand -> CommandRegistry.unregisterCommand(this.velocityPlugin, abstractCommand));
        Injector.INSTANCE.unprovide(ProxyServer.class);
    }

    @Test
    void shortcutExecutesDeepSubcommandWithoutParentArguments() throws CommandSyntaxException {
        final AbstractCommand<Object, CommandSource> abstractCommand = this.createCommand("admin", 2);
        final CommandDispatcher<CommandSource> commandDispatcher = this.createDispatcher(new BrigadierCommandWrapper(abstractCommand, "x"));

        assertEquals(1, commandDispatcher.execute("x", this.commandSource));
        assertEquals(1, commandDispatcher.execute("x player", this.commandSource));
        verify(abstractCommand).execute(this.commandSource, new String[0]);
        verify(abstractCommand).execute(this.commandSource, new String[]{"player"});
    }

    @Test
    void shortcutRoutesChildAliasesForExecutionAndCompletion() throws CommandSyntaxException {
        final AbstractCommand<Object, CommandSource> abstractCommand = this.createCommand("admin", 1);
        final AbstractCommand<Object, CommandSource> childCommand = this.createCommand("reload", 2);
        abstractCommand.getChildCommandMap().put("r", childCommand);
        when(childCommand.getTabComplete(this.commandSource, new String[]{"f"})).thenReturn(List.of("force"));

        final CommandDispatcher<CommandSource> commandDispatcher = this.createDispatcher(new BrigadierCommandWrapper(abstractCommand, "x"));

        assertEquals(1, commandDispatcher.execute("x r force", this.commandSource));
        verify(childCommand).execute(this.commandSource, new String[]{"force"});
        assertEquals("x r force", commandDispatcher.getCompletionSuggestions(commandDispatcher.parse("x r f", this.commandSource)).join().getList().getFirst().apply("x r f"));
    }

    @Test
    void shortcutCompletesTargetAndHandlesTrailingSpace() {
        final AbstractCommand<Object, CommandSource> abstractCommand = this.createCommand("admin", 2);
        when(abstractCommand.getTabComplete(this.commandSource, new String[]{""})).thenReturn(List.of("reload"));

        final CommandDispatcher<CommandSource> commandDispatcher = this.createDispatcher(new BrigadierCommandWrapper(abstractCommand, "x"));

        assertEquals("x reload", commandDispatcher.getCompletionSuggestions(commandDispatcher.parse("x ", this.commandSource)).join().getList().getFirst().apply("x "));
    }

    @Test
    void normalRootCommandStillRoutesSubcommands() throws CommandSyntaxException {
        final AbstractCommand<Object, CommandSource> abstractCommand = this.createCommand("account", 0);
        final AbstractCommand<Object, CommandSource> childCommand = this.createCommand("admin", 1);
        abstractCommand.getChildCommandMap().put("admin", childCommand);

        final CommandDispatcher<CommandSource> commandDispatcher = this.createDispatcher(new BrigadierCommandWrapper(abstractCommand));

        assertEquals(1, commandDispatcher.execute("account admin player", this.commandSource));
        verify(childCommand).execute(this.commandSource, new String[]{"player"});
    }

    @Test
    void shortcutRequiresTargetPermission() {
        final AbstractCommand<Object, CommandSource> abstractCommand = this.createCommand("admin", 1);
        when(abstractCommand.hasPermission(this.commandSource, false)).thenReturn(false);

        final BrigadierCommandWrapper brigadierCommandWrapper = new BrigadierCommandWrapper(abstractCommand, "x");
        final CommandDispatcher<CommandSource> commandDispatcher = this.createDispatcher(brigadierCommandWrapper);

        assertFalse(brigadierCommandWrapper.getBrigadierCommand().getNode().canUse(this.commandSource));
        assertThrows(CommandSyntaxException.class, () -> commandDispatcher.execute("x", this.commandSource));
        verify(abstractCommand, never()).execute(any(), any());
    }

    @Test
    void shortcutRequiresValidSender() {
        final AbstractCommand<Object, CommandSource> abstractCommand = this.createCommand("admin", 1);
        when(abstractCommand.isValidSender(this.commandSource, false)).thenReturn(false);

        final BrigadierCommandWrapper brigadierCommandWrapper = new BrigadierCommandWrapper(abstractCommand, "x");

        assertFalse(brigadierCommandWrapper.getBrigadierCommand().getNode().canUse(this.commandSource));
    }

    @Test
    void registrationPublishesShortcutsAndCleanupPreservesParent() {
        final CommandMeta commandMeta = this.configureCommandMeta("account");
        this.configureCommandMeta("x");
        this.configureCommandMeta("manage");
        final AbstractCommand<Object, CommandSource> abstractCommand = this.createCommand("account", 0);
        final AbstractCommand<Object, CommandSource> childCommand = this.createCommand("admin", 1);
        doReturn(abstractCommand).when(childCommand).getParentCommand();
        when(childCommand.getAliases()).thenReturn(List.of("a"));
        when(childCommand.getShortcuts()).thenReturn(Set.of("x", "manage"));

        this.registerCommand(abstractCommand);
        this.registerCommand(childCommand);

        assertSame(childCommand, abstractCommand.getChildCommandMap().get("admin"));
        assertSame(childCommand, abstractCommand.getChildCommandMap().get("a"));
        assertEquals("x", this.brigadierCommandMap.get("x").getNode().getLiteral());
        assertNotNull(this.commandMetaMap.get("manage"));

        CommandRegistry.unregisterCommand(this.velocityPlugin, childCommand);

        assertTrue(abstractCommand.getChildCommandMap().isEmpty());
        assertNull(this.commandMetaMap.get("x"));
        assertNull(this.commandMetaMap.get("manage"));
        assertSame(commandMeta, this.commandMetaMap.get("account"));
    }

    @Test
    void rootCommandsIgnoreShortcuts() {
        this.configureCommandMeta("account");
        final AbstractCommand<Object, CommandSource> abstractCommand = this.createCommand("account", 0);
        when(abstractCommand.getShortcuts()).thenReturn(Set.of("x"));

        this.registerCommand(abstractCommand);

        assertNotNull(this.commandMetaMap.get("account"));
        assertNull(this.commandMetaMap.get("x"));
    }

    @Test
    void unregisteringAnOldShortcutPreservesItsReplacement() {
        this.configureCommandMeta("x");
        final AbstractCommand<Object, CommandSource> abstractCommand = this.createCommand("account", 0);
        final AbstractCommand<Object, CommandSource> childCommand = this.createCommand("admin", 1);
        doReturn(abstractCommand).when(childCommand).getParentCommand();
        when(childCommand.getShortcuts()).thenReturn(Set.of("x"));
        this.registerCommand(childCommand);

        final CommandMeta replacementCommandMeta = this.configureCommandMeta("x");
        final AbstractCommand<Object, CommandSource> replacementCommand = this.createCommand("x", 0);
        this.registerCommand(replacementCommand);
        CommandRegistry.unregisterCommand(this.velocityPlugin, childCommand);

        assertSame(replacementCommandMeta, this.commandMetaMap.get("x"));
    }

    @SuppressWarnings("unchecked")
    private AbstractCommand<Object, CommandSource> createCommand(final String label, final int argStart) {
        final AbstractCommand<Object, CommandSource> abstractCommand = mock(AbstractCommand.class);
        when(abstractCommand.getLabel()).thenReturn(label);
        when(abstractCommand.getAliases()).thenReturn(List.of());
        when(abstractCommand.isRoot()).thenReturn(argStart == 0);
        when(abstractCommand.getArgStart()).thenReturn(argStart);
        when(abstractCommand.getChildCommandMap()).thenReturn(new LinkedHashMap<>());
        when(abstractCommand.getSenderType()).thenReturn(CommandSource.class);
        when(abstractCommand.isValidSender(this.commandSource, false)).thenReturn(true);
        when(abstractCommand.isValidSender(this.commandSource, true)).thenReturn(true);
        when(abstractCommand.hasPermission(this.commandSource, false)).thenReturn(true);
        when(abstractCommand.hasPermission(this.commandSource, true)).thenReturn(true);

        return abstractCommand;
    }

    private void registerCommand(final AbstractCommand<Object, CommandSource> abstractCommand) {
        CommandRegistry.registerCommand(this.velocityPlugin, abstractCommand);
        this.abstractCommandList.add(abstractCommand);
    }

    private CommandDispatcher<CommandSource> createDispatcher(final BrigadierCommandWrapper brigadierCommandWrapper) {
        final CommandDispatcher<CommandSource> commandDispatcher = new CommandDispatcher<>();
        commandDispatcher.getRoot().addChild(brigadierCommandWrapper.getBrigadierCommand().getNode());

        return commandDispatcher;
    }

    private CommandMeta configureCommandMeta(final String label) {
        final CommandMeta commandMeta = mock(CommandMeta.class);
        when(commandMeta.getAliases()).thenReturn(List.of(label));

        final CommandMeta.Builder commandMetaBuilder = mock(CommandMeta.Builder.class);
        when(this.commandManager.metaBuilder(label)).thenReturn(commandMetaBuilder);
        when(commandMetaBuilder.aliases(any(String[].class))).thenReturn(commandMetaBuilder);
        when(commandMetaBuilder.plugin(this.velocityPlugin)).thenReturn(commandMetaBuilder);
        when(commandMetaBuilder.build()).thenReturn(commandMeta);

        return commandMeta;
    }
}