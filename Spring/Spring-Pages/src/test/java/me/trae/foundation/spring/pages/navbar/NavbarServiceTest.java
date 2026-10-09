package me.trae.foundation.spring.pages.navbar;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.registry.PageRegistry;
import me.trae.foundation.spring.pages.registry.PageRegistryBuilder;
import me.trae.foundation.spring.pages.role.PageAccessResolver;
import me.trae.foundation.spring.pages.role.PageRole;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NavbarServiceTest {

    private static final PageRole MEMBER = () -> "MEMBER";

    @Test
    void returnsVisibleNavbarPagesSortedAndMarksTheMatchingPrefixActive() {
        final NavPage account = new NavPage("/account/{section}", MEMBER, 20, "Account");
        final NavPage home = new NavPage("/", null, 1, "Home");
        final NavPage orders = new NavPage("/orders", null, 10, "Orders");
        final Page hiddenNonNavbar = new Page("/internal", null) {};
        final PageRegistry registry = new PageRegistry();
        registry.register(PageRegistryBuilder.build(List.of(account, hiddenNonNavbar, orders, home), List.of()));

        final List<NavbarEntry> entries = new NavbarService(registry, new FixedResolver(true)).getNavbarEntryList(
                new MockHttpServletRequest("GET", "/account/security"), new MockHttpServletResponse()
        );

        assertEquals(List.of("Home", "Orders", "Account"), entries.stream().map(NavbarEntry::getName).toList());
        assertFalse(entries.get(0).isActive());
        assertFalse(entries.get(1).isActive());
        assertTrue(entries.get(2).isActive());
        assertEquals("/account", entries.get(2).getRoute());
        assertSame(MEMBER, entries.get(2).getRequiredRole());
    }

    @Test
    void omitsProtectedPagesWhenTheViewerLacksTheirRole() {
        final PageRegistry registry = new PageRegistry();
        registry.register(PageRegistryBuilder.build(List.of(
                new NavPage("/public", null, 1, "Public"),
                new NavPage("/member", MEMBER, 2, "Member")
        ), List.of()));

        final List<NavbarEntry> entries = new NavbarService(registry, new FixedResolver(false)).getNavbarEntryList(
                new MockHttpServletRequest("GET", "/public"), new MockHttpServletResponse()
        );

        assertEquals(List.of("Public"), entries.stream().map(NavbarEntry::getName).toList());
        assertTrue(entries.getFirst().isActive());
    }

    private static final class NavPage extends Page implements Navbar {

        private final int position;
        private final String name;

        private NavPage(final String route, final PageRole requiredRole, final int position, final String name) {
            super(route, requiredRole);
            this.position = position;
            this.name = name;
        }

        @Override
        public int getNavbarPosition() {
            return this.position;
        }

        @Override
        public String getNavbarName() {
            return this.name;
        }

        @Override
        public List<String> getNavbarDescription() {
            return List.of(this.name + " description");
        }
    }

    private record FixedResolver(boolean allowed) implements PageAccessResolver {

        @Override
        public boolean isAuthenticated(final HttpServletRequest request, final HttpServletResponse response) {
            return this.allowed;
        }

        @Override
        public boolean hasRole(final HttpServletRequest request, final HttpServletResponse response, final PageRole pageRole) {
            return this.allowed;
        }
    }
}