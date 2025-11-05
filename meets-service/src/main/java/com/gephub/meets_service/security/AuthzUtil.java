package com.gephub.meets_service.security;

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
        List<String> s = scopes(jwt);
        return s.contains(required) || s.contains("meets.*");
    }

    public static UUID orgId(Jwt jwt) {
        String s = jwt.getClaimAsString("org_id");
        return s != null ? UUID.fromString(s) : null;
    }
}

package com.gephub.meets_service.security;

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
        List<String> s = scopes(jwt);
        return s.contains(required) || s.contains("meets.*");
    }

    public static UUID orgId(Jwt jwt) {
        String s = jwt.getClaimAsString("org_id");
        return s != null ? UUID.fromString(s) : null;
    }
}


