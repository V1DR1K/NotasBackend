package com.tomas.cuaderno.auth;

import com.tomas.cuaderno.auth.CentralAuthDtos.CentralUser;
import com.tomas.cuaderno.auth.CentralAuthDtos.MeResponse;
import com.tomas.cuaderno.auth.CentralAuthDtos.RegistrationResponse;
import com.tomas.cuaderno.auth.CentralAuthDtos.TokenResponse;
import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.common.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CentralAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> APPLICATIONS = Set.of("notes", "whatplan", "scalegrams");

    private final CentralAuthUserRepository users;
    private final CentralAuthRefreshTokenRepository refreshTokens;
    private final CentralUserAppAccessRepository appAccess;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties properties;

    public CentralAuthService(CentralAuthUserRepository users,
                              CentralAuthRefreshTokenRepository refreshTokens,
                              CentralUserAppAccessRepository appAccess,
                              PasswordEncoder passwordEncoder,
                              JwtService jwtService,
                              AuthProperties properties) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.appAccess = appAccess;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public TokenResponse login(String username, String password, String requestedApp) {
        String appCode = requireApplication(requestedApp);
        CentralAuthUser user = users.findByUsernameIgnoreCaseAndDeletedAtIsNull(username.trim())
                .filter(CentralAuthUser::isActive)
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) throw invalidCredentials();
        CentralUserAppAccess access = requireAccess(user, appCode);
        user.markLogin();
        return issue(user, access);
    }

    @Transactional
    public RegistrationResponse register(String requestedUsername, String password, String requestedApp) {
        String appCode = requireApplication(requestedApp);
        String username = normalizeRegistrationUsername(requestedUsername);
        if (users.existsByUsernameIgnoreCase(username)) throw registrationFailed();

        CentralAuthUser user;
        try {
            user = users.saveAndFlush(CentralAuthUser.create(username, passwordEncoder.encode(password), false));
        } catch (DataIntegrityViolationException duplicateUsername) {
            throw registrationFailed();
        }
        for (String app : APPLICATIONS) {
            CentralAppAccessStatus status = app.equals(appCode)
                    ? CentralAppAccessStatus.PENDING : CentralAppAccessStatus.NONE;
            appAccess.save(CentralUserAppAccess.create(user.getId(), app, "USER", status));
        }
        return new RegistrationResponse(user.getId(), user.getUsername(), appCode,
                CentralAppAccessStatus.PENDING,
                "Solicitud enviada. Vas a poder ingresar cuando aprueben el acceso.");
    }

    @Transactional
    public TokenResponse refresh(String rawToken, String requestedApp) {
        String appCode = requireApplication(requestedApp);
        CentralAuthRefreshToken token = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(this::invalidRefresh);
        CentralAuthUser user = token.getUser();
        if (!token.usable() || !user.isActive() || !token.belongsToApp(appCode)) throw invalidRefresh();
        CentralUserAppAccess access = requireAccess(user, appCode);
        token.revoke();
        return issue(user, access);
    }

    @Transactional
    public void logout(String rawToken, String requestedApp) {
        if (rawToken == null || rawToken.isBlank()) return;
        CentralAuthRefreshToken token = refreshTokens.findByTokenHash(hash(rawToken)).orElse(null);
        if (token == null) return;
        String appCode = requestedApp == null || requestedApp.isBlank() ? null : requireApplication(requestedApp);
        if (token.belongsToApp(appCode) || token.getClientApp() == null || appCode == null) token.revoke();
    }

    @Transactional(readOnly = true)
    public MeResponse me(String accessToken, String requestedApp) {
        IdentityContext context = activeContext(accessToken, requestedApp);
        return new MeResponse(context.user().getId(), context.user().getUsername(),
                context.user().isMustChangePassword(), context.access().getRole());
    }

    @Transactional
    public void changePassword(String accessToken, String currentPassword, String newPassword, String requestedApp) {
        IdentityContext context = activeContext(accessToken, requestedApp);
        CentralAuthUser user = context.user();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new org.springframework.security.authentication.BadCredentialsException("La contraseña actual no es correcta.");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokens.findAllByUser(user).forEach(CentralAuthRefreshToken::revoke);
    }

    public static String requireApplication(String requestedApp) {
        String appCode = requestedApp == null ? "" : requestedApp.trim().toLowerCase(Locale.ROOT);
        if (!APPLICATIONS.contains(appCode)) throw new AccessDeniedException("La aplicación solicitada no está habilitada.");
        return appCode;
    }

    private IdentityContext activeContext(String accessToken, String requestedApp) {
        JwtService.TokenIdentity identity = jwtService.identity(accessToken);
        String appCode = identity.clientApp() == null || identity.clientApp().isBlank()
                ? requireApplication(requestedApp) : requireApplication(identity.clientApp());
        if (requestedApp != null && !requestedApp.isBlank() && !appCode.equals(requireApplication(requestedApp))) {
            throw new AccessDeniedException("La sesión pertenece a otra aplicación.");
        }
        CentralAuthUser user = users.findById(identity.userId()).filter(CentralAuthUser::isActive)
                .orElseThrow(() -> new BadCredentialsException("La sesión central no está activa."));
        return new IdentityContext(user, requireAccess(user, appCode));
    }

    private static String normalizeRegistrationUsername(String value) {
        String username = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,79}")) {
            throw new BadRequestException("Us\u00e1 entre 3 y 80 letras, n\u00fameros, puntos, guiones o guiones bajos.");
        }
        return username;
    }

    private BadRequestException registrationFailed() {
        return new BadRequestException("No se pudo completar el registro.");
    }

    private CentralUserAppAccess requireAccess(CentralAuthUser user, String appCode) {
        CentralUserAppAccess access = appAccess.findByUserIdAndAppCode(user.getId(), appCode)
                .orElseThrow(() -> new AccessDeniedException("Esta cuenta no tiene acceso a " + appCode + "."));
        if (access.getStatus() == CentralAppAccessStatus.PENDING) {
            throw new AccessDeniedException("Tu solicitud de acceso a " + appCode + " está pendiente de aprobación.");
        }
        if (access.getStatus() == CentralAppAccessStatus.REJECTED) {
            throw new AccessDeniedException("Tu solicitud de acceso a " + appCode + " fue rechazada.");
        }
        if (!access.isEnabled()) throw new AccessDeniedException("Esta cuenta no tiene acceso a " + appCode + ".");
        return access;
    }

    private TokenResponse issue(CentralAuthUser user, CentralUserAppAccess access) {
        String rawRefresh = randomToken();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(properties.getRefreshTokenDays()));
        refreshTokens.save(CentralAuthRefreshToken.create(user, hash(rawRefresh), expiresAt, access.getAppCode()));
        String accessToken = jwtService.issueAccessToken(user, access.getAppCode(), access.getRole());
        return new TokenResponse(accessToken, rawRefresh, "Bearer", jwtService.expiresInSeconds(), response(user, access.getRole()));
    }

    public static CentralUser response(CentralAuthUser user, String role) {
        return new CentralUser(user.getId(), user.getUsername(), user.getStatus(), user.getCreatedAt(),
                user.getLastLoginAt(), user.isMustChangePassword(), role);
    }

    private static String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Usuario o contraseña incorrectos.");
    }

    private BadCredentialsException invalidRefresh() {
        return new BadCredentialsException("La sesión de renovación no es válida o venció.");
    }

    private record IdentityContext(CentralAuthUser user, CentralUserAppAccess access) {}
}
