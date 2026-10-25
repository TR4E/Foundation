package me.trae.foundation.minecraft.paper.plugin.framework.search;

import me.trae.foundation.minecraft.common.search.AbstractMinecraftSearchEngine;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.function.Supplier;

public abstract class AbstractPaperSearchEngine<Type> extends AbstractMinecraftSearchEngine<Type, CommandSender> {

    public AbstractPaperSearchEngine(final String name, final Supplier<Collection<? extends Type>> collectionSupplier) {
        super(name, collectionSupplier);
    }
}