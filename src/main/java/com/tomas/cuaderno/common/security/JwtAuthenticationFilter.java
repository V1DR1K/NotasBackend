package com.tomas.cuaderno.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import com.tomas.cuaderno.auth.AuthCookie;
import com.tomas.cuaderno.auth.CentralUserAppAccessRepository;
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
    private final CentralUserAppAccessRepository appAccess;
    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService users, CentralUserAppAccessRepository appAccess) {
        this.jwtService = jwtService;
        this.users = users;
        this.appAccess = appAccess;
    }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null) {
            String cookie = AuthCookie.read(request, AuthCookie.ACCESS_TOKEN);
            if (cookie != null && !cookie.isBlank()) authorization = "Bearer " + cookie;
        }
        if (authorization != null && authorization.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String token = authorization.substring(7);
                JwtService.TokenIdentity identity = jwtService.identity(token);
                UUID id = identity.userId();
                if (identity.clientApp() != null && !"notes".equals(identity.clientApp())) {
                    throw new org.springframework.security.access.AccessDeniedException("This token is scoped to another application");
                }
                var grant = appAccess.findByUserIdAndAppCode(id, "notes")
                        .filter(value -> value.isEnabled() && appAccess.hasActiveAccess(id, "notes"))
                        .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Notes access is disabled"));
                AppPrincipal principal = (AppPrincipal) users.loadUserByUsername(id.toString());
                if (!principal.isEnabled()) throw new org.springframework.security.authentication.DisabledException("User is disabled");
                AppPrincipal scopedPrincipal = new AppPrincipal(principal.id(), principal.username(), principal.password(), grant.getRole(), principal.enabled());
                var authentication = new UsernamePasswordAuthenticationToken(scopedPrincipal, null, scopedPrincipal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) { SecurityContextHolder.clearContext(); }
        }
        chain.doFilter(request, response);
    }
}
