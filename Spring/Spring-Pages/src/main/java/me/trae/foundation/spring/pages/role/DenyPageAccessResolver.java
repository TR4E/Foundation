package me.trae.foundation.spring.pages.role;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class DenyPageAccessResolver implements PageAccessResolver {

    @Override
    public boolean isAuthenticated(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
        return false;
    }

    @Override
    public boolean hasRole(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse, final PageRole pageRole) {
        return false;
    }
}