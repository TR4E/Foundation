package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import io.papermc.paper.adventure.PaperAdventure;
import lombok.experimental.UtilityClass;
import org.bukkit.craftbukkit.CraftSound;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftVector;

@UtilityClass
public class UtilNms {

    public static net.minecraft.network.chat.Component toComponent(final net.kyori.adventure.text.Component component) {
        return PaperAdventure.asVanilla(component);
    }

    public static net.minecraft.world.entity.Entity toEntity(final org.bukkit.entity.Entity entity) {
        return CraftEntity.class.cast(entity).getHandle();
    }

    public static net.minecraft.world.entity.LivingEntity toLivingEntity(final org.bukkit.entity.LivingEntity livingEntity) {
        return CraftLivingEntity.class.cast(livingEntity).getHandle();
    }

    public static net.minecraft.server.level.ServerLevel toWorld(final org.bukkit.World world) {
        return CraftWorld.class.cast(world).getHandle();
    }

    public static net.minecraft.world.phys.Vec3 toVec3(final org.bukkit.util.Vector vector) {
        return CraftVector.toVec3(vector);
    }

    public static org.bukkit.util.Vector toVector(final net.minecraft.world.phys.Vec3 vec3) {
        return CraftVector.toBukkit(vec3);
    }

    public static net.minecraft.world.damagesource.DamageSource toDamageSource(final org.bukkit.damage.DamageSource damageSource) {
        return CraftDamageSource.class.cast(damageSource).getHandle();
    }

    public static net.minecraft.world.item.ItemStack toItemStack(final org.bukkit.inventory.ItemStack itemStack) {
        return CraftItemStack.asNMSCopy(itemStack);
    }

    public static org.bukkit.Sound toSound(final net.minecraft.sounds.SoundEvent soundEvent) {
        return CraftSound.minecraftToBukkit(soundEvent);
    }
}