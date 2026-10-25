package me.trae.foundation.minecraft.velocity.plugin.framework.search;

import com.velocitypowered.api.command.CommandSource;
import me.trae.foundation.minecraft.common.search.AbstractMinecraftSearchEngine;

import java.util.Collection;
import java.util.function.Supplier;

public abstract class AbstractVelocitySearchEngine<Type> extends AbstractMinecraftSearchEngine<Type, CommandSource> {

    public AbstractVelocitySearchEngine(final String name, final Supplier<Collection<? extends Type>> collectionSupplier) {
        super(name, collectionSupplier);
    }
}