package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.vanilla.damager.WeaponReductionEvent;
import me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damager.AttackSnapshotListener.AttackSnapshot;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("UNVERIFIED")
@SuppressWarnings("unchecked")
class WeaponReductionListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @CsvSource({"0,false,1.2", "0.5,false,2.4", "1,false,6", "0.5,true,3.6"})
    void weaponReductionDefaultUsesReconstructedBaseDamageScale(final float strength, final boolean critical, final double expected) {
        final ItemStack itemStack = mock(ItemStack.class);
        final CustomDamageEvent customDamageEvent = damage(
                entity(), mock(Player.class), null, mock(DamageSource.class),
                DamageCause.ENTITY_ATTACK, 20.0D, itemStack, null
        );
        customDamageEvent.setCritical(critical);
        final AttackSnapshotListener attackSnapshotListener = mock(AttackSnapshotListener.class);
        when(attackSnapshotListener.getSnapshot(customDamageEvent)).thenReturn(new AttackSnapshot(strength, false, true, false));
        final net.minecraft.world.item.ItemStack nmsItemStack = mock(net.minecraft.world.item.ItemStack.class);
        final ItemAttributeModifiers itemAttributeModifiers = mock(ItemAttributeModifiers.class);
        when(nmsItemStack.get(DataComponents.ATTRIBUTE_MODIFIERS)).thenReturn(itemAttributeModifiers);
        final AttributeModifier attributeModifier = mock(AttributeModifier.class);
        when(attributeModifier.operation()).thenReturn(AttributeModifier.Operation.ADD_VALUE);
        when(attributeModifier.amount()).thenReturn(6.0D);
        doAnswer((final var invocation) -> {
            final BiConsumer<Holder<Attribute>, AttributeModifier> biConsumer = invocation.getArgument(1);
            biConsumer.accept(Attributes.ATTACK_DAMAGE, attributeModifier);

            return null;
        }).when(itemAttributeModifiers).forEach(eq(EquipmentSlot.MAINHAND), any());
        this.utilEventMock.when(() -> UtilEvent.supply(any(WeaponReductionEvent.class))).thenAnswer((final var invocation) -> {
            final WeaponReductionEvent weaponReductionEvent = invocation.getArgument(0);
            assertEquals(expected, weaponReductionEvent.getOriginalAmount(), 0.000001D);
            assertEquals(expected, weaponReductionEvent.getAmount(), 0.000001D);
            weaponReductionEvent.setAmount(0.0D);

            return weaponReductionEvent;
        });

        try (final MockedStatic<CraftItemStack> craftItemStackMockedStatic = mockStatic(CraftItemStack.class)) {
            craftItemStackMockedStatic.when(() -> CraftItemStack.asNMSCopy(itemStack)).thenReturn(nmsItemStack);
            new WeaponReductionListener(attackSnapshotListener).onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.WEAPON));
        }

        assertEquals(20.0D - expected, customDamageEvent.getDamage(), 0.000001D);
        this.utilEventMock.verify(() -> UtilEvent.supply(any(WeaponReductionEvent.class)));
    }
}