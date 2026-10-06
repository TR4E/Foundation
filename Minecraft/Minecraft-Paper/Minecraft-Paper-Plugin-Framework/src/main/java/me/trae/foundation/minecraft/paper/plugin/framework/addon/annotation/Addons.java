package me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation;

import me.trae.foundation.minecraft.paper.plugin.framework.addon.Addon;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Addons {

    Class<? extends Addon>[] value();
}