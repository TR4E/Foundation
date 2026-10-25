package me.trae.foundation.minecraft.paper.addon.npc.snapshot;

import me.trae.foundation.minecraft.paper.addon.npc.NpcTestSupport;
import net.kyori.adventure.util.TriState;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class NpcSnapshotTest extends NpcTestSupport {

    private static final List<EquipmentSlot> EQUIPMENT_SLOT_LIST = List.of(
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    );

    @ParameterizedTest
    @CsvSource({"80,100,10,10", "15,20,40,15", "0,20,40,1", "20,20,20,20"})
    void healthIsClampedToTheCurrentMaximumRatherThanTheCapturedMaximum(
            final double capturedHealth, final double capturedMaximum,
            final double currentMaximum, final double expectedHealth
    ) {
        final LivingEntity originalLivingEntity = this.livingEntity();
        when(originalLivingEntity.getHealth()).thenReturn(capturedHealth);
        this.maxHealth(originalLivingEntity, capturedMaximum);
        final NpcSnapshot npcSnapshot = this.snapshot(originalLivingEntity, 0);
        final LivingEntity restoredLivingEntity = this.livingEntity();
        final AttributeInstance attributeInstance = this.maxHealth(restoredLivingEntity, currentMaximum);
        npcSnapshot.apply(restoredLivingEntity);

        verify(restoredLivingEntity).setHealth(expectedHealth);
        verify(attributeInstance, never()).setBaseValue(anyDouble());
    }

    @ParameterizedTest
    @CsvSource({"0,40,10,20", "15,25,0,5", "40,0,0,0", "100,0,0,0"})
    void transientTicksDecayByElapsedTicksAndAreFlooredAtZero(
            final int elapsedTicks, final int expectedFireTicks,
            final int expectedFreezeTicks, final int expectedNoDamageTicks
    ) {
        final LivingEntity originalLivingEntity = this.livingEntity();
        when(originalLivingEntity.getFireTicks()).thenReturn(40);
        when(originalLivingEntity.getFreezeTicks()).thenReturn(10);
        when(originalLivingEntity.getNoDamageTicks()).thenReturn(20);
        final NpcSnapshot npcSnapshot = this.snapshot(originalLivingEntity, elapsedTicks);
        final LivingEntity restoredLivingEntity = this.livingEntity();
        npcSnapshot.apply(restoredLivingEntity);

        verify(restoredLivingEntity).setFireTicks(expectedFireTicks);
        verify(restoredLivingEntity).setFreezeTicks(expectedFreezeTicks);
        verify(restoredLivingEntity).setNoDamageTicks(expectedNoDamageTicks);
    }

    @Test
    void finitePotionDurationDecaysWhileAmplifierAndPresentationFlagsArePreserved() {
        final LivingEntity originalLivingEntity = this.livingEntity();
        when(originalLivingEntity.getActivePotionEffects()).thenReturn(List.of(
                new PotionEffect(PotionEffectType.SPEED, 60, 2, true, false, true)
        ));
        final NpcSnapshot npcSnapshot = this.snapshot(originalLivingEntity, 20);
        final LivingEntity restoredLivingEntity = this.livingEntity();
        npcSnapshot.apply(restoredLivingEntity);

        final ArgumentCaptor<PotionEffect> potionEffectArgumentCaptor = ArgumentCaptor.forClass(PotionEffect.class);
        verify(restoredLivingEntity).addPotionEffect(potionEffectArgumentCaptor.capture());
        final PotionEffect potionEffect = potionEffectArgumentCaptor.getValue();
        assertSame(PotionEffectType.SPEED, potionEffect.getType());
        assertEquals(40, potionEffect.getDuration());
        assertEquals(2, potionEffect.getAmplifier());
        assertTrue(potionEffect.isAmbient());
        assertFalse(potionEffect.hasParticles());
        assertTrue(potionEffect.hasIcon());
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 21, 200})
    void expiredPotionsIncludingExactlyExpiredPotionsAreDropped(final int elapsedTicks) {
        final LivingEntity originalLivingEntity = this.livingEntity();
        when(originalLivingEntity.getActivePotionEffects()).thenReturn(List.of(new PotionEffect(PotionEffectType.SPEED, 20, 0)));
        final NpcSnapshot npcSnapshot = this.snapshot(originalLivingEntity, elapsedTicks);
        final LivingEntity restoredLivingEntity = this.livingEntity();
        npcSnapshot.apply(restoredLivingEntity);

        verify(restoredLivingEntity, never()).addPotionEffect(any());
    }

    @Test
    void infinitePotionsAreAppliedUnchanged() {
        final LivingEntity originalLivingEntity = this.livingEntity();
        final PotionEffect potionEffect = new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 1);
        when(originalLivingEntity.getActivePotionEffects()).thenReturn(List.of(potionEffect));
        final NpcSnapshot npcSnapshot = this.snapshot(originalLivingEntity, 100_000);
        final LivingEntity restoredLivingEntity = this.livingEntity();
        npcSnapshot.apply(restoredLivingEntity);

        verify(restoredLivingEntity).addPotionEffect(same(potionEffect));
    }

    @Test
    void equipmentItemsDurabilityAndDropChancesAreCapturedIndependentlyAndRoundTripped() {
        final LivingEntity originalLivingEntity = this.livingEntity();
        final EntityEquipment originalEntityEquipment = mock(EntityEquipment.class);
        when(originalLivingEntity.getEquipment()).thenReturn(originalEntityEquipment);
        final Map<EquipmentSlot, ItemStack> originalItemStackMap = new EnumMap<>(EquipmentSlot.class);

        for (final EquipmentSlot equipmentSlot : EQUIPMENT_SLOT_LIST) {
            final ItemStack itemStack = this.itemStack(Material.DIAMOND_SWORD, equipmentSlot.ordinal() + 7);
            originalItemStackMap.put(equipmentSlot, itemStack);
            when(originalEntityEquipment.getItem(equipmentSlot)).thenReturn(itemStack);
            when(originalEntityEquipment.getDropChance(equipmentSlot)).thenReturn(equipmentSlot.ordinal() / 10.0F);
        }

        final NpcEquipmentSnapshot npcEquipmentSnapshot = new NpcEquipmentSnapshot(originalLivingEntity);
        originalItemStackMap.values().forEach(itemStack -> ((Damageable) itemStack.getItemMeta()).setDamage(99));
        final LivingEntity restoredLivingEntity = this.livingEntity();
        final EntityEquipment restoredEntityEquipment = mock(EntityEquipment.class);
        when(restoredLivingEntity.getEquipment()).thenReturn(restoredEntityEquipment);
        final Map<EquipmentSlot, ItemStack> restoredItemStackMap = new EnumMap<>(EquipmentSlot.class);
        doAnswer(invocationOnMock -> {
            restoredItemStackMap.put(invocationOnMock.getArgument(0), invocationOnMock.getArgument(1));
            return null;
        }).when(restoredEntityEquipment).setItem(any(), any());
        npcEquipmentSnapshot.apply(restoredLivingEntity);

        assertEquals(EQUIPMENT_SLOT_LIST.size(), restoredItemStackMap.size());

        for (final EquipmentSlot equipmentSlot : EQUIPMENT_SLOT_LIST) {
            final ItemStack restoredItemStack = restoredItemStackMap.get(equipmentSlot);
            assertNotSame(originalItemStackMap.get(equipmentSlot), restoredItemStack);
            assertEquals(Material.DIAMOND_SWORD, restoredItemStack.getType());
            assertEquals(equipmentSlot.ordinal() + 7, ((Damageable) restoredItemStack.getItemMeta()).getDamage());
            verify(restoredEntityEquipment).setDropChance(equipmentSlot, equipmentSlot.ordinal() / 10.0F);
            verify(originalItemStackMap.get(equipmentSlot)).clone();
        }
    }

    @Test
    void missingEquipmentAtCaptureOrApplyIsSafe() {
        final NpcEquipmentSnapshot npcEquipmentSnapshot = new NpcEquipmentSnapshot(this.livingEntity());
        assertDoesNotThrow(() -> npcEquipmentSnapshot.apply(this.livingEntity()));

        final LivingEntity equippedLivingEntity = this.livingEntity();
        final EntityEquipment entityEquipment = mock(EntityEquipment.class);
        when(equippedLivingEntity.getEquipment()).thenReturn(entityEquipment);

        for (final EquipmentSlot equipmentSlot : EQUIPMENT_SLOT_LIST) {
            final ItemStack itemStack = this.itemStack(Material.AIR, 0);
            when(entityEquipment.getItem(equipmentSlot)).thenReturn(itemStack);
        }

        assertDoesNotThrow(() -> new NpcEquipmentSnapshot(equippedLivingEntity).apply(this.livingEntity()));
    }

    @Test
    void attributeModifiersReplaceTheSameKeyAndPreserveUnrelatedModifiersAcrossRepeatedApplies() {
        final NamespacedKey sharedNamespacedKey = new NamespacedKey("test", "shared");
        final NamespacedKey unrelatedNamespacedKey = new NamespacedKey("test", "unrelated");
        final AttributeModifier capturedAttributeModifier = new AttributeModifier(
                sharedNamespacedKey, 3.0D, AttributeModifier.Operation.ADD_NUMBER
        );
        final AttributeModifier oldAttributeModifier = new AttributeModifier(
                sharedNamespacedKey, 1.0D, AttributeModifier.Operation.ADD_NUMBER
        );
        final AttributeModifier unrelatedAttributeModifier = new AttributeModifier(
                unrelatedNamespacedKey, 2.0D, AttributeModifier.Operation.ADD_NUMBER
        );
        final LivingEntity originalLivingEntity = this.livingEntity();
        final AttributeInstance originalAttributeInstance = this.maxHealth(originalLivingEntity, 20.0D);
        final List<AttributeModifier> capturedAttributeModifierList = new ArrayList<>(List.of(capturedAttributeModifier));
        when(originalAttributeInstance.getModifiers()).thenReturn(capturedAttributeModifierList);
        final NpcAttributeSnapshot npcAttributeSnapshot = new NpcAttributeSnapshot(originalLivingEntity);
        capturedAttributeModifierList.clear();

        final LivingEntity restoredLivingEntity = this.livingEntity();
        final AttributeInstance restoredAttributeInstance = this.maxHealth(restoredLivingEntity, 20.0D);
        final Map<NamespacedKey, AttributeModifier> attributeModifierMap = new HashMap<>(Map.of(
                sharedNamespacedKey, oldAttributeModifier, unrelatedNamespacedKey, unrelatedAttributeModifier
        ));
        doAnswer(invocationOnMock -> attributeModifierMap.remove(invocationOnMock.<NamespacedKey>getArgument(0)))
                .when(restoredAttributeInstance).removeModifier(any(NamespacedKey.class));
        doAnswer(invocationOnMock -> {
            final AttributeModifier attributeModifier = invocationOnMock.getArgument(0);
            assertFalse(attributeModifierMap.containsKey(attributeModifier.getKey()), "Duplicate modifier key");
            attributeModifierMap.put(attributeModifier.getKey(), attributeModifier);
            return null;
        }).when(restoredAttributeInstance).addModifier(any());
        npcAttributeSnapshot.apply(restoredLivingEntity);
        npcAttributeSnapshot.apply(restoredLivingEntity);

        assertEquals(Map.of(sharedNamespacedKey, capturedAttributeModifier,
                unrelatedNamespacedKey, unrelatedAttributeModifier), attributeModifierMap);
    }

    @Test
    void anAttributeMissingFromTheRestoredEntityIsSkipped() {
        final LivingEntity originalLivingEntity = this.livingEntity();
        final AttributeInstance attributeInstance = this.maxHealth(originalLivingEntity, 20.0D);
        when(attributeInstance.getModifiers()).thenReturn(List.of(new AttributeModifier(
                new NamespacedKey("test", "bonus"), 2.0D, AttributeModifier.Operation.ADD_NUMBER
        )));
        final NpcAttributeSnapshot npcAttributeSnapshot = new NpcAttributeSnapshot(originalLivingEntity);

        assertDoesNotThrow(() -> npcAttributeSnapshot.apply(this.livingEntity()));
    }

    @Test
    void scoreboardTagsAndPersistentDataAreCapturedAndMergedWithExistingDestinationData() {
        final LivingEntity originalLivingEntity = this.livingEntity();
        final Set<String> originalTagSet = new HashSet<>(Set.of("captured"));
        when(originalLivingEntity.getScoreboardTags()).thenReturn(originalTagSet);
        final NamespacedKey capturedNamespacedKey = new NamespacedKey("test", "captured");
        final NamespacedKey existingNamespacedKey = new NamespacedKey("test", "existing");
        originalLivingEntity.getPersistentDataContainer().set(capturedNamespacedKey, PersistentDataType.STRING, "saved");
        final NpcSnapshot npcSnapshot = this.snapshot(originalLivingEntity, 0);
        originalTagSet.clear();
        originalLivingEntity.getPersistentDataContainer().set(capturedNamespacedKey, PersistentDataType.STRING, "changed");

        final LivingEntity restoredLivingEntity = this.livingEntity();
        final Set<String> restoredTagSet = new HashSet<>(Set.of("existing"));
        doAnswer(invocationOnMock -> restoredTagSet.add(invocationOnMock.getArgument(0)))
                .when(restoredLivingEntity).addScoreboardTag(anyString());
        restoredLivingEntity.getPersistentDataContainer().set(capturedNamespacedKey, PersistentDataType.STRING, "stale");
        restoredLivingEntity.getPersistentDataContainer().set(existingNamespacedKey, PersistentDataType.STRING, "retained");
        npcSnapshot.apply(restoredLivingEntity);

        assertEquals(Set.of("captured", "existing"), restoredTagSet);
        assertEquals(
                "saved",
                restoredLivingEntity.getPersistentDataContainer().get(capturedNamespacedKey, PersistentDataType.STRING)
        );
        assertEquals(
                "retained",
                restoredLivingEntity.getPersistentDataContainer().get(existingNamespacedKey, PersistentDataType.STRING)
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aMissingOrNonLivingTargetIsNotApplied(final boolean nonLivingReplacement) {
        final Mob originalMob = this.entity(Mob.class);
        when(originalMob.getHealth()).thenReturn(20.0D);
        final LivingEntity targetLivingEntity = this.livingEntity();
        when(originalMob.getTarget()).thenReturn(targetLivingEntity);
        final NpcSnapshot npcSnapshot = this.snapshot(originalMob, 0);
        final Mob restoredMob = this.entity(Mob.class);
        this.maxHealth(restoredMob, 20.0D);

        if (nonLivingReplacement) {
            final Entity entity = this.entity(Entity.class);
            when(this.server.getEntity(targetLivingEntity.getUniqueId())).thenReturn(entity);
        }

        npcSnapshot.apply(restoredMob);
        verify(this.server).getEntity(targetLivingEntity.getUniqueId());
        verify(restoredMob, never()).setTarget(any());
    }

    @Test
    void anExistingLivingTargetIsResolvedByItsCapturedUuid() {
        final Mob originalMob = this.entity(Mob.class);
        when(originalMob.getHealth()).thenReturn(20.0D);
        final LivingEntity targetLivingEntity = this.livingEntity();
        when(originalMob.getTarget()).thenReturn(targetLivingEntity);
        final NpcSnapshot npcSnapshot = this.snapshot(originalMob, 0);
        final Mob restoredMob = this.entity(Mob.class);
        this.maxHealth(restoredMob, 20.0D);
        final LivingEntity resolvedTargetLivingEntity = this.livingEntity();
        when(this.server.getEntity(targetLivingEntity.getUniqueId())).thenReturn(resolvedTargetLivingEntity);
        npcSnapshot.apply(restoredMob);

        verify(restoredMob).setTarget(resolvedTargetLivingEntity);
    }

    @Test
    void elapsedTicksAreComputedFromTheCaptureTimeAtFiftyMillisecondsPerTick() throws ReflectiveOperationException {
        final NpcSnapshot npcSnapshot = new NpcSnapshot(this.livingEntity());
        final long createdAt = System.currentTimeMillis() - 5_000L;
        this.setField(npcSnapshot, "createdAt", createdAt);
        final long earliestElapsedTicks = (System.currentTimeMillis() - createdAt) / 50L;
        final int elapsedTicks = npcSnapshot.getElapsedTicks();
        final long latestElapsedTicks = (System.currentTimeMillis() - createdAt) / 50L;

        assertTrue(elapsedTicks >= earliestElapsedTicks && elapsedTicks <= latestElapsedTicks);
    }

    @Test
    void otherCapturedLivingStateIsAppliedWithoutDecay() {
        final LivingEntity originalLivingEntity = this.livingEntity();
        when(originalLivingEntity.getAbsorptionAmount()).thenReturn(4.0D);
        when(originalLivingEntity.getRemainingAir()).thenReturn(123);
        when(originalLivingEntity.getArrowsInBody()).thenReturn(2);
        when(originalLivingEntity.getBeeStingersInBody()).thenReturn(3);
        when(originalLivingEntity.isGlowing()).thenReturn(true);
        when(originalLivingEntity.isInvisible()).thenReturn(true);
        when(originalLivingEntity.getVisualFire()).thenReturn(TriState.TRUE);
        final NpcSnapshot npcSnapshot = this.snapshot(originalLivingEntity, 500);
        final LivingEntity restoredLivingEntity = this.livingEntity();
        npcSnapshot.apply(restoredLivingEntity);

        verify(restoredLivingEntity).setAbsorptionAmount(4.0D);
        verify(restoredLivingEntity).setRemainingAir(123);
        verify(restoredLivingEntity).setArrowsInBody(2);
        verify(restoredLivingEntity).setBeeStingersInBody(3);
        verify(restoredLivingEntity).setGlowing(true);
        verify(restoredLivingEntity).setInvisible(true);
        verify(restoredLivingEntity).setVisualFire(TriState.TRUE);
    }

    private NpcSnapshot snapshot(final LivingEntity livingEntity, final int elapsedTicks) {
        final NpcSnapshot npcSnapshot = spy(new NpcSnapshot(livingEntity));
        doReturn(elapsedTicks).when(npcSnapshot).getElapsedTicks();
        return npcSnapshot;
    }

    private ItemStack itemStack(final Material material, final int damage) {
        final AtomicInteger damageAtomicInteger = new AtomicInteger(damage);
        final Damageable damageable = mock(Damageable.class);
        when(damageable.getDamage()).thenAnswer(_ -> damageAtomicInteger.get());
        doAnswer(invocationOnMock -> {
            damageAtomicInteger.set(invocationOnMock.getArgument(0));
            return null;
        }).when(damageable).setDamage(anyInt());
        final ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(material);
        when(itemStack.getItemMeta()).thenReturn(damageable);
        when(itemStack.clone()).thenAnswer(_ -> this.itemStack(material, damageAtomicInteger.get()));
        return itemStack;
    }
}
