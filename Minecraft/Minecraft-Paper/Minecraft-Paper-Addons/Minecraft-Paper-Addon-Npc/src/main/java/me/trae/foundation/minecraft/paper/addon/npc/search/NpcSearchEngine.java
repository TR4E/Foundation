package me.trae.foundation.minecraft.paper.addon.npc.search;

import me.trae.foundation.minecraft.paper.addon.npc.Npc;
import me.trae.foundation.minecraft.paper.addon.npc.services.NpcService;
import me.trae.foundation.minecraft.paper.plugin.framework.search.AbstractPaperSearchEngine;
import me.trae.foundation.utilities.UtilString;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public final class NpcSearchEngine extends AbstractPaperSearchEngine<Npc<?>> {

    public NpcSearchEngine(final NpcService npcService) {
        super("Npc Search", npcService::getNpcList);
    }

    @Override
    protected String getMatchName(final Npc<?> npc, final CommandSender commandSender) {
        return UtilString.clean(npc.getIdentifier());
    }

    @Override
    protected boolean isExact(final Npc<?> npc, final String result) {
        return npc.getIdentifier().equalsIgnoreCase(result);
    }

    @Override
    protected boolean isMatching(final Npc<?> npc, final String result) {
        return npc.getIdentifier().toLowerCase(Locale.ROOT).contains(result.toLowerCase(Locale.ROOT));
    }
}