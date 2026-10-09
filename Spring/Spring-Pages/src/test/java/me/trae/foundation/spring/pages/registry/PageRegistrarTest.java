package me.trae.foundation.spring.pages.registry;

import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.annotation.Render;
import me.trae.foundation.spring.pages.exception.PageRegistrationException;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PageRegistrarTest {

    @Test
    void registersTheRenderMethodAsAGetMapping() throws Exception {
        final RequestMappingHandlerMapping mapping = mapping();
        final ValidPage page = new ValidPage();

        new PageRegistrar(mapping).register(List.of(page));

        final Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod> entry = mapping.getHandlerMethods().entrySet().stream().findFirst().orElseThrow();
        assertEquals(java.util.Set.of("/orders/{id}"), entry.getKey().getPatternValues());
        assertEquals(java.util.Set.of(RequestMethod.GET), entry.getKey().getMethodsCondition().getMethods());
        assertSame(page, entry.getValue().getBean());
        assertEquals("render", entry.getValue().getMethod().getName());
    }

    @Test
    void rejectsAPageWithoutARenderMethod() throws Exception {
        final PageRegistrationException exception = assertThrows(PageRegistrationException.class, () -> new PageRegistrar(mapping()).register(List.of(new MissingRenderPage())));

        assertTrue(exception.getMessage().contains("declares no @Render method"));
        assertTrue(exception.getMessage().contains(MissingRenderPage.class.getName()));
    }

    @Test
    void rejectsAPageWithMoreThanOneRenderMethod() throws Exception {
        final PageRegistrationException exception = assertThrows(PageRegistrationException.class, () -> new PageRegistrar(mapping()).register(List.of(new MultipleRenderPage())));

        assertTrue(exception.getMessage().contains("declares 2 @Render methods, expected 1"));
    }

    @Test
    void rejectsARenderMethodWithTheWrongReturnType() throws Exception {
        final PageRegistrationException exception = assertThrows(PageRegistrationException.class, () -> new PageRegistrar(mapping()).register(List.of(new NonStringRenderPage())));

        assertTrue(exception.getMessage().contains("@Render method must return String"));
        assertTrue(exception.getMessage().contains(Method.class.getName()));
    }

    private static RequestMappingHandlerMapping mapping() {
        return new RequestMappingHandlerMapping();
    }

    private static final class ValidPage extends Page {

        private ValidPage() {
            super("/orders/{id}", null);
        }

        @Render
        public String render() {
            return "orders";
        }
    }

    private static final class MissingRenderPage extends Page {

        private MissingRenderPage() {
            super("/missing", null);
        }
    }

    private static final class MultipleRenderPage extends Page {

        private MultipleRenderPage() {
            super("/multiple", null);
        }

        @Render
        public String first() {
            return "first";
        }

        @Render
        public String second() {
            return "second";
        }
    }

    private static final class NonStringRenderPage extends Page {

        private NonStringRenderPage() {
            super("/invalid", null);
        }

        @Render
        public Method render() {
            return null;
        }
    }
}