package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.paper.plugin.framework.search.types.PaperWorldSearchEngine;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@UtilityClass
public class UtilWorld {

    private static final PaperWorldSearchEngine PAPER_WORLD_SEARCH_ENGINE = new PaperWorldSearchEngine();

    public static List<World> getWorlds() {
        return Bukkit.getServer().getWorlds();
    }

    public static Optional<World> getWorldById(final UUID id) {
        return Optional.ofNullable(Bukkit.getServer().getWorld(id));
    }

    public static Optional<World> getWorldByName(final String name) {
        return Optional.ofNullable(Bukkit.getServer().getWorld(name));
    }

    public static Optional<World> searchWorld(final CommandSender commandSender, final String input, final boolean inform, final Predicate<World> predicate) {
        return PAPER_WORLD_SEARCH_ENGINE.find(commandSender, input, inform, predicate);
    }

    public static Optional<World> searchWorld(final CommandSender commandSender, final String input, final boolean inform) {
        return searchWorld(commandSender, input, inform, null);
    }
}