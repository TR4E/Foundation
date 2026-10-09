package me.trae.foundation.spring.pages.registry;

import lombok.AllArgsConstructor;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.annotation.Render;
import me.trae.foundation.spring.pages.exception.PageRegistrationException;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodIntrospector;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
public final class PageRegistrar {

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    public void register(final List<Page> pageList) {
        for (final Page page : pageList) {
            this.requestMappingHandlerMapping.registerMapping(
                    RequestMappingInfo.paths(page.getRoute()).methods(RequestMethod.GET).build(),
                    page,
                    this.getRenderMethod(page)
            );
        }
    }

    private Method getRenderMethod(final Page page) {
        final Class<?> userClass = ClassUtils.getUserClass(AopUtils.getTargetClass(page));

        final Set<Method> methodSet = MethodIntrospector.selectMethods(userClass, (ReflectionUtils.MethodFilter) method -> method.isAnnotationPresent(Render.class) && !method.isSynthetic());

        if (methodSet.isEmpty()) {
            throw new PageRegistrationException("%s declares no @Render method".formatted(userClass.getName()));
        }

        if (methodSet.size() > 1) {
            throw new PageRegistrationException("%s declares %d @Render methods, expected 1".formatted(userClass.getName(), methodSet.size()));
        }

        final Method method = methodSet.iterator().next();

        if (method.getReturnType() != String.class) {
            throw new PageRegistrationException("%s @Render method must return String, found %s".formatted(userClass.getName(), method.getReturnType().getName()));
        }

        return method;
    }
}