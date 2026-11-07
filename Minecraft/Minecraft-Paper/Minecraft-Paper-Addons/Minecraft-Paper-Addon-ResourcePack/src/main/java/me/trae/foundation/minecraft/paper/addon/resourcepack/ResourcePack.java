package me.trae.foundation.minecraft.paper.addon.resourcepack;

import lombok.Getter;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.minecraft.paper.addon.resourcepack.services.ResourcePackService;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackInfoLike;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.UUID;

public abstract class ResourcePack implements ResourcePackInfoLike {

    @Getter
    private final UUID uniqueId;

    @Getter
    private final String identifier;

    private ResourcePackInfo resourcePackInfo;

    public ResourcePack(final String uniqueId, final String identifier) {
        this.uniqueId = UUID.fromString(uniqueId);
        this.identifier = identifier;
    }

    @Override
    public final @NotNull ResourcePackInfo asResourcePackInfo() {
        if (this.resourcePackInfo == null) {
            this.revalidate();
        }

        return this.resourcePackInfo;
    }

    public final void sync(final Player player) {
        Injector.INSTANCE.get(ResourcePackService.class).sync(player);
    }

    public final void revalidate() {
        this.resourcePackInfo = ResourcePackInfo.resourcePackInfo(this.uniqueId, URI.create(this.getUrl()), this.getHash());
    }

    public final boolean isValidated() {
        return this.resourcePackInfo != null && this.resourcePackInfo.uri().toString().equals(this.getUrl()) && this.resourcePackInfo.hash().equals(this.getHash());
    }

    public void onLoading(final Player player) {
    }

    public void onLoaded(final Player player) {
    }

    public boolean canLoad() {
        return true;
    }

    public boolean canLoad(final Player player) {
        return true;
    }

    public boolean isRequired() {
        return false;
    }

    public abstract String getUrl();

    public abstract String getHash();
}