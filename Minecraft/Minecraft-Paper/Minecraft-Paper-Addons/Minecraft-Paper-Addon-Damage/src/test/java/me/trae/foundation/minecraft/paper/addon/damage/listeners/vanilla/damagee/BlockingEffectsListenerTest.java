package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageSetupEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("VERIFIED")
class BlockingEffectsListenerTest extends DamageTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void capturesBlockingComponentBeforeEffectsAndCallsOnBlockedOnlyForFullHit(final boolean full) {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final LivingEntity livingEntity = mock(LivingEntity.class);
        final DamageSource damageSource = mock(DamageSource.class);
        final ItemStack itemStack = mock(ItemStack.class);
        final BlocksAttacks blocksAttacks = mock(BlocksAttacks.class);
        when(livingEntity.getUseItem()).thenReturn(itemStack);
        when(itemStack.get(DataComponents.BLOCKS_ATTACKS)).thenReturn(blocksAttacks);
        final CustomDamageEvent customDamageEvent = damage(craft(livingEntity, serverLevel), null, source(damageSource), DamageCause.CUSTOM, 2.0D);
        customDamageEvent.setFullDamage(full);
        customDamageEvent.setBlockedDamage(2.0D);
        new BlockingEffectsListener().onDamageSetup(new DamageSetupEvent(customDamageEvent, Stage.BLOCKING_EFFECTS));

        final InOrder inOrder = inOrder(itemStack, livingEntity, blocksAttacks);
        inOrder.verify(itemStack).get(DataComponents.BLOCKS_ATTACKS);
        inOrder.verify(livingEntity).blockingItemEffects(serverLevel, damageSource, 2.0F);
        verify(blocksAttacks, times(full ? 1 : 0)).onBlocked(serverLevel, livingEntity);
    }
}