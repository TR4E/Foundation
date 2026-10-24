package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damager.SweepingAttackEvent;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackSnapshotListener.AttackSnapshot;
import me.trae.foundation.minecraft.paper.addon.damage.services.DamageService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.bukkit.Tag;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@AddonSingleton
public final class SweepingAttackListener implements Listener {

    private final AttackSnapshotListener attackSnapshotListener;
    private final DamageService damageService;

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.SWEEPING) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        if (damageEvent.isCancelled() || damageEvent.getBlockedDamage() > 0.0D || !damageEvent.isFullDamage() || damageEvent.getCause() != DamageCause.ENTITY_ATTACK || damageEvent.getProjectile() != null || !Player.class.isInstance(damageEvent.getDamager()) || !CraftEntity.class.isInstance(damageEvent.getDamagee()) || !CraftDamageSource.class.isInstance(damageEvent.getSource())) {
            return;
        }

        final AttackSnapshot attackSnapshot = this.attackSnapshotListener.getSnapshot(damageEvent);
        if (attackSnapshot == null || !attackSnapshot.sweeping()) {
            return;
        }

        final ItemStack itemStack = damageEvent.getItemStack();
        if (itemStack == null || !Tag.ITEMS_SWORDS.isTagged(itemStack.getType())) {
            return;
        }

        final net.minecraft.server.level.ServerPlayer attacker = CraftPlayer.class.cast(damageEvent.getDamager()).getHandle();

        final net.minecraft.world.entity.Entity primary = CraftEntity.class.cast(damageEvent.getDamagee()).getHandle();

        final ServerLevel serverLevel = ServerLevel.class.cast(attacker.level());

        final net.minecraft.world.damagesource.DamageSource damageSource = CraftDamageSource.class.cast(damageEvent.getSource()).getHandle();

        final int sweepingLevel = itemStack.getEnchantmentLevel(Enchantment.SWEEPING_EDGE);

        final double sweepingRatio = sweepingLevel / (sweepingLevel + 1.0D);

        final float baseDamage = 1.0F + (float) (sweepingRatio * attacker.getAttributeValue(Attributes.ATTACK_DAMAGE));

        for (final LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, primary.getBoundingBox().inflate(1.0D, 0.25D, 1.0D))) {
            if (target == attacker || target == primary || attacker.isAlliedTo(target) || attacker.distanceToSqr(target) >= 9.0D) {
                continue;
            }

            final org.bukkit.entity.LivingEntity entity = org.bukkit.entity.LivingEntity.class.cast(target.getBukkitEntity());

            if (entity instanceof final ArmorStand armorStand && armorStand.isMarker()) {
                continue;
            }

            final float damage = EnchantmentHelper.modifyDamage(serverLevel, CraftItemStack.asNMSCopy(itemStack), target, damageSource, baseDamage) * attackSnapshot.strength();

            final SweepingAttackEvent sweepingAttackEvent = UtilEvent.supply(new SweepingAttackEvent(damageEvent, entity, damage));

            if (sweepingAttackEvent.isCancelled() || sweepingAttackEvent.getDamage() <= 0.0D) {
                continue;
            }

            final org.bukkit.damage.DamageSource sweepingSource = new CraftDamageSource(damageSource.knownCause(DamageCause.ENTITY_SWEEP_ATTACK));

            this.damageService.process(new CustomDamageEvent(entity, damageEvent.getDamager(), sweepingSource, DamageCause.ENTITY_SWEEP_ATTACK, sweepingAttackEvent.getDamage()));
        }
    }
}