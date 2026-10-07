package me.trae.foundation.minecraft.velocity.plugin.framework.utility;

import com.velocitypowered.api.permission.PermissionSubject;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UtilPermission {

    @Getter
    @Setter
    private static String customWildcardPermission = "*";

    public static boolean hasPermission(final PermissionSubject permissionSubject, final String permission) {
        if (permission == null) {
            return true;
        }

        if (permissionSubject != null) {
            if (permissionSubject.hasPermission("*")) {
                return true;
            }

            if (permissionSubject.hasPermission(customWildcardPermission)) {
                return true;
            }

            return permissionSubject.hasPermission(permission);
        }

        return false;
    }
}