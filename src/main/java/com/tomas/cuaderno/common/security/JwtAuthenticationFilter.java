package com.tomas.cuaderno.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import com.tomas.cuaderno.auth.AuthCookie;
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
    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService users) { this.jwtService = jwtService; this.users = users; }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null) {
            String cookie = AuthCookie.read(request, AuthCookie.ACCESS_TOKEN);
            if (cookie != null && !cookie.isBlank()) authorization = "Bearer " + cookie;
        }
        if (authorization != null && authorization.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String token = authorization.substring(7);
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
