package me.trae.foundation.spring.pages.navbar;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.role.PageRole;

import java.util.List;

@AllArgsConstructor
@Getter
public final class NavbarEntry {

    private final int position;
    private final String name, route;
    private final List<String> description;
    private final boolean active;
    private final PageRole requiredRole;

    public NavbarEntry(final Page page, final Navbar navbar, final boolean active) {
        this(
                navbar.getNavbarPosition(),
                navbar.getNavbarName(),
                page.getBaseRoute(),
                navbar.getNavbarDescription(),
                active,
                page.getRequiredRole()
        );
    }
}