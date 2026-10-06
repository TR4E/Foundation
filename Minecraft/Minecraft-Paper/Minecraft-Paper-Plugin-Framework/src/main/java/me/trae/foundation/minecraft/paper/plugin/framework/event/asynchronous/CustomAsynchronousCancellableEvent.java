package me.trae.foundation.minecraft.paper.plugin.framework.event.asynchronous;

import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;

public class CustomAsynchronousCancellableEvent extends CustomCancellableEvent {

    public CustomAsynchronousCancellableEvent() {
        super(true);
    }
}