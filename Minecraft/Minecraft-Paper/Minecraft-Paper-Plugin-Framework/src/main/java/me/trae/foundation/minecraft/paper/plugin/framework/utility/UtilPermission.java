package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import org.bukkit.permissions.Permissible;

@UtilityClass
public class UtilPermission {

    @Getter
    @Setter
    private static String customWildcardPermission = "*";

    public static boolean hasPermission(final Permissible permissible, final String permission) {
        if (permission == null) {
            return true;
        }

        if (permissible != null) {
            if (permissible.isOp()) {
                return true;
            }

            if (permissible.hasPermission("*")) {
                return true;
            }

            if (permissible.hasPermission(customWildcardPermission)) {
                return true;
            }

            return permissible.hasPermission(permission);
        }

        return false;
    }
}