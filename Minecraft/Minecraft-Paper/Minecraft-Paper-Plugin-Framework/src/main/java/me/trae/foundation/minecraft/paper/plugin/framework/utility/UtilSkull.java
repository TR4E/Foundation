package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@UtilityClass
public class UtilSkull {

    public static void applyWithTexture(final ItemStack itemStack, final String texture) {
        final PlayerProfile playerProfile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)));

        playerProfile.setProperty(new ProfileProperty("textures", texture));

        itemStack.editMeta(SkullMeta.class, skullMeta -> skullMeta.setPlayerProfile(playerProfile));
    }

    public static void applyWithPlayerProfile(final ItemStack itemStack, final PlayerProfile playerProfile) {
        if (!playerProfile.isComplete() && !playerProfile.completeFromCache() && !playerProfile.complete(true)) {
            return;
        }

        itemStack.editMeta(SkullMeta.class, skullMeta -> skullMeta.setPlayerProfile(playerProfile));
    }

    public static void applyWithUniqueId(final ItemStack itemStack, final UUID uniqueId) {
        applyWithPlayerProfile(itemStack, Bukkit.createProfile(uniqueId));
    }

    public static void applyWithName(final ItemStack itemStack, final String name) {
        applyWithPlayerProfile(itemStack, Bukkit.createProfile(name));
    }
}