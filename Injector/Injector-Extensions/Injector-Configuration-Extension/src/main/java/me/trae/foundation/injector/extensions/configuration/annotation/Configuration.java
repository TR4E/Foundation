package me.trae.foundation.injector.extensions.configuration.annotation;

import me.trae.foundation.injector.extensions.configuration.enums.ConfigType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Configuration {

    String value();

    ConfigType type() default ConfigType.JSON;
}