package me.trae.foundation.spring.pages.role;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface PageAccessResolver {

    boolean isAuthenticated(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse);

    boolean hasRole(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse, final PageRole pageRole);
}