<h1 align="center">Minecraft-Paper-Addon-Window</h1>

<p align="center">Per-player inventory menus with layout-driven slot placement, asynchronous element building, pagination, and click routing.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-API-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Window addon defines inventory menus as classes. A window owns one viewer and one layout; the layout owns slot placement, so a window supplies content without deciding where it lands. Element building runs off the main thread, which means a window can read from a database or a remote service while it opens, and only the item writes return to the main thread. The addon handles the inventory holder wiring, click routing, cancellation of every item movement, open and close gating, and cleanup on quit.

## Contents

<details>
<summary>On this page</summary>

- [Window definition](#window-definition)
- [Window types](#window-types)
- [Layout](#layout)
- [Paginated layout](#paginated-layout)
- [Elements](#elements)
- [Buttons](#buttons)
- [Lifecycle](#lifecycle)
- [Asynchronous rendering](#asynchronous-rendering)
- [Refreshing](#refreshing)
- [Click handling](#click-handling)
- [Persistent data stamping](#persistent-data-stamping)
- [Sub windows](#sub-windows)
- [Events](#events)
- [Window service](#window-service)
- [Installation](#installation)

</details>

## Window definition

`Window<Layout>` is the abstract base. It is both the menu definition and its own `InventoryHolder`, which is how a click resolves back to the window with no lookup map. The generic parameter is the layout type, so a window built on `PaginatedLayout` receives that type in `registerElements` without casting.

One instance belongs to one viewer. Construct it when you want to show it; a parent window can also be retained and reopened from a child window.

The constructor takes `(layout, viewer)`. Create a plain layout with `Layout.of(size)` or a paginated layout with `PaginatedLayout.of(size, contentSlots)`. Windows are constructed per viewer and are not singletons. Examples omit imports and use illustrative application types such as `OrderRepository`, `OrderElement`, and the confirmation buttons.

```java
public final class OrderWindow extends ChestWindow<Layout> {

    private final OrderRepository orderRepository;
    private final String orderId;

    public OrderWindow(final Player viewer, final OrderRepository orderRepository, final String orderId) {
        super(Layout.of(27), viewer);

        this.orderRepository = orderRepository;
        this.orderId = orderId;
    }

    @Override
    protected Component getTitle() {
        return BaseUtilMessage.deserialize("<gold>Order %s".formatted(this.orderId));
    }

    @Override
    protected void registerElements(final Layout layout) {
        final Order order = this.orderRepository.findOrderById(this.orderId);

        layout.border(new FillerElement(this));

        layout.set(13, new OrderElement(this, order));
    }
}
```

| Value | Example | Purpose |
| --- | --- | --- |
| `getViewerId()` | `f7c1e0a4-...` | The viewer's UUID, stored instead of a `Player` field |
| `getViewer()` | `Optional<Player>` | The viewer resolved from that UUID, empty once they disconnect |
| `getLayout()` | `Layout` | The layout supplied to the constructor, typed to the generic parameter |
| `getInventory()` | `Inventory` | The backing inventory, created lazily on first access and never null |

The base constructor rejects a layout whose size is zero or negative. Window types add their own size rules on top.

### Overridable methods

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `getTitle()` | `Component` | _abstract_ | Protected method; inventory title, read once when the inventory is created |
| `registerElements(layout)` | `void` | _abstract_ | Populate the layout; runs off the main thread |
| `createInventory()` | `Inventory` | _abstract_ | Implemented as final by the supplied window types |
| `canOpen(player)` | `boolean` | `true` | Pre-open check |
| `canClose(player)` | `boolean` | `true` | Check for player and API closes; `false` schedules a reopen |
| `onOpen(player)` | `void` | no-op | Called from an uncancelled Bukkit open event; tracked reopens skip it |
| `onClose(player)` | `void` | no-op | Called for an accepted close, including forced closes |

`getTitle()` is evaluated when the inventory is built, so a title cannot change on a later render. Build a new window to change it.

## Window types

A window type owns the inventory shape and validates the layout size against it. The type is the class you extend; the layout type stays a generic parameter, so each type works with any layout.

| Class | Inventory | Size rule |
| --- | --- | --- |
| `ChestWindow<Layout>` | Sized chest | Multiple of `9`, at most `54` |
| `DispenserWindow<Layout>` | Dispenser | Exactly `9` |

```java
public final class InvoiceWindow extends DispenserWindow<Layout> {

    public InvoiceWindow(final Player viewer) {
        super(Layout.of(9), viewer);
    }

    @Override
    protected Component getTitle() {
        return BaseUtilMessage.deserialize("<gold>Invoice");
    }

    @Override
    protected void registerElements(final Layout layout) {
        layout.fill(new FillerElement(this));
    }
}
```

A mismatch between the layout size and the inventory shape throws from the constructor rather than failing later when the items are written, so an invalid menu never reaches a viewer.

## Layout

`Layout` is the slot map. It holds the size, the placed elements, and the placement helpers. Elements do not store their own slot; the layout decides where each supplied element appears.

Create it with `Layout.of(size)`. The constructor is protected for layout subclasses.

| Method | Returns | Purpose |
| --- | --- | --- |
| `of(size)` | `Layout` | Create a layout with the given slot count |
| `getSize()` | `int` | Slot count supplied to the factory |
| `set(slot, element)` | `void` | Place one element; out-of-range slots are ignored |
| `fill(element)` | `void` | Place the element in every slot that is still empty |
| `border(element)` | `void` | Place the element around the edge of a layout with nine columns |
| `row(row, element)` | `void` | Place the element across one zero-based row of nine slots |
| `column(column, element)` | `void` | Place the element down one zero-based column, from `0` through `8` |
| `clear()` | `void` | Drop every static placement; subclasses can extend the cleanup |
| `getElementBySlot(slot)` | `Optional<Element<?>>` | Resolve the element at a slot |
| `applyTo(inventory)` | `void` | Clear the inventory and write every element's item stack |

`fill` skips occupied slots, so calling it after `border` and `set` leaves the earlier placements intact. `set` and `row` ignore slots outside the layout; `column` ignores column numbers outside `0` through `8` and visits each complete row.

`border`, `row`, and `column` assume nine columns. For a dispenser's 3×3 shape, use `set` to place individual slots or `fill` to fill empty slots.

The element map is a `ConcurrentHashMap`. Element registration writes to it off the main thread; inventory rendering and click routing read it on the main thread.

## Paginated layout

`PaginatedLayout` adds a content region on top of the static placements. Create it with `PaginatedLayout.of(size, contentSlots)`. Supply the total matching item count with `setTotal(total)` and load only the current page into `setContent(content)`, using `getOffset()` and `getLimit()` for the query.

The example repository counts orders for the viewer and accepts `(viewerId, offset, limit)` when fetching them. Call `setTotal` before checking navigation or reading the offset: it updates the page count and clamps the current page when the total shrinks.

```java
public final class OrderHistoryWindow extends ChestWindow<PaginatedLayout> {

    private static final int[] CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final OrderRepository orderRepository;

    public OrderHistoryWindow(final Player viewer, final OrderRepository orderRepository) {
        super(PaginatedLayout.of(27, CONTENT_SLOTS), viewer);

        this.orderRepository = orderRepository;
    }

    @Override
    protected Component getTitle() {
        return BaseUtilMessage.deserialize("<gold>Order History");
    }

    @Override
    protected void registerElements(final PaginatedLayout layout) {
        layout.setTotal(this.orderRepository.countOrdersByCustomer(this.getViewerId()));

        layout.border(new FillerElement(this));

        if (layout.hasPreviousPage()) {
            layout.set(18, new PreviousPageButton(this));
        }

        if (layout.hasNextPage()) {
            layout.set(26, new NextPageButton(this));
        }

        final List<Element<?>> orderElementList = this.orderRepository
                .findOrdersByCustomer(this.getViewerId(), layout.getOffset(), layout.getLimit())
                .stream()
                .map(order -> new OrderElement(this, order))
                .collect(Collectors.toCollection(ArrayList::new));

        layout.setContent(orderElementList);
    }
}
```

| Method | Returns | Purpose |
| --- | --- | --- |
| `of(size, contentSlots)` | `PaginatedLayout` | Create a paginated layout with the given content slots |
| `getContentSlots()` | `int[]` | Slots the content flows into, in order |
| `getContent()` | `List<Element<?>>` | Elements loaded for the current page |
| `setContent(content)` | `void` | Replace the current page's elements without changing the total or page |
| `getTotal()` | `int` | Total matching item count across all pages; initially `0` |
| `setTotal(total)` | `void` | Clamp the total to at least `0` and the current page to the available range |
| `getPage()` | `int` | Current page, zero based |
| `setPage(page)` | `void` | Jump to a page, clamped to the available range |
| `getOffset()` | `int` | Query offset: `page * contentSlots.length` |
| `getLimit()` | `int` | Maximum page size: `contentSlots.length` |
| `getPageCount()` | `int` | Page count calculated from the total and limit, at least `1` |
| `hasNextPage()` | `boolean` | Whether a later page exists, without changing the current page |
| `hasPreviousPage()` | `boolean` | Whether the current page is greater than `0`, without changing it |
| `nextPage()` | `boolean` | Advance a page; `false` when already on the last |
| `previousPage()` | `boolean` | Go back a page; `false` when already on the first |
| `clear()` | `void` | Drop static placements and replace the content with an empty list; retain the total and page |

The factory rejects an empty content-slot array and any slot that falls outside the layout size. Supply distinct content slots so rendering and click resolution agree.

`setContent` stores the current page's list directly. Rendering and click resolution use its indices starting at `0`, with no page offset applied to the list. Supply at most `getLimit()` elements; entries beyond the content-slot count are not displayed. Loading content does not update the total, so call `setTotal` explicitly even when the first page contains all results.

`clear()` removes static placements and resets the loaded content to an empty list. The total and page stay unchanged. Each render starts with an empty content list; `registerElements` supplies the new page's results.

Content is written after the static placements, so an element placed in a content slot shows through wherever the current page runs short. A filler behind the content region fills the gaps on the last page without any extra work. The same rule applies to click resolution: a content slot past the end of the list resolves to whatever static element sits underneath it.

Changing the page updates the query offset without loading content. After `nextPage()`, `previousPage()`, or `setPage(page)`, call `render(null)` to fetch and render that page. `refresh()` redraws the already loaded list, so it does not load a different page.

The click listener ignores button clicks while the window is rendering, so navigation buttons do not need their own rendering check. A previous-page button uses `previousPage()` followed by `render(null)` in the same way as the next-page button below.

```java
public final class NextPageButton extends Button<OrderHistoryWindow> {

    public NextPageButton(final OrderHistoryWindow window) {
        super(window, ItemStack.of(Material.ARROW));
    }

    @Override
    public List<String> getDescription() {
        final PaginatedLayout paginatedLayout = this.getWindow().getLayout();

        return List.of("<gray>Page %d of %d".formatted(
                paginatedLayout.getPage() + 1,
                paginatedLayout.getPageCount()
        ));
    }

    @Override
    public void onClick(final Player player, final ClickType clickType) {
        if (!this.getWindow().getLayout().nextPage()) {
            return;
        }

        this.getWindow().render(null);
    }
}
```

## Elements

`Element<Window>` is one item stack in a menu. It is typed to the window that owns it, so a definition reaches its window's state without a cast. An element is not clickable; that is what `Button` adds, which keeps decorative items from needing an empty click handler.

The constructor takes `(window, itemStack)`.

```java
public final class FillerElement extends Element<Window<?>> {

    public FillerElement(final Window<?> window) {
        super(window, ItemStack.of(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public List<String> getDescription() {
        return null;
    }
}
```

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `getDescription()` | `List<String>` | _abstract_ | Lines appended to the item's lore; `null` or an empty list leaves existing lore unchanged |
| `getWindow()` | `Window` | n/a | The owning window, typed to the generic parameter |
| `getItemStack()` | `ItemStack` | n/a | The base stack supplied to the constructor |
| `toItemStack()` | `ItemStack` | n/a | Clone the base stack, stamp it, and apply the description |

`toItemStack()` is called on the main thread during every render, which is where the description is resolved. That makes `getDescription()` the hook for anything that changes between renders, such as a page counter or a live total. A non-empty description is appended to the base stack's lore, with a blank line separating it from any existing lore. A `null` or empty description leaves that lore unchanged and adds no separator. Description lines are deserialized, default to white, and have italics turned off.

The base stack is cloned on every call, so an element never mutates the stack it was constructed with and the same stack can back several elements.

## Buttons

`Button<Window>` extends `Element<Window>` with a click handler and an optional check.

```java
public final class CancelOrderButton extends Button<OrderWindow> {

    public CancelOrderButton(final OrderWindow window) {
        super(window, ItemStack.of(Material.BARRIER));
    }

    @Override
    public List<String> getDescription() {
        return List.of("<red>Cancel this order.");
    }

    @Override
    public boolean canClick(final Player player, final ClickType clickType) {
        return clickType == ClickType.LEFT;
    }

    @Override
    public void onClick(final Player player, final ClickType clickType) {
        UtilMessage.message(player, "Order", "Cancelled.");

        this.getWindow().close();
    }
}
```

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `onClick(player, clickType)` | `void` | _abstract_ | Called when the button is clicked |
| `canClick(player, clickType)` | `boolean` | `true` | Pre-click check |

Button callbacks run inside `InventoryClickEvent`. Call `open()` and `close()` directly: the Window API marshals these calls to the main thread and defers the operation until the next tick. Button classes do not need scheduler calls.

## Lifecycle

| Method | Behaviour |
| --- | --- |
| `open()` | Queue an open for the next tick, check the viewer and open gates, render, and show only if its request is still current |
| `close()` | Cancel an unfinished open for this window and queue a close for the next tick; close only if this window is still on top |
| `render(runnable)` | Rebuild every element off-thread, write them on the main thread, then run the callback |
| `refresh()` | Rewrite the current elements immediately with no rebuild; skipped while rendering; call on the main thread |

`open()` does nothing when the viewer is offline or this instance is already rendering. Otherwise it creates a unique request for that viewer and schedules the open checks for the next tick. Cancelling `WindowOpenEvent` or returning `false` from `canOpen` prevents the build. Items are applied before the inventory is shown, and the viewer and request are checked again before opening it.

A newer open or close request invalidates the viewer's previous request. Opening or closing another inventory through Bukkit also invalidates pending requests, including when the viewer returns to the same crafting inventory afterward. Completing an older request cannot consume a newer one. Disconnect cleanup and `closeAll()` invalidate pending requests too.

`close()` accepts a request only if this window is currently visible or owns the viewer's pending request. It invalidates the previous request immediately on the main thread, then checks its request and the current inventory again next tick. A stale reference cannot close a replacement inventory, and calling `close()` before an unfinished open completes prevents that open from appearing. A normal API close still passes through `canClose`.

A close removes the window from the active tracking maps before any veto check. Only Bukkit close reasons `PLAYER` and `PLUGIN` dispatch `WindowCloseEvent` and check `canClose`. Cancelling that event or returning `false` schedules a reopen one tick later and skips `onClose` for that close. Other reasons, including replacement, disconnect, death, and `CANT_USE`, bypass both checks and call `onClose`.

The delayed reopen checks both its unique request ID and the pending inventory. It requires an online, living viewer whose current view is the default `CRAFTING` view. A newer request or another inventory open or close invalidates it, so opening and closing a newer menu cannot revive an old reopen. A matching reopen marker suppresses `onOpen`. Opening a replacement inventory closes the previous window without a veto.

The open listener handles uncancelled Bukkit open events at `MONITOR` priority, registers the window in both active maps, and then calls `onOpen` unless it is a tracked reopen. Internal inventory transitions preserve their expected request, including a newer request made by a lifecycle callback.

## Asynchronous rendering

`registerElements` runs off the main thread on every render. Blocking calls belong there, which is the point: a window can query a database, read a cache, or fetch a profile while it opens, with no scheduling code in the definition.

`open()` and `close()` handle main-thread scheduling internally and can be called directly from button handlers or asynchronous code. Call `render()`, `refresh()`, and `getInventory()` on the main thread. `render()` schedules `registerElements` asynchronously, then schedules `applyTo` and its callback on the main thread. `toItemStack()` runs inside `applyTo`; keep any Bukkit operations in element constructors or `registerElements` limited to APIs that support asynchronous use.

```java
@Override
protected void registerElements(final Layout layout) {
    final Customer customer = this.customerRepository.findCustomerByName("alice");

    layout.set(13, new CustomerElement(this, customer));
}
```

A render in flight blocks another from starting on the same window instance. A render requested while one is running is dropped. Separate instances can render concurrently; only the viewer's current open request can show its result.

If `registerElements` throws a `RuntimeException` or `Error`, the normal apply and callback steps are skipped and an abort is scheduled on the main thread. Exceptions while applying items or running the callback also abort the render. The abort clears the layout, invalidates this window's current request, and schedules a forced close for the next tick with reason `CANT_USE`. That close affects only this window if it is still visible. Click routing remains blocked until cleanup finishes, and the original exception is rethrown for the scheduler to report.

Treat `registerElements` as a rebuild from an empty layout. Static placements are cleared before it runs, and `PaginatedLayout.clear()` also clears loaded content while retaining the page and total. The previous items can remain visible during a rebuild, but button clicks are ignored while `isRendering()` is `true`.

On a successful render, the rendering flag stays set until after the callback returns. Calls to `render()` or `refresh()` from that callback are therefore skipped, including from `onOpen` invoked while `open()` shows the inventory. Calling `close()` or opening another window from a lifecycle callback queues the operation safely for the next tick.

## Refreshing

`refresh()` rewrites the current elements without rebuilding them. Call it on the main thread: it applies the layout immediately without scheduling. It does not call `registerElements`, but it does call `toItemStack()` again, including each element's `getDescription()`.

Use `refresh()` when the item stacks should change but the element set should not, such as a counter in a description or a toggle whose state lives on the element. Use `render(null)` when the element set itself has to be rebuilt, including page navigation, a list that gained an entry, or a slot that became available.

`refresh()` is a no-op while rendering or until the backing inventory has been created. Calling `getInventory()` creates it even before the first `open()`. If item rendering fails, it uses the same deferred abort cleanup as `render()` and rethrows the exception to the caller.

## Click handling

Every click inside a window is cancelled before anything else happens, so no item can be taken, inserted, or swapped regardless of what the clicked slot holds.

| Interaction | Result |
| --- | --- |
| Click on a window slot holding a button while not rendering | Cancelled, then routed to the button |
| Click on a window slot while rendering | Cancelled; no button action |
| Click on any other window slot | Cancelled |
| Click in the viewer's own inventory while a window is open | Cancelled |
| Shift-click, hotbar swap, offhand swap, double-click collect | Cancelled |
| Drag across a window | Cancelled |
| Creative slot set | Cancelled, and the viewer's inventory is resynchronized |

Only clicks on the window itself resolve to a button, so a slot number that exists in both the menu and the viewer's inventory cannot trigger a button from the wrong one.

While `window.isRendering()` is `true`, the click is still cancelled, but the listener returns before resolving an element. No `ButtonPreClickEvent`, `canClick`, `onClick`, or `ButtonPostClickEvent` runs for that click.

A routed click runs `ButtonPreClickEvent`, then `canClick`, then `onClick`, then `ButtonPostClickEvent`. A cancelled pre-click event or a `canClick` returning `false` stops it, and the post-click event does not fire. That split is the hook for cross-cutting rules: check a cooldown on the pre event and stamp it on the post event, and no button needs to know about it.

Click routing runs at `HIGH` priority and respects an earlier cancellation, so a plugin that cancels the click first suppresses the button.

## Persistent data stamping

| Key | Type | Purpose |
| --- | --- | --- |
| `custom:element_item` | `Boolean` | Written to every item stack a window renders |

The key is public as `Element.ELEMENT_KEY`. Every item an element produces carries it, which gives a consuming plugin a reliable way to recognize a menu item that has escaped into the world or an inventory. It is a marker only; the window and element a stack came from are not recorded.

## Sub windows

A sub window is a separate window that holds its parent, opened from a click. Nothing on the base class is involved.

```java
@Getter
public final class ConfirmWindow extends ChestWindow<Layout> {

    private final Window<?> parent;

    public ConfirmWindow(final Player viewer, final Window<?> parent) {
        super(Layout.of(27), viewer);

        this.parent = parent;
    }

    @Override
    protected Component getTitle() {
        return BaseUtilMessage.deserialize("<red>Confirm");
    }

    @Override
    protected void registerElements(final Layout layout) {
        layout.set(11, new ConfirmButton(this));
        layout.set(15, new BackButton(this));
    }
}
```

```java
@Override
public void onClick(final Player player, final ClickType clickType) {
    new ConfirmWindow(player, this.getWindow()).open();
}
```

A back button is the same call in reverse:

```java
@Override
public void onClick(final Player player, final ClickType clickType) {
    this.getWindow().getParent().open();
}
```

Opening and closing from inside a click are safe through the Window API: both methods defer the inventory operation internally. The parent instance stays alive because the child holds it, and reopening it runs `registerElements` again, so it comes back with current data.

## Events

Pre events are cancellable, post events are informational.

| Event | Cancellable | Data |
| --- | --- | --- |
| `WindowOpenEvent` | Yes | Window, player |
| `WindowCloseEvent` | Yes | Window, player |
| `ButtonPreClickEvent` | Yes | Window, button, player, click type |
| `ButtonPostClickEvent` | No | Window, button, player, click type |

`WindowOpenEvent` is dispatched by `open()` before the render begins, so cancelling it avoids the element build entirely. `WindowCloseEvent` is dispatched only for `PLAYER` and `PLUGIN` closes. Cancelling it schedules the same guarded reopen as a `canClose` rejection. Forced closes and inventory replacements do not dispatch this custom event.

Each pre event runs before the window's or button's own check, so a cancelled event stops the operation regardless of what the definition would have allowed.

## Window service

Inject `WindowService` to resolve open windows:

| Method | Returns | Behaviour |
| --- | --- | --- |
| `getWindowByPlayerMap()` | `Map<UUID, Window<?>>` | The window each player currently has open |
| `getWindowByInventoryMap()` | `Map<Inventory, Window<?>>` | The window backing each open inventory |
| `getReopeningMap()` | `Map<UUID, Inventory>` | Internal: inventories pending a reopen after a vetoed close |
| `closeAll()` | `void` | Invalidate pending requests, force-close tracked windows with `CANT_USE`, and clear the tracking maps |
| `cleanup(player)` | `void` | Drop a player's entries, called on quit |

A window is also reachable from its inventory without the service, since `Inventory.getHolder()` returns the window itself.

Use `closeAll()` on the main thread in a consuming plugin's shutdown path. It invalidates open, close, and reopen requests before closing tracked windows with reason `CANT_USE`, which bypasses close vetoes. New Window API requests are ignored while it runs. Builds already in flight can finish, but their invalidated open requests cannot show an inventory afterward.

Cleanup runs on `PlayerQuitEvent` at `MONITOR` priority. It removes the player's current window entry, that window's inventory entry, and all pending request markers. The close listener also removes active entries immediately when an inventory closes, including when that close is vetoed.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Window</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Declare `WindowAddon` on your `PaperPlugin` with `@Addons({WindowAddon.class})`. Window definitions belong under the plugin's package and are constructed per viewer rather than registered; the framework registers the addon's internal service and listeners.

Does not require Paper NMS. Return to the [Addons README](../README.md).
