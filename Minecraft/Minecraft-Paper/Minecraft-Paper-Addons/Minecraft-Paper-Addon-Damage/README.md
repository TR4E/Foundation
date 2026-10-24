<h1 align="center">Minecraft-Paper-Addon-Damage</h1>

<p align="center">Custom damage processing with ordered stages, configurable hit delays, vanilla combat modifiers, knockback, hurt sounds, and death integration.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper NMS" src="https://img.shields.io/badge/Paper-NMS-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Damage addon replaces the normal Bukkit damage application path with a staged, event-driven damage system. It intercepts incoming damage, exposes a mutable `CustomDamageEvent`, and processes weapon adjustments, hit delays, damage reductions, health and absorption, knockback, sweeping attacks, hurt sounds, and death handling. Individual mechanics expose cancellable events so consuming plugins can customize combat without replacing the entire pipeline.

## Contents

<details>
<summary>On this page</summary>

- [How it works](#how-it-works)
- [Damage processing stages](#damage-processing-stages)
- [CustomDamageEvent](#customdamageevent)
- [Damage service](#damage-service)
- [Damage delay modes](#damage-delay-modes)
- [Damage reductions](#damage-reductions)
- [Attacker mechanics](#attacker-mechanics)
- [Sound handling](#sound-handling)
- [Knockback](#knockback)
- [Sweeping attacks](#sweeping-attacks)
- [Death integration](#death-integration)
- [Events](#events)
- [Customizing damage](#customizing-damage)
- [Implementation notes](#implementation-notes)
- [Installation](#installation)

</details>

## How it works

`PreDamageListener` listens to Bukkit's `EntityDamageEvent` at `LOWEST` priority. For a non-cancelled event targeting a `Damageable` entity (excluding `ComplexEntityPart`), it cancels the original event, wraps it in `CustomDamageEvent`, and passes it to `DamageService`.

The service dispatches `CustomDamageEvent` first, then sends `DamageSetupEvent` through a fixed sequence of stages. Built-in listeners handle individual stages, while consuming plugins can listen for the same events to change damage values, cancel an attack, or customize a specific vanilla mechanic.

After health processing, the service dispatches `DamageAppliedEvent`, processes knockback, records the last applied damage, and runs sweeping and death handling. These are synchronous events within the same processing call.

Because the Bukkit event is cancelled before custom processing, listeners that only observe uncancelled vanilla `EntityDamageEvent`s will not see this damage as an ordinary completed Bukkit hit. Integrations should use the addon's custom events.

## Damage processing stages

`DamageService` processes stages in the following order:

| Order | Stage | Behaviour |
| --- | --- | --- |
| 1 | `WEAPON` | Adjust damage using the attacking weapon's attribute contribution and attack strength |
| 2 | `DELAY` | Apply modern or legacy hit-delay rules |
| 3 | `FREEZING` | Apply extra freezing damage to susceptible entities |
| 4 | `HARD_HAT` | Reduce eligible falling-object damage when a helmet is equipped |
| 5 | `BLOCKING` | Determine damage blocked by an active shield or other blocking mechanic |
| 6 | `ARMOUR` | Reduce damage using armour points and toughness |
| 7 | `RESISTANCE` | Apply the Resistance status effect |
| 8 | `PROTECTION` | Apply protection-enchantment reduction |
| 9 | `ABSORPTION` | Allocate damage to absorption hearts |
| 10 | `HEALTH` | Subtract remaining damage from health |
| 11 | `KNOCKBACK` | Apply eligible damage knockback |
| 12 | `SWEEPING` | Process eligible sword sweep targets |
| 13 | `DEATH` | Handle lethal damage and totem protection |

`DamageAppliedEvent` is dispatched **after `HEALTH` and before `KNOCKBACK`**. When `damageEvent.isApplied()` is true, the service saves the event in its last-damage map **after `KNOCKBACK` and before `SWEEPING`**.

Cancellation is checked after the initial custom event, after `DELAY`, after `ABSORPTION`, and after `HEALTH`. Built-in stage listeners also check the damage event's cancellation state before acting. Cancelling `CustomDamageEvent` prevents the remaining effective damage processing; cancelling a mechanic-specific event normally skips only that mechanic.

## CustomDamageEvent

`CustomDamageEvent` extends `CustomCancellableEvent` and stores the damage context and mutable result.

| Field or method | Purpose |
| --- | --- |
| `getDamagee()` | Entity receiving damage |
| `getDamager()` | Attacking entity, or `null` for environmental damage |
| `getProjectile()` | Original projectile when the Bukkit damager was a projectile |
| `getSource()` | Bukkit `DamageSource`, including damage-type information |
| `getCause()` | Bukkit `DamageCause` |
| `getItemStack()` | Attacker's captured main-hand item, when available |
| `getArmourContents()` | Target's captured armour contents |
| `getOriginalDamage()` | Initial damage value, retained separately from changes |
| `getDamage()` / `setDamage(...)` | Current damage within the pipeline |
| `getBlockedDamage()` | Damage removed by the blocking stage |
| `isCritical()` | Whether the original Bukkit entity attack was critical |
| `isFullDamage()` | Whether the delay stage classified this as a full hit |
| `isApplied()` | Whether the built-in health or absorption handler actually reduced a value |
| `getSoundStash()` / `setSoundStash(...)` | Optional `SoundStash` for the damagee’s hurt sound; includes the sound key, category, volume, and pitch |
| `getSystemTime()` | Damage-event creation time in milliseconds |
| `getFinalDamage()` | Current damage clamped to a minimum of zero |
| `hasDamage()` | Whether final damage is greater than zero |
| `isMelee()` | Whether the cause is `ENTITY_ATTACK` or `ENTITY_SWEEP_ATTACK` |

`addDamage(amount)`, `subtractDamage(amount)`, and `multiplyDamage(multiplier)` modify the current damage directly. These helpers do not clamp the stored value; `getFinalDamage()` applies the lower bound when reading it.

For projectile hits, the constructor retains the projectile separately and resolves `getDamager()` to its shooter when the shooter is an entity. Custom constructors are available with or without a damager; the damage source and cause must be supplied by the caller.

## Damage service

Inject `DamageService` to process custom damage or inspect the latest applied damage for an entity.

| Member | Purpose |
| --- | --- |
| `process(damageEvent)` | Execute the complete damage pipeline for a `CustomDamageEvent` |
| `getLastDamageMap()` | Mutable concurrent map of entity UUIDs to their most recently recorded applied damage event |

To inflict damage through the addon rather than Bukkit's normal `damage(...)` method:

```java
@AllArgsConstructor
@Singleton
public final class CustomDamageService {

    private final DamageService damageService;

    public void applyDamage(final LivingEntity target) {
        final DamageSource source = DamageSource.builder(DamageType.GENERIC).build();

        this.damageService.process(new CustomDamageEvent(target, source, DamageCause.CUSTOM, 6.0D));
    }
}
```

`DamageService` does not synthesize an attacker or weapon for these custom constructors. If attacker-specific behaviour is needed, use the constructor that accepts a damager. The source must be compatible with the NMS-backed mechanics you intend to use.

The last-damage map is updated only when the event's `applied` flag is set. `DamageCleanupListener` removes entries when entities leave the world; death integration also consumes the matching entry on death.

## Damage delay modes

`DamageSettings` exposes a global delay mode. The default is `MODERN`:

```java
DamageSettings.setDelayMode(DelayMode.LEGACY);
```

| Mode | Behaviour |
| --- | --- |
| `MODERN` | Uses the entity's vanilla no-damage ticks and previous damage. During the protected window, a stronger hit can apply only the difference; an equal or weaker hit is cancelled. Damage types tagged to bypass cooldown skip that comparison. |
| `LEGACY` | Uses separate expiry tracking by attacker UUID or environmental damage cause. A hit during an existing tracked delay is cancelled. |

### Modern delay

`ModernDamageDelayListener` snapshots `getLastDamage()`, `getNoDamageTicks()`, and the previous configured duration. Its default duration is `getMaximumNoDamageTicks() * 50` milliseconds, converted back to ticks after customization. Accepted full hits update the no-damage ticks and hurt animation; accepted partial hits update the last incoming damage without resetting the full delay.

### Legacy delay

`LegacyDamageDelayListener` tracks expiry per target and attacker, or per target and damage cause when no attacker is present. Its default durations are:

| Damage cause | Duration |
| --- | --- |
| `STARVATION` | 4,000 ms |
| `WITHER`, `FREEZE` | 2,000 ms |
| `POISON` | 1,250 ms |
| `FIRE_TICK`, `DROWNING` | 1,000 ms |
| All other causes | 500 ms |

The expiry is committed on `DamageAppliedEvent`. Entity removal clears related legacy delay records.

### Customizing a delay

Both modes fire `CustomDelayEvent` during the `DELAY` stage. Its `durationMillis` is settable:

```java
@Singleton
public final class FireDelayListener implements Listener {

    @EventHandler
    public void onCustomDelay(final CustomDelayEvent event) {
        if (event.getDamageEvent().getCause() == DamageCause.FIRE_TICK) {
            event.setDurationMillis(500L);
        }
    }
}
```

Cancelling `CustomDelayEvent` makes the current delay-stage handler mark the hit as full damage without installing its new delay state. This is different from cancelling `CustomDamageEvent`. In legacy mode, the early check against an *already active* delay happens before `CustomDelayEvent` is fired, so this cancellation cannot override an existing legacy cooldown.

## Damage reductions

The target-side stages provide individual events for modifying or skipping damage calculations:

| Event | Stage | Customizable value |
| --- | --- | --- |
| `FreezingDamageEvent` | `FREEZING` | Multiplier, default `5.0` for tagged susceptible entities |
| `HardHatDamageEvent` | `HARD_HAT` | Multiplier, default `0.75` when a helmet is present |
| `BlockingDamageEvent` | `BLOCKING` | Amount of damage blocked, clamped to the current nonnegative damage |
| `ArmourReductionEvent` | `ARMOUR` | Armour points and toughness contribution for each equipped armour slot |
| `ResistanceReductionEvent` | `RESISTANCE` | Damage remaining after the Resistance effect |
| `ProtectionReductionEvent` | `PROTECTION` | Enchantment protection value used by the damage formula |
| `AbsorptionReductionEvent` | `ABSORPTION` | Amount absorbed, bounded by current absorption hearts and damage |

These events extend `CustomCancellableEvent`. Cancellation skips the corresponding reduction or, for armour, removes the affected slot's default contribution from the calculation. Cancelling one does not automatically cancel the entire hit.

The armour stage starts with the entity's NMS armour and toughness attributes, then dispatches `ArmourReductionEvent` separately for the `BOOTS`, `LEGGINGS`, `CHESTPLATE`, and `HELMET` slots. It applies Minecraft's armour formula using the resulting totals. Resistance and protection respect relevant damage-type bypass tags.

Absorption is calculated during `ABSORPTION` but applied to the entity on `DamageAppliedEvent`; the health stage subtracts the remaining damage separately. This allows an absorption-only hit to mark the event as applied.

## Attacker mechanics

The addon also exposes cancellable events for the attacker's weapon and on-hit effects:

| Event | Trigger | Customizable behaviour |
| --- | --- | --- |
| `WeaponReductionEvent` | `WEAPON` stage on eligible melee hits | Replace the calculated weapon-attribute damage contribution |
| `AttackKnockbackEvent` | Damage knockback with an eligible player attacker | Change enchantment/sprinting knockback bonus and sprint reset |
| `AttackEffectsEvent` | `DamageAppliedEvent` on eligible player attacks | Change attack sound, exhaustion, and critical particle count |
| `AttackEnchantmentEvent` | `DamageAppliedEvent` on eligible melee hits | Allow or suppress NMS post-attack enchantment effects |
| `WeaponDurabilityEvent` | `DamageAppliedEvent` on eligible melee hits | Change the durability amount consumed by the weapon |
| `SweepingAttackEvent` | `SWEEPING` stage, for each eligible secondary target | Change or cancel that target's sweep damage |

`AttackSnapshotListener` captures player attack strength, sprinting, and on-ground state from Paper's `PrePlayerAttackEntityEvent`. It determines sweep eligibility using the server's NMS attack logic. `WeaponReductionListener`, `AttackKnockbackListener`, `AttackEffectsListener`, and `SweepingAttackListener` consume this snapshot when available.

`WeaponReductionEvent` supplies the calculated contribution as `originalAmount` and an editable `amount`. The listener adds the difference between these values to the current damage; it does not simply replace the entire hit with the event's amount.

## Sound handling

The addon separates the **attacker's attack sound** from the **damagee's hurt sound**. Both can play for a successful melee hit, but they have different owners and customization points.

| Sound | Source | Playback |
| --- | --- | --- |
| Attack sound | `AttackEffectsEvent` in `AttackEffectsListener` | Played at the attacking player's location; `CRIT`, `STRONG`, or `WEAK` is selected from the critical flag and captured attack strength |
| Hurt sound | `CustomDamageEvent.getSoundStash()` in `HurtSoundListener` | Played at the damagee's location only for an uncancelled, applied, full-damage hit with a non-null sound stash |

`CustomDamageEvent` carries an optional `SoundStash` for the damagee. A stash holds the sound key, `SoundCategory`, volume, and pitch, allowing an event listener to replace or suppress the hurt sound without changing the attacker's combat effects. `HurtSoundListener` observes `DamageAppliedEvent` and plays the stored stash when `!damageEvent.isCancelled()`, `damageEvent.isApplied()`, and `damageEvent.isFullDamage()` are all true.

```java
@Singleton
public final class QuietHurtSoundListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageApplied(final DamageAppliedEvent event) {
        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.getDamagee() instanceof Player) {
            damageEvent.setSoundStash(null);
        }
    }
}
```

The example suppresses a player’s hurt sound before the default-priority `HurtSoundListener` executes. Populate the stash before `DamageAppliedEvent` is dispatched for the sound to play; merely adding the `soundStash` field or the playback listener does not generate a default hurt sound. Partial hits that are not marked `fullDamage` do not play it through this listener.

## Knockback

At the `KNOCKBACK` stage, eligible full, applied hits produce `DamageKnockbackEvent`. The initial strength is `0.4`, with horizontal direction derived from the damage source's position when available. Eligible player melee attacks may add Knockback-enchantment and sprinting bonuses through `AttackKnockbackEvent`.

| Property | Behaviour |
| --- | --- |
| `directionX`, `directionZ` | Editable horizontal knockback direction |
| `strength` | Editable knockback strength |
| `sprintReset` | Whether to stop the attacker sprinting after knockback |
| Cancellation | Suppresses the custom knockback action, not the damage itself |

Knockback is skipped for damage types tagged `NO_KNOCKBACK`, for blocked damage, for hits not marked `fullDamage`, and for events not marked `applied`. Its final application uses NMS knockback and damage indication with a Paper knockback cause.

## Sweeping attacks

`SweepingAttackListener` handles eligible full player sword attacks after the primary target's knockback. It uses the captured attack snapshot, checks nearby living entities against vanilla-style ally, distance, and target filters, and dispatches one `SweepingAttackEvent` for each eligible secondary target.

Each uncancelled sweep with positive damage is routed back through `DamageService.process(...)` as a new `CustomDamageEvent` with cause `ENTITY_SWEEP_ATTACK`. Secondary targets therefore run through their own damage reductions, health, and death handling instead of receiving a direct health subtraction from the sweep listener.

## Death integration

The Damage addon integrates with the [Death addon](../Minecraft-Paper-Addon-Death/README.md) through `CustomDeathCreator` and `CustomDeathEvent`.

At the `DEATH` stage, `CustomDeathListener` checks the NMS entity's dying state, invokes totem-death protection, and calls the entity's death handler when appropriate. It preserves and restores the existing `silentDeath` flag, setting it during a non-full hit.

When the Death addon receives `EntityDeathEvent`, `CustomDeathCreator` removes the last recorded damage event for that entity. If present, it creates a `CustomDeathEvent` implementing the Death addon's `DeathEvent` contract:

| Property | Source |
| --- | --- |
| Victim, killer, cause, item | Saved `CustomDamageEvent` |
| Drops, dropped experience | Bukkit `EntityDeathEvent` |
| Death sound | Bukkit death sound, category, volume, and pitch |
| Reason name | `null` by default |

Drops, experience, and the sound stash can be modified on `CustomDeathEvent`. If no saved damage event exists, the creator logs the missing history and returns `null` rather than inventing a damage cause.

## Events

All events are dispatched synchronously by the addon and its listeners.

| Event | Type | Purpose |
| --- | --- | --- |
| `CustomDamageEvent` | Cancellable, mutable | Inspect, modify, or cancel the incoming hit |
| `DamageSetupEvent` | Informational | Identify the active `Stage` and expose its damage event |
| `CustomDelayEvent` | Cancellable, mutable | Change or bypass the current delay setup |
| `DamageAppliedEvent` | Informational | Notify listeners after the health stage; also used by attack effects and hurt-sound playback |
| `DamageKnockbackEvent` | Cancellable, mutable | Adjust or cancel primary knockback |
| `FreezingDamageEvent` | Cancellable, mutable | Modify freezing damage multiplier |
| `HardHatDamageEvent` | Cancellable, mutable | Modify helmet damage multiplier |
| `BlockingDamageEvent` | Cancellable, mutable | Modify blocked damage |
| `ArmourReductionEvent` | Cancellable, mutable | Customize armour points and toughness per slot |
| `ResistanceReductionEvent` | Cancellable, mutable | Customize resistance reduction |
| `ProtectionReductionEvent` | Cancellable, mutable | Customize enchantment protection |
| `AbsorptionReductionEvent` | Cancellable, mutable | Customize absorption consumption |
| `WeaponReductionEvent` | Cancellable, mutable | Customize the weapon-attribute contribution |
| `AttackKnockbackEvent` | Cancellable, mutable | Customize attack knockback bonus |
| `AttackEffectsEvent` | Cancellable, mutable | Customize attack audiovisual effects and exhaustion |
| `AttackEnchantmentEvent` | Cancellable | Suppress post-attack enchantment effects |
| `WeaponDurabilityEvent` | Cancellable, mutable | Customize weapon durability consumption |
| `SweepingAttackEvent` | Cancellable, mutable | Customize damage to a secondary sweep target |

`CustomDeathEvent` is a separate, non-cancellable death event implementing the Death addon's `DeathEvent` contract.

## Customizing damage

Register event listeners as plugin components with Injector's `@Singleton` annotation. For example, reduce fall damage by 50% and increase sword-sweep damage for secondary targets:

```java
@Singleton
public final class CombatRulesListener implements Listener {

    @EventHandler
    public void onCustomDamage(final CustomDamageEvent event) {
        if (event.getCause() == DamageCause.FALL) {
            event.multiplyDamage(0.5D);
        }
    }

    @EventHandler
    public void onSweepingAttack(final SweepingAttackEvent event) {
        event.setDamage(event.getDamage() * 1.25D);
    }
}
```

To modify damage at a particular processing step, listen for `DamageSetupEvent` and check its stage:

```java
@Singleton
public final class HealthDamageListener implements Listener {

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.HEALTH) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.getDamagee() instanceof Player) {
            damageEvent.multiplyDamage(0.9D);
        }
    }
}
```

The `HEALTH` stage runs after armour, Resistance, protection enchantments, and absorption calculations. Changes at this stage affect the amount removed from health, not the absorption already calculated. Use an earlier stage to change the amount entering those reductions.

## Implementation notes

- `DamageAppliedEvent` means the pipeline reached its post-health notification point; it is **not proof of nonzero damage**. Check `damageEvent.isApplied()` to determine whether the built-in health or absorption handler reduced a value.
- `HurtSoundListener` requires a populated `CustomDamageEvent.soundStash`; it does not determine a default hurt sound itself. Its playback is limited to applied, full-damage hits.
- `isFullDamage()` describes the delay system's classification and controls subsequent mechanics such as knockback and sweeping. It is not the same as `getFinalDamage()` or `isApplied()`.
- The addon recreates the combat mechanics implemented by its listeners. It does **not** automatically restore every side effect of the vanilla damage path, which was cancelled by `PreDamageListener`.
- The implementation uses CraftBukkit and NMS classes, damage-type tags, and reflective access to internal methods. It is tied to the compatible Paper server version and requires corresponding NMS mappings at runtime.
- The `DamageService` last-damage map is a mutable internal data structure. Prefer reading it rather than changing entries from unrelated plugins.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Damage</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Declare `DamageAddon` on your `PaperPlugin` with `@Addons({DamageAddon.class})`. The framework discovers the addon's `@AddonSingleton` services and listeners. Add and declare the Death addon as well if using the linked custom death event integration.

**Requires Paper NMS.** Return to the [Addons README](../README.md).
