package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damager.WeaponReductionEvent;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackSnapshotListener.AttackSnapshot;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@AddonSingleton
public final class WeaponReductionListener implements Listener {

    private final AttackSnapshotListener attackSnapshotListener;

    @EventHandler
    public void onDamageSetup(final DamageSetupEvent event) {
        if (event.getStage() != Stage.WEAPON) {
            return;
        }

        final CustomDamageEvent damageEvent = event.getDamageEvent();

        final ItemStack itemStack = damageEvent.getItemStack();

        if (damageEvent.isCancelled() || !damageEvent.isMelee() || damageEvent.getProjectile() != null || itemStack == null || itemStack.isEmpty()) {
            return;
        }

        final ItemAttributeModifiers itemAttributeModifiers = CraftItemStack.asNMSCopy(itemStack).get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (itemAttributeModifiers == null) {
            return;
        }

        final double[] attackDamage = new double[1];

        itemAttributeModifiers.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                attackDamage[0] += modifier.amount();
            }
        });

        if (attackDamage[0] == 0.0D) {
            return;
        }

        double scale = 1.0D;

        if (damageEvent.getDamager() instanceof Player) {
            final AttackSnapshot snapshot = this.attackSnapshotListener.getSnapshot(damageEvent);
            if (snapshot == null) {
                return;
            }

            scale = 0.2D + snapshot.strength() * snapshot.strength() * 0.8D;
        }

        final double weaponDamage = attackDamage[0] * scale * (damageEvent.isCritical() ? 1.5D : 1.0D);

        final WeaponReductionEvent weaponReductionEvent = UtilEvent.supply(new WeaponReductionEvent(damageEvent, itemStack, weaponDamage, weaponDamage));

        if (weaponReductionEvent.isCancelled()) {
            return;
        }

        damageEvent.addDamage(weaponReductionEvent.getAmount() - weaponDamage);
    }
}