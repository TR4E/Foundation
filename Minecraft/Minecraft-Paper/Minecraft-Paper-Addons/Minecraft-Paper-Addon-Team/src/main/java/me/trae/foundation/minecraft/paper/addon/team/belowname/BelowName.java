package me.trae.foundation.minecraft.paper.addon.team.belowname;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.RenderType;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class BelowName {

    private final Component component;

    private final Criteria criteria;

    private final RenderType renderType;

    public static BelowName asComponent(final Component component) {
        return new BelowName(component, null, null);
    }

    public static BelowName asCriteria(final Criteria criteria) {
        return new BelowName(null, criteria, RenderType.INTEGER);
    }

    public static BelowName asHealth(final Component component, final RenderType renderType) {
        return new BelowName(component, Criteria.HEALTH, renderType);
    }

    public static BelowName asHealth() {
        return asCriteria(Criteria.HEALTH);
    }
}