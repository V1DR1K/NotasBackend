package com.tomas.cuaderno.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public final class AuthCookie {
    public static final String ACCESS_TOKEN = "notes.access";
    public static final String REFRESH_TOKEN = "notes.refresh";

    private AuthCookie() {}

    public static String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
