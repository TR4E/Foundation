package me.trae.foundation.minecraft.paper.addon.item;

import lombok.experimental.UtilityClass;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.plugin.framework.command.Suggestions;
import me.trae.foundation.utilities.UtilString;

import java.util.List;
import java.util.function.Predicate;

@UtilityClass
public class ItemSuggestions {

    public static List<String> items(final Predicate<CustomItem> predicate, final String arg) {
        return Suggestions.filter(
                Injector.INSTANCE.get(ItemService.class).getItems(),
                predicate,
                customItem -> UtilString.clean(customItem.getNamespace()).replace(" ", "_"),
                arg
        );
    }
}