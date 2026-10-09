package me.trae.foundation.spring.pages.navbar;

import java.util.Collections;
import java.util.List;

public interface Navbar {

    int getNavbarPosition();

    String getNavbarName();

    default List<String> getNavbarDescription() {
        return Collections.emptyList();
    }
}