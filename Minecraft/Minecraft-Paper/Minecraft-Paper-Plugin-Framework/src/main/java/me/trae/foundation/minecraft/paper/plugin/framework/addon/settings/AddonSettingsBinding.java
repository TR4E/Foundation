package me.trae.foundation.minecraft.paper.plugin.framework.addon.settings;

import lombok.AllArgsConstructor;
import me.trae.foundation.injector.api.Injector;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

@AllArgsConstructor
public final class AddonSettingsBinding<Settings extends AddonSettings> implements InvocationHandler {

    private final Class<Settings> type;
    private final Injector injector;
    private final Settings defaultSettings;

    public static <Settings extends AddonSettings> Settings create(final Class<Settings> type, final Injector injector) {
        final Settings defaultSettings = AddonSettingsFactory.create(type, List.of());

        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, new AddonSettingsBinding<>(type, injector, defaultSettings)));
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] arguments) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "%s binding".formatted(this.type.getName());
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        final List<Settings> overrideList = this.injector.getAll(this.type);

        final Settings settings = overrideList.isEmpty() ? this.defaultSettings : AddonSettingsFactory.create(this.type, overrideList);

        try {
            return method.invoke(settings, arguments);
        } catch (final InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}