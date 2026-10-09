package me.trae.foundation.spring.pages.navbar;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.utility.UtilRequestPath;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.registry.PageRegistry;
import me.trae.foundation.spring.pages.role.PageAccessResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@AllArgsConstructor
public final class NavbarService {

    private final PageRegistry pageRegistry;
    private final PageAccessResolver pageAccessResolver;

    public List<NavbarEntry> getNavbarEntryList(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
        final String path = UtilRequestPath.getCanonicalPath(httpServletRequest);

        final List<NavbarEntry> entryList = new ArrayList<>();

        for (final Page page : this.pageRegistry.getPageList()) {
            if (!(page instanceof final Navbar navbar)) {
                continue;
            }

            if (!this.isVisible(httpServletRequest, httpServletResponse, page)) {
                continue;
            }

            entryList.add(new NavbarEntry(page, navbar, UtilRequestPath.matchesPrefix(path, UtilRequestPath.canonicalise(page.getBaseRoute()))));
        }

        entryList.sort(Comparator.comparingInt(NavbarEntry::getPosition));

        return List.copyOf(entryList);
    }

    private boolean isVisible(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse, final Page page) {
        if (page.isPublic()) {
            return true;
        }

        return this.pageAccessResolver.hasRole(httpServletRequest, httpServletResponse, page.getRequiredRole());
    }
}