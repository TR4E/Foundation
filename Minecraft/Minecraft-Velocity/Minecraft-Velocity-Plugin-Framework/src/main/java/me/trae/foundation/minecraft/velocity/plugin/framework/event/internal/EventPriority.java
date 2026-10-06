package me.trae.foundation.minecraft.velocity.plugin.framework.event.internal;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EventPriority {

    public static final short BASELINE = Short.MAX_VALUE;

    public static final short LOWEST = Short.MAX_VALUE - 1;

    public static final short LOW = Short.MAX_VALUE - 2;

    public static final short NORMAL = 0;

    public static final short HIGH = Short.MIN_VALUE + 2;

    public static final short HIGHEST = Short.MIN_VALUE + 1;

    public static final short MONITOR = Short.MIN_VALUE;
}