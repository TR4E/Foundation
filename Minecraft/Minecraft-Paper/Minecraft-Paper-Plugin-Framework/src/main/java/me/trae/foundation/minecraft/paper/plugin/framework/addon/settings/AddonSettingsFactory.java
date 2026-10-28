package me.trae.foundation.minecraft.paper.plugin.framework.addon.settings;

import lombok.experimental.UtilityClass;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;

@UtilityClass
public class AddonSettingsFactory {

    public static <Settings extends AddonSettings> Settings create(final Class<Settings> type, final List<Settings> overrideList) {
        if (!overrideList.isEmpty()) {
            return overrideList.getLast();
        }

        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, InvocationHandler::invokeDefault));
    }
}