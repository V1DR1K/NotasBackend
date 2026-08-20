package com.tomas.cuaderno.common.security;

import com.tomas.cuaderno.common.errors.ForbiddenException;
import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}
    public static UUID id() {
        Object value = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (value instanceof AppPrincipal principal) return principal.id();
        throw new ForbiddenException("Authenticated user required");
    }
}
