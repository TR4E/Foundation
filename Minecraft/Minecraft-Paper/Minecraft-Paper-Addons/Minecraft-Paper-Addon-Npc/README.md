<h1 align="center">Minecraft-Paper-Addon-Npc</h1>

<p align="center">Code-defined NPCs with capability interfaces, chunk-aware unloading, state snapshots, and delayed respawns.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-API-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Npc addon defines NPCs as classes rather than configuration. A definition declares the entity type it spawns and opts into capabilities through interfaces; the addon handles spawning, protection from damage and vanilla despawning, click handling, chunk unloading, state restoration, and delayed respawns. NPC entities are never written to chunk data, so the addon owns their lifecycle instead of the world file. Spawn locations come from the consuming plugin, so nothing persists across a restart.

## Contents

<details>
<summary>On this page</summary>

- [NPC definition](#npc-definition)
- [Entity records](#entity-records)
- [Spawn defaults](#spawn-defaults)
- [Capabilities](#capabilities)
- [Nameable NPC](#nameable-npc)
- [Interactable NPC](#interactable-npc)
- [Damageable NPC](#damageable-npc)
- [Respawnable NPC](#respawnable-npc)
- [Equippable NPC](#equippable-npc)
- [Looking NPC](#looking-npc)
- [Movable NPC](#movable-npc)
- [Lifecycle](#lifecycle)
- [Persistent data stamping](#persistent-data-stamping)
- [Snapshots](#snapshots)
- [Custom snapshots](#custom-snapshots)
- [Goal restoration](#goal-restoration)
- [Events](#events)
- [NPC service](#npc-service)
- [NPC search](#npc-search)
- [Installation](#installation)

</details>

## NPC definition

`Npc<Entity>` is the abstract base. It is both the definition and the holder: one instance per kind of NPC, typed to the Bukkit entity it spawns, owning every live entity of that kind.

The constructor takes `(entityClass, identifier)`. Examples omit imports and use Injector's `@Singleton` for plugin components.

```java
@Singleton
public final class GreeterNpc extends Npc<Villager> {

    public GreeterNpc() {
        super(Villager.class, "GREETER");
    }
}
```

| Value | Example | Purpose |
| --- | --- | --- |
| `getEntityClass()` | `Villager.class` | The entity type spawned, and the generic parameter every hook is typed to |
| `getIdentifier()` | `greeter` | Stable identity written to every spawned entity and used for lookup and search |

The identifier must be unique across definitions. It is stamped onto each entity under `Npc.NPC_KEY`, which is how `NpcService.getNpcByEntity(...)` resolves an arbitrary entity back to its definition in a single persistent-data read. When two definitions share an identifier, the last one in the live list wins.

Spawning is explicit and takes a location:

```java
npc.spawn(location);
```

### Overridable methods

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `onSetup(entity)` | `void` | no-op | Configure the entity before it enters the world; entity-type specifics belong here |
| `onSpawn(entity)` | `void` | no-op | Called after a spawn or a restore, once the entity is tracked |
| `onDespawn(entity)` | `void` | no-op | Called before a deliberate despawn removes the entity |
| `canSpawn(location)` | `boolean` | `true` | Vetoes a spawn and a restore |
| `canDespawn(entity)` | `boolean` | `true` | Vetoes a despawn |
| `createSnapshot(livingEntity)` | `NpcSnapshot` | `new NpcSnapshot(...)` | Supplies a custom snapshot type |

### Accessors

| Method | Returns | Behaviour |
| --- | --- | --- |
| `getEntities()` | `List<Entity>` | Snapshot copy of every live entity of this definition |
| `getEntityById(id)` | `Optional<Entity>` | Resolve a live entity by its UUID |
| `getNpcEntityById(id)` | `Optional<NpcEntity<Entity>>` | Resolve the record rather than the entity |

## Entity records

`NpcEntity<Entity>` is the record for one spawned entity. It survives a chunk unload; the Bukkit entity inside it does not.

| Field | Purpose |
| --- | --- |
| `entity` | The current Bukkit entity, replaced on each restore |
| `spawnedAtLocation` | The anchor a restore spawns at |
| `lastKnownLocation` | Where the entity stood when it unloaded, used to match the chunk that brings it back |
| `snapshot` | Runtime state captured at unload, cleared once reapplied |

For a `MovableNpc`, a restore promotes `lastKnownLocation` to `spawnedAtLocation`, so a patrolling NPC resumes from its drift point rather than teleporting back to the start of its route.

## Spawn defaults

Every entity the addon creates is configured inside the spawn consumer, before it enters the world:

| Default | Reason |
| --- | --- |
| `setPersistent(false)` | The addon owns the lifecycle; the entity is never saved to chunk data |
| `setGravity(false)` | A stationary NPC does not drift or fall |
| `setInvulnerable(true)` | Damage, knockback and environmental effects are ignored |
| `setCollidable(false)` | Players cannot push the NPC around |
| `setRemoveWhenFarAway(false)` | Vanilla never despawns it |
| `removeAllGoals(mob)` | No vanilla AI, so the NPC never wanders or targets anything |

Capability interfaces then override whichever defaults they own: `DamageableNpc` turns invulnerability off and collision on, and `MovableNpc` restores gravity.

## Capabilities

Behaviour is added by implementing interfaces. Every method is defaulted except where noted, so a definition overrides only what it needs.

| Interface | Adds | Requires |
| --- | --- | --- |
| `NameableNpc` | Display name and visibility | Any entity |
| `InteractableNpc<Entity>` | Left and right click handling | Any entity |
| `DamageableNpc` | Health, attackability | `LivingEntity` |
| `RespawnableNpc` | Delayed respawn after death | `DamageableNpc` to be reachable |
| `EquippableNpc` | Held items and armour | `LivingEntity` with equipment |
| `LookingNpc` | Head tracking of nearby entities | `Mob` |
| `MovableNpc` | Waypoint pathing | `Mob` |

`LookingNpc` and `MovableNpc` are built on Paper's goal API, so a definition spawning an `ArmorStand` or a display entity silently gets neither.

Without `DamageableNpc`, `NpcDamageListener` cancels all damage, combustion, and harmful potion effects for that NPC at `LOW` priority. Harmful is read from `PotionEffectTypeCategory`.

## Nameable NPC

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `getName()` | `Component` | _abstract_ | Display name applied as the entity's custom name |
| `isNameAlwaysVisible()` | `boolean` | `true` | `true` renders the name constantly, `false` falls back to vanilla look-at behaviour |

The name is reapplied by `setup` on every spawn and restore, so it is rebuilt from the definition rather than carried in a snapshot.

## Interactable NPC

`InteractableNpc<Entity>` is typed to the same entity as the definition, so the handler receives the concrete type.

```java
@Singleton
public final class ShopKeeperNpc extends Npc<Villager> implements NameableNpc, InteractableNpc<Villager>, LookingNpc {

    public ShopKeeperNpc() {
        super(Villager.class, "SHOP_KEEPER");
    }

    @Override
    public Component getName() {
        return UtilMessage.deserialize("<gold>Shopkeeper");
    }

    @Override
    public void onSetup(final Villager entity) {
        entity.setProfession(Villager.Profession.LIBRARIAN);
    }

    @Override
    public boolean canInteract(final Player player, final Villager entity, final NpcInteractType interactType) {
        return !player.isSneaking();
    }

    @Override
    public void onInteract(final Player player, final Villager entity, final NpcInteractType interactType) {
        if (interactType != NpcInteractType.RIGHT_CLICK) {
            return;
        }

        UtilMessage.message(player, "Shop", "Welcome, %s.".formatted(player.getName()));
    }
}
```

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `onInteract(player, entity, type)` | `void` | _abstract_ | Called when the NPC is clicked |
| `canInteract(player, entity, type)` | `boolean` | `true` | Pre-interaction check |

### Interaction types

| Type | Trigger | Bukkit event |
| --- | --- | --- |
| `LEFT_CLICK` | Player attacks the NPC | `PrePlayerAttackEntityEvent` |
| `RIGHT_CLICK` | Player right-clicks the NPC with the main hand | `PlayerInteractEntityEvent` |

Left click comes from `PrePlayerAttackEntityEvent` rather than a damage event, so it still fires while the NPC is invulnerable. Off-hand right clicks are ignored.

A handled interaction cancels the underlying event, which suppresses the vanilla result such as opening a villager trade screen. On a `DamageableNpc`, left click is treated as an attack and skips interaction entirely, so a damageable NPC remains hittable.

## Damageable NPC

```java
@Singleton
public final class BossNpc extends Npc<WitherSkeleton> implements NameableNpc, DamageableNpc, EquippableNpc, RespawnableNpc {

    public BossNpc() {
        super(WitherSkeleton.class, "BOSS");
    }

    @Override
    public Component getName() {
        return UtilMessage.deserialize("<dark_red><bold>Warden of the Keep");
    }

    @Override
    public double getMaxHealth() {
        return 400.0D;
    }

    @Override
    public double getDefaultHealth() {
        return 400.0D;
    }

    @Override
    public int getRespawnDelay() {
        return 5;
    }

    @Override
    public ChronoUnit getRespawnInterval() {
        return ChronoUnit.MINUTES;
    }

    @Override
    public ItemStack getItemInMainHand() {
        return ItemStack.of(Material.NETHERITE_SWORD);
    }

    @Override
    public ItemStack[] getArmourContents() {
        return new ItemStack[]{ItemStack.of(Material.NETHERITE_BOOTS), ItemStack.of(Material.NETHERITE_LEGGINGS), ItemStack.of(Material.NETHERITE_CHESTPLATE), ItemStack.of(Material.NETHERITE_HELMET)};
    }

    @Override
    public boolean canSpawn(final Location location) {
        return location.getWorld().isDayTime();
    }
}
```

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `getMaxHealth()` | `double` | _abstract_ | Base value applied to `Attribute.MAX_HEALTH` |
| `getDefaultHealth()` | `double` | _abstract_ | Starting health, clamped between `1.0` and the max |

`canSpawn` gates both a fresh spawn and a restore, so an NPC tied to an event stays gone once the event ends rather than reappearing on the next chunk load.

## Respawnable NPC

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `getRespawnDelay()` | `int` | `5` | Delay before the NPC returns |
| `getRespawnInterval()` | `ChronoUnit` | `SECONDS` | Unit the delay is measured in |

A death produces a fresh entity: the snapshot is cleared first, so the NPC comes back at its default health with default equipment, at `spawnedAtLocation` rather than where it fell. Without this interface a killed NPC is discarded and stays dead until the consuming plugin spawns it again.

Death is only reachable through `DamageableNpc`, since every other NPC is invulnerable.

## Equippable NPC

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `getItemInMainHand()` | `ItemStack` | `null` | Main-hand item |
| `getItemInOffHand()` | `ItemStack` | `null` | Off-hand item |
| `getArmourContents()` | `ItemStack[]` | empty array | Armour in Bukkit order: boots, leggings, chestplate, helmet |

These supply the starting loadout. Runtime changes, including armour durability lost to damage, are carried across an unload by the snapshot rather than rebuilt from these methods.

## Looking NPC

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `getLookingAtType()` | `Class<? extends LivingEntity>` | `Player.class` | Which entity type the NPC tracks |
| `getLookingDistance()` | `double` | `8.0` | Search radius |
| `getLookingProbability()` | `float` | `0.02` | Chance per tick of beginning a glance; `1.0` tracks constantly |
| `isLookingOnlyHorizontal()` | `boolean` | `false` | `true` keeps the head level instead of tilting |

Backed by `LookAtEntityGoal`, registered at priority `0`. It picks the nearest matching entity, skipping spectators, and holds the glance for 40 to 80 ticks. The `0.02` default reproduces vanilla's idle glance rather than deliberate tracking.

## Movable NPC

```java
@Singleton
public final class PatrolNpc extends Npc<Zombie> implements NameableNpc, MovableNpc {

    private final List<Location> pathLocations;

    public PatrolNpc(final List<Location> pathLocations) {
        super(Zombie.class, "PATROL");

        this.pathLocations = pathLocations;
    }

    @Override
    public Component getName() {
        return UtilMessage.deserialize("<gray>Guard");
    }

    @Override
    public List<Location> getPathLocations() {
        return this.pathLocations;
    }

    @Override
    public long getPathWaitTicks() {
        return 40L;
    }

    @Override
    public boolean isPathLooping() {
        return true;
    }
}
```

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `getPathLocations()` | `List<Location>` | empty list | Waypoints walked in order |
| `isPathLooping()` | `boolean` | `true` | `false` walks the route once and stops permanently |
| `getPathWaitTicks()` | `long` | `0L` | Ticks paused at each waypoint |
| `getMovementSpeed()` | `double` | `1.0` | Multiplier on the mob's movement attribute, not blocks per second |
| `hasGravity()` | `boolean` | `true` | Restores gravity, which the pathfinder needs to walk |

Backed by FollowPathGoal, registered at priority 1. A waypoint counts as reached within one block, and a waypoint in another world is unreachable, so it is skipped without consuming its wait. getPathLocations() is read once per spawn, so changing the list affects the next spawn rather than a live NPC.

With `isPathLooping()` false the goal marks itself finished and never reactivates, and that finished state survives an unload.

An NPC with no capabilities at all is the cheapest case, a frozen invulnerable prop:

```java
@Singleton
public final class StatueNpc extends Npc<ArmorStand> {

    public StatueNpc() {
        super(ArmorStand.class, "STATUE");
    }

    @Override
    public void onSetup(final ArmorStand entity) {
        entity.setInvisible(true);
    }
}
```

## Lifecycle

| Method | Behaviour |
| --- | --- |
| `spawn(location)` | Create an entity, track it, and fire the spawn events |
| `despawn(entityId)` | Remove an entity permanently, after the pre-despawn checks |
| `unload(entityId, location)` | Chunk unloaded: drop the entity, keep the record and a snapshot for later |
| `load(chunk)` | Chunk loaded: restore every record pending inside it |
| `respawn(entityId)` | Death path: clear the snapshot and restore a fresh entity |
| `discard(entityId)` | Drop a record whose entity is already gone, with no respawn |
| `purge(entity)` | Remove one entity and its record without events |
| `purgeAll()` | Clear pending records and purge every live entity |

`NpcCleanupListener` drives this from `EntityRemoveEvent` at `MONITOR` priority, branching on its cause:

| Cause | Path |
| --- | --- |
| `UNLOAD` | `unload`, keeping the NPC for a later chunk load |
| `DEATH` | `death`, which schedules a respawn or discards the record |
| Anything else | `discard` |

Restores run one tick after `ChunkLoadEvent` rather than inside it, because spawning an entity during chunk load is unsafe. `NpcService.onComponentShutdown` calls `purgeAll` on every definition.

## Persistent data stamping

| Key | Type | Purpose |
| --- | --- | --- |
| `custom:npc` | `String` | The definition's identifier, written to every spawned entity |

The key is public as `Npc.NPC_KEY`. A snapshot copies the entity's whole persistent data container, so plugin-written keys survive an unload alongside this one.

## Snapshots

When a chunk unloads, the addon captures the entity's runtime state so the NPC returns as it was rather than as a fresh copy. `NpcSnapshot` records `createdAt` at capture, and time-based values are advanced by however long the chunk stayed unloaded.

| Captured | Restored |
| --- | --- |
| Health and absorption amount | Health clamped to the current `MAX_HEALTH`, so raising or lowering the definition's max is respected |
| Fire, freeze, and no-damage ticks | Counted down by the elapsed time, floored at zero |
| Active potion effects | Durations reduced by the elapsed time, expired effects dropped, infinite effects passed through untouched |
| Remaining air, arrows in body, bee stingers in body | Restored as captured |
| Glowing, invisible, visual fire | Restored as captured; visual fire keeps its `TriState` so an explicitly extinguished entity stays extinguished |
| Equipment in six slots with per-slot drop chances | Restored including durability |
| Attribute modifiers | Reapplied by key; base values are deliberately not captured, so editing `getMaxHealth()` is not fought by an old snapshot |
| Scoreboard tags and persistent data container | Merged back onto the new entity |
| Attack target | Resolved by UUID, skipped when that entity is gone |
| Goal progress | Handed to the rebuilt goal through `RestorableGoal` |

The snapshot is applied after `setup` has run, so it overwrites the definition defaults rather than being overwritten by them. It is cleared once applied, and a death clears it before the restore so a respawn is clean.

Values that `setup` rebuilds from the definition, such as the display name and name visibility, are deliberately not captured.

| Class | Scope |
| --- | --- |
| `NpcSnapshot` | Entity state, and the owner of the three below |
| `NpcEquipmentSnapshot` | Items and drop chances for the six equipment slots |
| `NpcAttributeSnapshot` | Runtime attribute modifiers, discovered through `Registry.ATTRIBUTE` |
| `NpcGoalSnapshot` | Goals that implement `RestorableGoal` |

## Custom snapshots

To carry entity-type state across an unload, extend `NpcSnapshot` and return it from `createSnapshot`:

```java
public final class VillagerNpcSnapshot extends NpcSnapshot {

    private final Villager.Profession profession;
    private final int villagerLevel;

    public VillagerNpcSnapshot(final LivingEntity livingEntity) {
        super(livingEntity);

        final Villager villager = Villager.class.cast(livingEntity);

        this.profession = villager.getProfession();
        this.villagerLevel = villager.getVillagerLevel();
    }

    @Override
    public void apply(final LivingEntity livingEntity) {
        super.apply(livingEntity);

        final Villager villager = Villager.class.cast(livingEntity);

        villager.setProfession(this.profession);
        villager.setVillagerLevel(this.villagerLevel);
    }
}
```

```java
@Override
protected NpcSnapshot createSnapshot(final LivingEntity livingEntity) {
    return new VillagerNpcSnapshot(livingEntity);
}
```

Capture in the constructor and restore in `apply`, calling `super` first in both, so the two halves stay mirrored and the base state is in place before the subclass adds to it.

## Goal restoration

Goals are rebuilt by `setup` on every spawn, so a goal that should carry progress across an unload implements `RestorableGoal`, typed to itself, and copies what it needs from the previous instance:

```java
public final class FollowPathGoal implements Goal<Mob>, RestorableGoal<FollowPathGoal> {

    @Override
    public void restore(final FollowPathGoal followPathGoal) {
        this.index = followPathGoal.index;
        this.waited = followPathGoal.waited;
        this.finished = followPathGoal.finished;
    }
}
```

Because the goal restores from its own type, its fields stay private and no goal key needs exposing. `NpcGoalSnapshot` collects every opted-in goal from the old entity and hands each one to the goal of the same class on the new entity, so the snapshot layer never learns about individual goals.

`LookAtEntityGoal` does not opt in: its target and glance timer resolve themselves on the next activation roll.

## Events

Pre events are cancellable, post events are informational.

| Event | Cancellable | Data |
| --- | --- | --- |
| `NpcPreSpawnEvent` | Yes | NPC, spawn location |
| `NpcPostSpawnEvent` | No | NPC, entity |
| `NpcPreRestoreEvent` | Yes | NPC, restore location |
| `NpcPostRestoreEvent` | No | NPC, entity |
| `NpcPreDespawnEvent` | Yes | NPC, entity |
| `NpcPostDespawnEvent` | No | NPC, entity |
| `NpcUnloadEvent` | No | NPC, entity |
| `NpcPreInteractEvent` | Yes | NPC, player, entity, interaction type |
| `NpcPostInteractEvent` | No | NPC, player, entity, interaction type |

Restore events are separate from spawn events so a listener can tell a brand new NPC from a returning one. `NpcUnloadEvent` fires while the entity is still readable, which is the last chance to inspect it.

Pre events run before the matching `canSpawn`, `canDespawn`, or `canInteract` check, so a cancelled event stops the operation regardless of what the definition would have allowed. Death and respawn fire no addon events of their own; the Damage addon's death events cover that.

## NPC service

Inject `NpcService` to resolve definitions:

| Method | Returns | Behaviour |
| --- | --- | --- |
| `getNpcList()` | `List<Npc<?>>` | Injected read-only live list of definitions |
| `getNpcByIdentifier(identifier)` | `Optional<Npc<?>>` | Resolve through the identifier map; empty for an unknown identifier |
| `getNpcByEntity(entity)` | `Optional<Npc<?>>` | Resolve from the entity's stamped identifier in one persistent-data read |
| `searchNpc(commandSender, input, inform)` | `Optional<Npc<?>>` | Search identifiers with exact and partial matching and optional feedback |
| `searchNpc(commandSender, input, inform, predicate)` | `Optional<Npc<?>>` | The same search restricted by the predicate |

On live dependency updates the service rebuilds its identifier map from the live list. `getNpcByEntity` is cheap enough for event handlers, which is why the damage and interact listeners call it directly.

## NPC search

`NpcSearchEngine` extends the framework's [Paper search engine](../../Minecraft-Paper-Plugin-Framework/README.md#search-engines). `NpcService` keeps a reusable instance and exposes it through `searchNpc(...)`; each search reads the service's current list.

| Behaviour | Value |
| --- | --- |
| Exact match | Case-insensitive equality against `Npc.getIdentifier()` |
| Partial match | Case-insensitive containment in the identifier |
| Displayed match | Identifier formatted with `UtilString.clean`, such as `Shop Keeper` for `shop_keeper` |
| Feedback prefix | `Npc Search` |

With `npcService`, `commandSender`, and `input` available in a command or service:

```java
npcService.searchNpc(commandSender, input, true).ifPresent(npc -> UtilMessage.message(commandSender, "Npc", npc.getIdentifier()));
```

Use the four-argument overload to restrict eligible definitions with a predicate. With `inform` set to `true`, missing or ambiguous matches produce colored feedback; a unique result is returned without an automatic success message.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Npc</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Declare `NpcAddon` on your `PaperPlugin` with `@Addons({NpcAddon.class})`. NPC definitions belong under the plugin's package and use `@Singleton`; the framework registers the addon's internal service and listeners.

Does not require Paper NMS. Return to the [Addons README](../README.md).
