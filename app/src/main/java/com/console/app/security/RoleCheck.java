package com.console.app.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public final class RoleCheck {

    private RoleCheck() {
    }

    public static boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "ADMINISTRADOR");
    }

    public static boolean canOperate(Authentication authentication) {
        return hasRole(authentication, "ADMINISTRADOR") || hasRole(authentication, "OPERADOR");
    }

    public static String perfil(Authentication authentication) {
        if (authentication == null) {
            return "LEITOR";
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .findFirst()
                .orElse("LEITOR");
    }

    private static boolean hasRole(Authentication authentication, String role) {
        if (authentication == null) {
            return false;
        }
        String expected = "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> expected.equals(a.getAuthority()));
    }
}
