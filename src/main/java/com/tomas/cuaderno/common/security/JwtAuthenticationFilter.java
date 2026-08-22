package com.tomas.cuaderno.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserDetailsService users;
    private final SecurityProperties properties;
    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService users, SecurityProperties properties) { this.jwtService = jwtService; this.users = users; this.properties = properties; }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String token = null;
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) if (properties.getCookieName().equals(cookie.getName())) token = cookie.getValue();
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UUID id = jwtService.subject(token);
                AppPrincipal principal = (AppPrincipal) users.loadUserByUsername(id.toString());
                if (!principal.isEnabled()) throw new org.springframework.security.authentication.DisabledException("User is disabled");
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) { SecurityContextHolder.clearContext(); }
        }
        chain.doFilter(request, response);
    }
}
