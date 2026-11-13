package me.trae.foundation.minecraft.paper.addon.window;

import io.papermc.paper.registry.RegistryAccess;
import lombok.NoArgsConstructor;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.minecraft.paper.addon.window.element.Element;
import me.trae.foundation.minecraft.paper.addon.window.events.WindowOpenEvent;
import me.trae.foundation.minecraft.paper.addon.window.layout.Layout;
import me.trae.foundation.minecraft.paper.addon.window.layout.PaginatedLayout;
import me.trae.foundation.minecraft.paper.addon.window.listeners.WindowCloseListener;
import me.trae.foundation.minecraft.paper.addon.window.listeners.WindowOpenListener;
import me.trae.foundation.minecraft.paper.addon.window.services.WindowService;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class WindowLifecycleTest {

    private final TestApplication testApplication = new TestApplication();

    private final Player player = mock(Player.class);

    private final Inventory craftingInventory = mock(Inventory.class);

    private final AtomicReference<InventoryView> inventoryViewAtomicReference = new AtomicReference<>();

    private final AtomicBoolean primaryThreadAtomicBoolean = new AtomicBoolean(true);

    private final ArrayDeque<Runnable> delayedRunnableDeque = new ArrayDeque<>();
    private final ArrayDeque<Runnable> asynchronousRunnableDeque = new ArrayDeque<>();
    private final ArrayDeque<Runnable> synchronousRunnableDeque = new ArrayDeque<>();

    private final UUID viewerId = UUID.randomUUID();

    private WindowService windowService;

    private WindowOpenListener windowOpenListener;

    private WindowCloseListener windowCloseListener;

    private MockedStatic<UtilTask> utilTaskMockedStatic;
    private MockedStatic<UtilServer> utilServerMockedStatic;
    private MockedStatic<UtilEvent> utilEventMockedStatic;

    @BeforeAll
    static void initializeInventoryTypes() {
        try (final MockedStatic<RegistryAccess> registryAccessMockedStatic = mockStatic(RegistryAccess.class)) {
            final RegistryAccess registryAccess = mock(
                    RegistryAccess.class,
                    _ -> mock(Registry.class, invocationOnMock -> {
                        if (invocationOnMock.getMethod().getName().equals("getOrThrow")) {
                            return mock(MenuType.Typed.class);
                        }

                        return RETURNS_DEFAULTS.answer(invocationOnMock);
                    })
            );
            registryAccessMockedStatic.when(RegistryAccess::registryAccess).thenReturn(registryAccess);
            InventoryType.CHEST.getDefaultSize();
        }
    }

    @BeforeEach
    void initialize() {
        Injector.INSTANCE.initialize(this.testApplication, List.of(WindowService.class));
        this.windowService = Injector.INSTANCE.get(WindowService.class);
        this.windowOpenListener = new WindowOpenListener(this.windowService);
        this.windowCloseListener = new WindowCloseListener(this.windowService);

        when(this.player.getUniqueId()).thenReturn(this.viewerId);
        when(this.craftingInventory.getType()).thenReturn(InventoryType.CRAFTING);
        this.inventoryViewAtomicReference.set(this.inventoryView(this.craftingInventory));
        when(this.player.getOpenInventory()).thenAnswer(_ -> this.inventoryViewAtomicReference.get());

        this.utilTaskMockedStatic = mockStatic(UtilTask.class);
        this.utilServerMockedStatic = mockStatic(UtilServer.class);
        this.utilEventMockedStatic = mockStatic(UtilEvent.class);
        this.utilServerMockedStatic.when(() -> UtilServer.getOnlinePlayerById(this.viewerId))
                .thenReturn(Optional.of(this.player));
        this.utilEventMockedStatic.when(() -> UtilEvent.supply(any()))
                .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        this.utilTaskMockedStatic.when(() -> UtilTask.executeSynchronously(any(Runnable.class)))
                .thenAnswer(invocationOnMock -> {
                    final Runnable runnable = invocationOnMock.getArgument(0);

                    if (this.primaryThreadAtomicBoolean.get()) {
                        runnable.run();
                    } else {
                        this.synchronousRunnableDeque.addLast(runnable);
                    }

                    return null;
                });
        this.utilTaskMockedStatic.when(() -> UtilTask.executeAsynchronously(any(Runnable.class)))
                .thenAnswer(invocationOnMock -> {
                    this.asynchronousRunnableDeque.addLast(invocationOnMock.getArgument(0));

                    return null;
                });
        this.utilTaskMockedStatic.when(() -> UtilTask.executeLaterSynchronously(
                anyInt(), any(ChronoUnit.class), any(Runnable.class)
        )).thenAnswer(invocationOnMock -> {
            this.delayedRunnableDeque.addLast(invocationOnMock.getArgument(2));

            return null;
        });

        doAnswer(invocationOnMock -> {
            this.openInventory(invocationOnMock.getArgument(0));

            return this.inventoryViewAtomicReference.get();
        }).when(this.player).openInventory(any(Inventory.class));
        doAnswer(invocationOnMock -> {
            final InventoryCloseEvent.Reason inventoryCloseReason = invocationOnMock.getArgument(0);
            this.windowCloseListener.onInventoryClose(new InventoryCloseEvent(
                    this.inventoryViewAtomicReference.get(), inventoryCloseReason
            ));
            this.inventoryViewAtomicReference.set(this.inventoryView(this.craftingInventory));

            return null;
        }).when(this.player).closeInventory(any(InventoryCloseEvent.Reason.class));
        doAnswer(_ -> {
            this.player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);

            return null;
        }).when(this.player).closeInventory();
    }

    @AfterEach
    void shutdown() {
        Injector.INSTANCE.shutdown(this.testApplication);
        Optional.ofNullable(this.utilTaskMockedStatic).ifPresent(MockedStatic::close);
        Optional.ofNullable(this.utilServerMockedStatic).ifPresent(MockedStatic::close);
        Optional.ofNullable(this.utilEventMockedStatic).ifPresent(MockedStatic::close);
    }

    @Test
    void closeChangesTheInventoryImmediately() {
        final TestWindow testWindow = this.window();
        this.openInventory(testWindow.getInventory());

        testWindow.close();

        verify(this.player).closeInventory();
        verify(this.player).closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        assertSame(this.craftingInventory, this.player.getOpenInventory().getTopInventory());
        assertTrue(this.delayedRunnableDeque.isEmpty());
        assertTrue(this.windowService.getWindowByInventoryMap().isEmpty());
    }

    @Test
    void closeDoesNotCloseAReplacementInventory() {
        final TestWindow testWindow = this.window();
        this.openInventory(testWindow.getInventory());

        final Inventory inventory = this.inventory();
        this.openInventory(inventory);
        testWindow.close();

        verify(this.player, never()).closeInventory();
        assertSame(inventory, this.player.getOpenInventory().getTopInventory());
    }

    @Test
    void openChecksImmediatelyAndDisplaysAfterRendering() {
        final TestWindow testWindow = this.window();

        testWindow.open();

        verify(testWindow).canOpen(this.player);
        assertFalse(this.asynchronousRunnableDeque.isEmpty());
        assertTrue(this.delayedRunnableDeque.isEmpty());
        verify(this.player, never()).openInventory(any(Inventory.class));

        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());
        this.synchronousRunnableDeque.removeFirst().run();

        assertSame(testWindow.getInventory(), this.player.getOpenInventory().getTopInventory());
        assertSame(testWindow, this.windowService.getWindowByPlayerMap().get(this.viewerId));
        assertTrue(this.windowService.getRequestIdMap().isEmpty());
    }

    @Test
    void aSlowOpenCanCompleteAfterReturningToItsCapturedInventory() {
        final TestWindow firstTestWindow = this.window();
        final TestWindow secondTestWindow = this.window();
        firstTestWindow.open();
        secondTestWindow.open();

        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeLast());
        this.synchronousRunnableDeque.removeFirst().run();
        this.player.closeInventory(InventoryCloseEvent.Reason.PLAYER);
        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());
        this.synchronousRunnableDeque.removeFirst().run();

        assertSame(firstTestWindow.getInventory(), this.player.getOpenInventory().getTopInventory());
    }

    @Test
    void aDifferentInventoryPreventsAnOlderOpen() {
        final TestWindow testWindow = this.window();
        testWindow.open();

        final Inventory inventory = this.inventory();
        this.openInventory(inventory);
        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());
        this.synchronousRunnableDeque.removeFirst().run();

        verify(this.player, never()).openInventory(testWindow.getInventory());
        assertSame(inventory, this.player.getOpenInventory().getTopInventory());
    }

    @Test
    void closeDoesNotCancelTheSameWindowsUnfinishedOpen() {
        final TestWindow testWindow = this.window();
        testWindow.open();

        testWindow.close();
        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());
        this.synchronousRunnableDeque.removeFirst().run();

        assertSame(testWindow.getInventory(), this.player.getOpenInventory().getTopInventory());
        assertTrue(this.windowService.getRequestIdMap().isEmpty());
    }

    @Test
    void anExternalInventoryRoundTripCancelsAPendingReopen() {
        final TestWindow testWindow = this.window();
        doReturn(false).when(testWindow).canClose(this.player);
        this.openInventory(testWindow.getInventory());
        this.player.closeInventory(InventoryCloseEvent.Reason.PLAYER);

        this.openInventory(this.inventory());
        this.player.closeInventory(InventoryCloseEvent.Reason.PLAYER);
        this.runDelayedTasks();

        verify(this.player, never()).openInventory(testWindow.getInventory());
        assertTrue(this.windowService.getReopeningMap().isEmpty());
        assertTrue(this.windowService.getWindowByInventoryMap().isEmpty());
    }

    @Test
    void anOldReopenCannotConsumeANewerRequestForTheSameInventory() {
        final TestWindow testWindow = this.window();
        doReturn(false).when(testWindow).canClose(this.player);
        this.openInventory(testWindow.getInventory());
        this.player.closeInventory(InventoryCloseEvent.Reason.PLAYER);
        final UUID firstRequestId = this.windowService.getRequestIdMap().get(this.viewerId);

        this.openInventory(testWindow.getInventory());
        this.player.closeInventory(InventoryCloseEvent.Reason.PLAYER);
        final UUID secondRequestId = this.windowService.getRequestIdMap().get(this.viewerId);
        assertNotEquals(firstRequestId, secondRequestId);

        this.delayedRunnableDeque.removeFirst().run();

        assertSame(secondRequestId, this.windowService.getRequestIdMap().get(this.viewerId));
        verify(this.player, never()).openInventory(testWindow.getInventory());

        this.runDelayedTasks();

        verify(this.player).openInventory(testWindow.getInventory());
        verify(testWindow, times(2)).onOpen(this.player);
        assertTrue(this.windowService.getRequestIdMap().isEmpty());
    }

    @Test
    void anOpenMadeFromOnOpenStartsAnIndependentRender() {
        final TestWindow firstTestWindow = this.window();
        final TestWindow secondTestWindow = this.window();
        doAnswer(_ -> {
            secondTestWindow.open();

            return null;
        }).when(firstTestWindow).onOpen(this.player);

        firstTestWindow.open();
        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());
        this.synchronousRunnableDeque.removeFirst().run();

        assertTrue(secondTestWindow.isRendering());
        assertTrue(this.windowService.getRequestIdMap().isEmpty());

        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());
        this.synchronousRunnableDeque.removeFirst().run();

        assertSame(secondTestWindow.getInventory(), this.player.getOpenInventory().getTopInventory());
    }

    @Test
    void closeFromOnOpenChangesTheInventoryImmediately() {
        final TestWindow testWindow = this.window();
        doAnswer(_ -> {
            testWindow.close();

            return null;
        }).when(testWindow).onOpen(this.player);

        testWindow.open();
        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());
        this.synchronousRunnableDeque.removeFirst().run();

        verify(this.player).closeInventory();
        assertSame(this.craftingInventory, this.player.getOpenInventory().getTopInventory());
        assertTrue(this.delayedRunnableDeque.isEmpty());
        assertTrue(this.windowService.getWindowByPlayerMap().isEmpty());
    }

    @Test
    void anExplicitOpenFromACloseVetoTakesPriorityOverAnAutomaticReopen() {
        final TestWindow firstTestWindow = this.window();
        final TestWindow secondTestWindow = this.window();
        doAnswer(_ -> {
            secondTestWindow.open();

            return false;
        }).when(firstTestWindow).canClose(this.player);
        this.openInventory(firstTestWindow.getInventory());

        this.player.closeInventory(InventoryCloseEvent.Reason.PLAYER);
        this.runDelayedTasks();
        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());
        this.synchronousRunnableDeque.removeFirst().run();

        assertSame(secondTestWindow.getInventory(), this.player.getOpenInventory().getTopInventory());
        assertTrue(this.windowService.getReopeningMap().isEmpty());
    }

    @Test
    void aCancelledOpenDoesNotStartRendering() {
        final TestWindow testWindow = this.window();
        this.utilEventMockedStatic.when(() -> UtilEvent.supply(any(WindowOpenEvent.class)))
                .thenAnswer(invocationOnMock -> {
                    final WindowOpenEvent windowOpenEvent = invocationOnMock.getArgument(0);
                    windowOpenEvent.setCancelled(true);

                    return windowOpenEvent;
                });

        testWindow.open();
        this.runDelayedTasks();

        assertTrue(this.asynchronousRunnableDeque.isEmpty());
        assertTrue(this.windowService.getRequestIdMap().isEmpty());
        verify(this.player, never()).openInventory(any(Inventory.class));
    }

    @Test
    void closeAllBypassesVetoesAndChangesTheInventoryCapturedByAPendingOpen() {
        final TestWindow firstTestWindow = this.window();
        final TestWindow secondTestWindow = this.window();
        doReturn(false).when(firstTestWindow).canClose(this.player);
        this.openInventory(firstTestWindow.getInventory());
        secondTestWindow.open();
        this.runDelayedTasks();

        this.windowService.closeAll();
        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());
        this.synchronousRunnableDeque.removeFirst().run();

        verify(this.player).closeInventory(InventoryCloseEvent.Reason.CANT_USE);
        verify(this.player, never()).openInventory(secondTestWindow.getInventory());
        assertTrue(this.windowService.getWindowByInventoryMap().isEmpty());
        assertTrue(this.windowService.getRequestIdMap().isEmpty());
        assertTrue(this.windowService.getReopeningMap().isEmpty());
    }

    @Test
    void closeAllCancelsPendingReopens() {
        final TestWindow testWindow = this.window();
        doReturn(false).when(testWindow).canClose(this.player);
        this.openInventory(testWindow.getInventory());
        this.player.closeInventory(InventoryCloseEvent.Reason.PLAYER);

        this.windowService.closeAll();
        this.runDelayedTasks();

        verify(this.player, never()).openInventory(testWindow.getInventory());
        assertTrue(this.windowService.getReopeningMap().isEmpty());
    }

    @Test
    void closeAllFromOnOpenCannotLeaveThatInventoryVisible() {
        final TestWindow testWindow = this.window();
        doAnswer(_ -> {
            this.windowService.closeAll();

            return null;
        }).when(testWindow).onOpen(this.player);

        testWindow.open();
        this.runDelayedTasks();
        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());
        this.synchronousRunnableDeque.removeFirst().run();

        assertSame(this.craftingInventory, this.player.getOpenInventory().getTopInventory());
        assertTrue(this.windowService.getWindowByPlayerMap().isEmpty());
        assertTrue(this.windowService.getWindowByInventoryMap().isEmpty());
        assertTrue(this.windowService.getRequestIdMap().isEmpty());
    }

    @Test
    void anOfflineViewerPreventsAnUnfinishedOpen() {
        final TestWindow testWindow = this.window();
        testWindow.open();

        this.windowService.cleanup(this.player);
        this.utilServerMockedStatic.when(() -> UtilServer.getOnlinePlayerById(this.viewerId))
                .thenReturn(Optional.empty());
        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());
        this.synchronousRunnableDeque.removeFirst().run();

        verify(this.player, never()).openInventory(testWindow.getInventory());
    }

    @Test
    void aFailedBuildClearsPagePlacementsAndClosesDuringAbort() {
        final PaginatedLayout paginatedLayout = PaginatedLayout.of(9, new int[]{4});
        final Element<?> element = mock(Element.class);
        paginatedLayout.setContent(List.of(element));

        final TestWindow testWindow = this.window(paginatedLayout, () -> {});
        doThrow(new IllegalStateException("Build failed")).when(testWindow).build();
        this.openInventory(testWindow.getInventory());

        testWindow.render();

        assertThrows(
                IllegalStateException.class,
                () -> this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst())
        );
        assertTrue(testWindow.isRendering());
        verify(this.player, never()).closeInventory(any(InventoryCloseEvent.Reason.class));

        this.synchronousRunnableDeque.removeFirst().run();

        assertFalse(testWindow.isRendering());
        assertTrue(paginatedLayout.getElementMap().isEmpty());
        assertTrue(paginatedLayout.getSlotMap().isEmpty());
        assertTrue(this.delayedRunnableDeque.isEmpty());
        verify(this.player).closeInventory(InventoryCloseEvent.Reason.CANT_USE);
    }

    @Test
    void refreshFailuresPropagateWithoutAbortingTheWindow() {
        final Layout layout = mock(Layout.class);
        when(layout.getSize()).thenReturn(9);

        final TestWindow testWindow = this.window(layout, () -> {});
        this.openInventory(testWindow.getInventory());
        doThrow(new IllegalStateException("Item failed")).when(layout).applyTo(any(Inventory.class));

        assertThrows(IllegalStateException.class, testWindow::refresh);

        verify(this.player, never()).closeInventory(any(InventoryCloseEvent.Reason.class));
        assertFalse(testWindow.isRendering());
        assertTrue(this.delayedRunnableDeque.isEmpty());
        assertSame(testWindow.getInventory(), this.player.getOpenInventory().getTopInventory());
    }

    @Test
    void abortingAnOlderWindowDoesNotCancelANewerOpen() {
        final TestWindow firstTestWindow = this.window();
        final TestWindow secondTestWindow = this.window();
        this.openInventory(firstTestWindow.getInventory());
        doThrow(new IllegalStateException("Build failed")).when(firstTestWindow).build();
        firstTestWindow.render();
        secondTestWindow.open();

        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeLast());
        this.synchronousRunnableDeque.removeFirst().run();

        assertThrows(
                IllegalStateException.class,
                () -> this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst())
        );
        this.synchronousRunnableDeque.removeFirst().run();

        assertSame(secondTestWindow.getInventory(), this.player.getOpenInventory().getTopInventory());
    }

    @Test
    void aFullRenderDropsAnotherRenderUntilApplyCompletes() {
        final TestWindow testWindow = this.window();
        testWindow.render();
        testWindow.render();

        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());

        verify(testWindow).build();
        assertTrue(this.asynchronousRunnableDeque.isEmpty());
        assertTrue(testWindow.isRendering());

        this.synchronousRunnableDeque.removeFirst().run();

        assertFalse(testWindow.isRendering());
    }

    @Test
    void closeDoesNothingForAnOfflineViewer() {
        final TestWindow testWindow = this.window();
        this.openInventory(testWindow.getInventory());
        this.utilServerMockedStatic.when(() -> UtilServer.getOnlinePlayerById(this.viewerId))
                .thenReturn(Optional.empty());

        testWindow.close();

        verify(this.player, never()).closeInventory();
        verify(this.player, never()).closeInventory(any(InventoryCloseEvent.Reason.class));
        assertTrue(this.synchronousRunnableDeque.isEmpty());
        assertTrue(this.delayedRunnableDeque.isEmpty());
    }

    @Test
    void anElementRenderBuildsOffThreadAndRefreshesOnlyItsSlot() {
        final TestWindow testWindow = this.window();
        final Element<?> element = mock(Element.class);
        final ItemStack itemStack = mock(ItemStack.class);
        when(element.toItemStack()).thenReturn(itemStack);
        testWindow.getLayout().set(4, element);

        testWindow.renderElement(element);

        verify(element, never()).build();
        assertFalse(testWindow.isRendering());

        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());

        verify(element).build();
        verify(testWindow.getInventory(), never()).setItem(anyInt(), any(ItemStack.class));

        this.synchronousRunnableDeque.removeFirst().run();

        verify(testWindow.getInventory()).setItem(4, itemStack);
        verify(testWindow.getInventory(), never()).clear();
        verify(testWindow, never()).build();
    }

    @Test
    void aFullRenderSuppressesAnElementRefreshThatWasAlreadyBuilding() {
        final TestWindow testWindow = this.window();
        final Element<?> element = mock(Element.class);
        testWindow.getLayout().set(4, element);
        testWindow.renderElement(element);
        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());

        testWindow.render();
        this.synchronousRunnableDeque.removeFirst().run();

        verify(element, never()).toItemStack();
        assertTrue(testWindow.isRendering());

        this.runAsynchronousTask(this.asynchronousRunnableDeque.removeFirst());
        this.synchronousRunnableDeque.removeFirst().run();

        assertFalse(testWindow.isRendering());
    }

    @Test
    void paginatedContentSupportsElementRefreshAndRetainsUnreplacedSlots() {
        final PaginatedLayout paginatedLayout = PaginatedLayout.of(9, new int[]{3, 4});
        final TestWindow testWindow = this.window(paginatedLayout, () -> {});
        final Element<?> firstElement = mock(Element.class);
        final Element<?> secondElement = mock(Element.class);
        paginatedLayout.setContent(List.of(firstElement, secondElement));

        final Element<?> replacementElement = mock(Element.class);
        final ItemStack itemStack = mock(ItemStack.class);
        when(replacementElement.toItemStack()).thenReturn(itemStack);
        paginatedLayout.setContent(List.of(replacementElement));
        testWindow.refresh(replacementElement);

        assertSame(replacementElement, paginatedLayout.getElementBySlot(3).orElseThrow());
        assertSame(secondElement, paginatedLayout.getElementBySlot(4).orElseThrow());
        verify(testWindow.getInventory()).setItem(3, itemStack);
        verify(testWindow.getInventory(), never()).clear();

        paginatedLayout.clear();

        assertTrue(paginatedLayout.getElementBySlot(3).isEmpty());
        assertTrue(paginatedLayout.getSlotByElement(replacementElement).isEmpty());
    }

    private Inventory inventory() {
        final Inventory inventory = mock(Inventory.class);
        when(inventory.getType()).thenReturn(InventoryType.CHEST);

        return inventory;
    }

    private InventoryView inventoryView(final Inventory inventory) {
        final InventoryView inventoryView = mock(InventoryView.class);
        when(inventoryView.getPlayer()).thenReturn(this.player);
        when(inventoryView.getTopInventory()).thenReturn(inventory);
        when(inventoryView.getType()).thenAnswer(_ -> inventory.getType());

        return inventoryView;
    }

    private TestWindow window() {
        return this.window(Layout.of(9), () -> {});
    }

    private TestWindow window(final Layout layout, final Runnable registerRunnable) {
        final Inventory inventory = this.inventory();
        final TestWindow testWindow = spy(new TestWindow(this.player, layout, inventory, registerRunnable));
        when(inventory.getHolder()).thenReturn(testWindow);
        testWindow.getInventory();

        return testWindow;
    }

    private void openInventory(final Inventory inventory) {
        this.windowCloseListener.onInventoryClose(new InventoryCloseEvent(
                this.inventoryViewAtomicReference.get(), InventoryCloseEvent.Reason.OPEN_NEW
        ));

        final InventoryView inventoryView = this.inventoryView(inventory);
        this.inventoryViewAtomicReference.set(inventoryView);
        this.windowOpenListener.onInventoryOpen(new InventoryOpenEvent(inventoryView));
    }

    private void runDelayedTasks() {
        final List<Runnable> runnableList = List.copyOf(this.delayedRunnableDeque);
        this.delayedRunnableDeque.clear();
        runnableList.forEach(Runnable::run);
    }

    private void runAsynchronousTask(final Runnable runnable) {
        this.primaryThreadAtomicBoolean.set(false);

        try {
            runnable.run();
        } finally {
            this.primaryThreadAtomicBoolean.set(true);
        }
    }

    @NoArgsConstructor
    @Application
    public static final class TestApplication {

    }

    private static final class TestWindow extends Window<Layout> {

        private final Inventory inventory;

        private final Runnable registerRunnable;

        private TestWindow(
                final Player player,
                final Layout layout,
                final Inventory inventory,
                final Runnable registerRunnable
        ) {
            super(layout, player);

            this.inventory = inventory;
            this.registerRunnable = registerRunnable;
        }

        @Override
        protected Inventory createInventory() {
            return this.inventory;
        }

        @Override
        protected Component getTitle() {
            return Component.empty();
        }

        @Override
        protected void registerElements(final Layout layout) {
            this.registerRunnable.run();
        }
    }
}