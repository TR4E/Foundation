package me.trae.foundation.minecraft.paper.addon.damage.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.damage.enums.Stage;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;

@AllArgsConstructor
@Getter
public final class DamageSetupEvent extends CustomEvent {

    private final CustomDamageEvent damageEvent;
    private final Stage stage;
}