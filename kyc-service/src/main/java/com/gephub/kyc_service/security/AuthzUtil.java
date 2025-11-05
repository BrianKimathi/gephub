package com.gephub.kyc_service.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AuthzUtil {
    @SuppressWarnings("unchecked")
    public static List<String> scopes(Jwt jwt) {
        Object s = jwt.getClaim("scopes");
        if (s instanceof Collection<?> c) {
            return c.stream().map(Object::toString).toList();
        }
        String scopeStr = jwt.getClaimAsString("scope");
        if (scopeStr != null) return List.of(scopeStr.split(" "));
        return List.of();
    }

    public static boolean hasScope(Jwt jwt, String required) {
        return scopes(jwt).contains(required);
    }

    public static String role(Jwt jwt) {
        String r = jwt.getClaimAsString("role");
        if (r != null) return r;
        List<String> roles = jwt.getClaimAsStringList("roles");
        return (roles != null && !roles.isEmpty()) ? roles.get(0) : null;
    }

    public static boolean roleAtLeast(String have, String required) {
        return rank(have) <= rank(required);
    }

    private static int rank(String r) {
        if (r == null) return 99;
        return switch (r.toUpperCase()) {
            case "OWNER" -> 0;
            case "ADMIN" -> 1;
            case "DEV" -> 2;
            case "READONLY" -> 3;
            default -> 99;
        };
    }

    public static UUID orgId(Jwt jwt) {
        String s = jwt.getClaimAsString("org_id");
        return s != null ? UUID.fromString(s) : null;
    }
}


