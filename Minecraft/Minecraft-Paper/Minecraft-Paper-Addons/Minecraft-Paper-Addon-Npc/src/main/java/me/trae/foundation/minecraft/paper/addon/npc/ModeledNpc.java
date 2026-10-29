package me.trae.foundation.minecraft.paper.addon.npc;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes;
import com.ticxo.modelengine.api.model.bone.type.NameTag;
import me.trae.foundation.minecraft.paper.addon.npc.types.NameableNpc;

import java.util.Objects;
import java.util.Optional;

public abstract class ModeledNpc<Entity extends org.bukkit.entity.Entity> extends Npc<Entity> {

    public ModeledNpc(final Class<Entity> entityClass, final String identifier) {
        super(entityClass, identifier);
    }

    @Override
    protected final void setupSpawn(final Entity entity) {
        final ActiveModel activeModel = ModelEngineAPI.createActiveModel(this.getModelId());

        activeModel.setScale(this.getModelScale());

        activeModel.setHitboxScale(this.getHitboxScale());

        final ModeledEntity modeledEntity = ModelEngineAPI.createModeledEntity(entity);

        modeledEntity.addModel(activeModel, this.overrideHitbox());

        modeledEntity.setBaseEntityVisible(this.isBaseEntityVisible());

        this.applyNameTag(entity, activeModel);

        this.onModelSpawn(entity, activeModel);
    }

    @Override
    protected final void setupRemove(final Entity entity) {
        if (!ModelEngineAPI.isModeledEntity(entity.getUniqueId())) {
            return;
        }

        final ModeledEntity modeledEntity = ModelEngineAPI.removeModeledEntity(entity.getUniqueId());
        if (modeledEntity == null) {
            return;
        }

        modeledEntity.destroy();
    }

    public final Optional<ModeledEntity> getModeledEntity(final Entity entity) {
        return Optional.ofNullable(ModelEngineAPI.getModeledEntity(entity.getUniqueId()));
    }

    public final Optional<ActiveModel> getActiveModel(final Entity entity) {
        return this.getModeledEntity(entity).flatMap(modeledEntity -> modeledEntity.getModel(this.getModelId()));
    }

    public final void playAnimation(final Entity entity, final String animation, final double lerpIn, final double lerpOut, final double speed, final boolean force) {
        this.getActiveModel(entity).ifPresent(activeModel -> activeModel.getAnimationHandler().playAnimation(animation, lerpIn, lerpOut, speed, force));
    }

    public final void playAnimation(final Entity entity, final String animation) {
        this.playAnimation(entity, animation, 0.0D, 0.0D, 1.0D, false);
    }

    public final void stopAnimation(final Entity entity, final String animation) {
        this.getActiveModel(entity).ifPresent(activeModel -> activeModel.getAnimationHandler().stopAnimation(animation));
    }

    public final boolean isPlayingAnimation(final Entity entity, final String animation) {
        return this.getActiveModel(entity).map(activeModel -> activeModel.getAnimationHandler().isPlayingAnimation(animation)).orElse(false);
    }

    protected void onModelSpawn(final Entity entity, final ActiveModel activeModel) {
    }

    public boolean isBaseEntityVisible() {
        return false;
    }

    public boolean overrideHitbox() {
        return true;
    }

    public double getModelScale() {
        return 1.0D;
    }

    public double getHitboxScale() {
        return 1.0D;
    }

    public abstract String getModelId();

    private void applyNameTag(final Entity entity, final ActiveModel activeModel) {
        if (!(this instanceof final NameableNpc nameableNpc)) {
            return;
        }

        final NameTag nameTag = activeModel.getBones().values().stream().map(modelBone -> modelBone.getBoneBehavior(BoneBehaviorTypes.NAMETAG).orElse(null)).filter(Objects::nonNull).findFirst().orElse(null);
        if (nameTag == null) {
            return;
        }

        entity.customName(null);

        entity.setCustomNameVisible(false);

        nameTag.setComponent(nameableNpc.getName());

        nameTag.setVisible(nameableNpc.isNameAlwaysVisible());
    }
}