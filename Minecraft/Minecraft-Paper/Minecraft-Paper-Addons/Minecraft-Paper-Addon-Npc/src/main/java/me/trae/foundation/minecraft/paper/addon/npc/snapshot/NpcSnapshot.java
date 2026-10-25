package me.trae.foundation.minecraft.paper.addon.npc.snapshot;

import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEntity;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Getter
public class NpcSnapshot {

    private final long createdAt = System.currentTimeMillis();

    private final NpcAttributeSnapshot attributeSnapshot;
    private final NpcEquipmentSnapshot equipmentSnapshot;
    private final NpcGoalSnapshot goalSnapshot;

    private final double health, absorptionAmount;
    private final int fireTicks, freezeTicks, noDamageTicks, remainingAir, arrowsInBody, beeStingersInBody;
    private final boolean glowing, invisible;
    private final TriState visualFire;
    private final Collection<PotionEffect> potionEffectCollection;
    private final Set<String> scoreboardTagSet;
    private final PersistentDataContainer persistentDataContainer;
    private final UUID targetId;

    public NpcSnapshot(final LivingEntity livingEntity) {
        this.attributeSnapshot = new NpcAttributeSnapshot(livingEntity);
        this.equipmentSnapshot = new NpcEquipmentSnapshot(livingEntity);
        this.goalSnapshot = new NpcGoalSnapshot(livingEntity);

        this.health = livingEntity.getHealth();
        this.absorptionAmount = livingEntity.getAbsorptionAmount();

        this.fireTicks = livingEntity.getFireTicks();
        this.freezeTicks = livingEntity.getFreezeTicks();
        this.noDamageTicks = livingEntity.getNoDamageTicks();
        this.remainingAir = livingEntity.getRemainingAir();
        this.arrowsInBody = livingEntity.getArrowsInBody();
        this.beeStingersInBody = livingEntity.getBeeStingersInBody();

        this.glowing = livingEntity.isGlowing();
        this.invisible = livingEntity.isInvisible();
        this.visualFire = livingEntity.getVisualFire();

        this.potionEffectCollection = List.copyOf(livingEntity.getActivePotionEffects());
        this.scoreboardTagSet = Set.copyOf(livingEntity.getScoreboardTags());

        this.persistentDataContainer = livingEntity.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();

        livingEntity.getPersistentDataContainer().copyTo(this.persistentDataContainer, true);

        this.targetId = livingEntity instanceof final Mob mob && mob.getTarget() != null ? mob.getTarget().getUniqueId() : null;
    }

    public void apply(final LivingEntity livingEntity) {
        final int elapsedTicks = this.getElapsedTicks();

        this.attributeSnapshot.apply(livingEntity);

        livingEntity.setHealth(Math.clamp(this.health, 1.0D, UtilEntity.getAttributeValue(livingEntity, Attribute.MAX_HEALTH, this.health)));
        livingEntity.setAbsorptionAmount(this.absorptionAmount);

        livingEntity.setFireTicks(Math.max(0, this.fireTicks - elapsedTicks));
        livingEntity.setFreezeTicks(Math.max(0, this.freezeTicks - elapsedTicks));
        livingEntity.setNoDamageTicks(Math.max(0, this.noDamageTicks - elapsedTicks));
        livingEntity.setRemainingAir(this.remainingAir);
        livingEntity.setArrowsInBody(this.arrowsInBody);
        livingEntity.setBeeStingersInBody(this.beeStingersInBody);

        livingEntity.setGlowing(this.glowing);
        livingEntity.setInvisible(this.invisible);
        livingEntity.setVisualFire(this.visualFire);

        this.equipmentSnapshot.apply(livingEntity);

        this.goalSnapshot.apply(livingEntity);

        this.potionEffectCollection.stream()
                .map(potionEffect -> this.decay(potionEffect, elapsedTicks))
                .filter(Objects::nonNull)
                .forEach(livingEntity::addPotionEffect);

        this.scoreboardTagSet.forEach(livingEntity::addScoreboardTag);

        this.persistentDataContainer.copyTo(livingEntity.getPersistentDataContainer(), true);

        this.applyTarget(livingEntity);
    }

    protected final int getElapsedTicks() {
        return (int) ((System.currentTimeMillis() - this.createdAt) / 50L);
    }

    private PotionEffect decay(final PotionEffect potionEffect, final int elapsedTicks) {
        if (potionEffect.isInfinite()) {
            return potionEffect;
        }

        final int duration = potionEffect.getDuration() - elapsedTicks;

        if (duration <= 0) {
            return null;
        }

        return potionEffect.withDuration(duration);
    }

    private void applyTarget(final LivingEntity livingEntity) {
        if (this.targetId == null || !(livingEntity instanceof final Mob mob)) {
            return;
        }

        if (!(Bukkit.getServer().getEntity(this.targetId) instanceof final LivingEntity target)) {
            return;
        }

        mob.setTarget(target);
    }
}