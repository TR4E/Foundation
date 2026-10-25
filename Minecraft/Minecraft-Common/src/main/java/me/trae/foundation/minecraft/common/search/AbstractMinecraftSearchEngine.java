package me.trae.foundation.minecraft.common.search;

import me.trae.foundation.minecraft.common.color.BaseUtilColor;
import me.trae.foundation.minecraft.common.color.ChatColor;
import me.trae.foundation.minecraft.common.message.BaseUtilMessage;
import me.trae.foundation.utilities.search.AbstractSearchEngine;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.awt.Color;
import java.util.Collection;
import java.util.function.Supplier;

public abstract class AbstractMinecraftSearchEngine<Type, Receiver extends Audience> extends AbstractSearchEngine<Type, Receiver> {

    protected AbstractMinecraftSearchEngine(final String name, final Supplier<Collection<? extends Type>> collectionSupplier) {
        super(name, collectionSupplier);
    }

    @Override
    protected final void message(final Receiver receiver, final String prefix, final String message) {
        BaseUtilMessage.message(receiver, prefix, message);
    }

    @Override
    protected final String getMatchSeparator() {
        return BaseUtilColor.serialize(ChatColor.GRAY.getColor(), MATCH_SEPARATOR);
    }

    @Override
    protected final String getInputFormat(final String string) {
        return BaseUtilColor.serialize(this.getInputColor(), this.escapeString(string));
    }

    @Override
    protected final String formatMatch(final Type type, final String name, final Receiver receiver) {
        return BaseUtilColor.serialize(this.getTypeColor(type, receiver), this.escapeString(name));
    }

    protected Color getInputColor() {
        return ChatColor.YELLOW.getColor();
    }

    protected Color getTypeColor(final Type type, final Receiver receiver) {
        return ChatColor.YELLOW.getColor();
    }

    private String escapeString(final String string) {
        return MiniMessage.miniMessage().escapeTags(string.replace("\\", "\\\\"));
    }
}