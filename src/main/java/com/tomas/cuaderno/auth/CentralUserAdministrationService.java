package com.tomas.cuaderno.auth;

import com.tomas.cuaderno.auth.CentralAuthDtos.AppAccessRequest;
import com.tomas.cuaderno.auth.CentralAuthDtos.AppAccessResponse;
import com.tomas.cuaderno.auth.CentralAuthDtos.CreateUserRequest;
import com.tomas.cuaderno.auth.CentralAuthDtos.UpdateApplicationsRequest;
import com.tomas.cuaderno.auth.CentralAuthDtos.UpdateUserRequest;
import com.tomas.cuaderno.auth.CentralAuthDtos.UserAdminResponse;
import com.tomas.cuaderno.auth.CentralAuthDtos.CentralUser;
import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.common.errors.NotFoundException;
import com.tomas.cuaderno.auth.UserRepository;
import com.tomas.cuaderno.auth.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CentralUserAdministrationService {
    private static final List<String> APPLICATIONS = List.of("notes", "whatplan", "scalegrams");
    private static final Set<String> ROLES = Set.of("USER", "ADMIN");

    private final CentralAuthUserRepository users;
    private final CentralAuthRefreshTokenRepository refreshTokens;
    private final CentralUserAppAccessRepository appAccess;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository notesUsers;

    public CentralUserAdministrationService(CentralAuthUserRepository users,
                                            CentralAuthRefreshTokenRepository refreshTokens,
                                            CentralUserAppAccessRepository appAccess,
                                            PasswordEncoder passwordEncoder,
                                            UserRepository notesUsers) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.appAccess = appAccess;
        this.passwordEncoder = passwordEncoder;
        this.notesUsers = notesUsers;
    }

    @Transactional(readOnly = true)
    public List<UserAdminResponse> list() {
        List<CentralAuthUser> identities = users.findAllByDeletedAtIsNullOrderByCreatedAtDesc();
        if (identities.isEmpty()) return List.of();
        Map<UUID, List<CentralUserAppAccess>> grants = appAccess.findAllByUserIdIn(
                        identities.stream().map(CentralAuthUser::getId).toList())
                .stream().collect(Collectors.groupingBy(CentralUserAppAccess::getUserId));
        return identities.stream().map(user -> response(user, grants.getOrDefault(user.getId(), List.of()))).toList();
    }

    @Transactional
    public UserAdminResponse create(CreateUserRequest request) {
        String username = normalizeUsername(request.username());
        if (users.existsByUsernameIgnoreCase(username)) throw new BadRequestException("Ese nombre de usuario ya está en uso.");
        CentralAuthUser user = users.save(CentralAuthUser.create(username,
                passwordEncoder.encode(request.password()), request.mustChangePassword() == null || request.mustChangePassword()));
        for (String app : APPLICATIONS) {
            appAccess.save(CentralUserAppAccess.create(user.getId(), app, "USER", CentralAppAccessStatus.NONE));
        }
        return response(user, appAccess.findAllByUserIdIn(List.of(user.getId())));
    }

    @Transactional
    public UserAdminResponse update(UUID userId, UpdateUserRequest request) {
        CentralAuthUser user = activeUser(userId);
        if (request.username() != null && !request.username().isBlank()) {
            String username = normalizeUsername(request.username());
            if (!username.equalsIgnoreCase(user.getUsername()) && users.existsByUsernameIgnoreCase(username)) {
                throw new BadRequestException("Ese nombre de usuario ya está en uso.");
            }
            user.setUsername(username);
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.changePassword(passwordEncoder.encode(request.password()));
            revokeSessions(user);
        }
        if (request.mustChangePassword() != null) user.setMustChangePassword(request.mustChangePassword());
        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            if (!request.enabled()) ensureNotLastNotesAdmin(user.getId(), false, "USER");
            user.setEnabled(request.enabled());
            if (!request.enabled()) revokeSessions(user);
        }
        return response(user, appAccess.findAllByUserIdIn(List.of(user.getId())));
    }

    @Transactional
    public UserAdminResponse updateApplications(UUID userId, UpdateApplicationsRequest request) {
        CentralAuthUser user = activeUser(userId);
        Map<String, AppAccessRequest> updates = request.applications();
        if (updates == null || !updates.keySet().equals(Set.copyOf(APPLICATIONS))) {
            throw new BadRequestException("Incluí Notes, WhatPlan y ScaleGrams para guardar los accesos.");
        }
        Map<String, String> roles = updates.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> normalizeRole(entry.getValue())));
        AppAccessRequest notes = updates.get("notes");
        CentralAppAccessStatus notesStatus = normalizeStatus(notes);
        ensureNotLastNotesAdmin(user.getId(), notesStatus.grantsAccess(), roles.get("notes"));

        for (String app : APPLICATIONS) {
            AppAccessRequest update = updates.get(app);
            if (update == null || update.enabled() == null) throw new BadRequestException("El acceso de cada aplicación debe estar definido.");
            String role = roles.get(app);
            CentralAppAccessStatus status = normalizeStatus(update);
            CentralUserAppAccess grant = appAccess.findByUserIdAndAppCode(user.getId(), app)
                    .orElseGet(() -> CentralUserAppAccess.create(user.getId(), app, role, status));
            grant.update(role, status);
            appAccess.save(grant);
            if ("notes".equals(app)) {
                notesUsers.findByAuthUserId(user.getId()).ifPresent(local -> {
                    local.setRole(role);
                    notesUsers.save(local);
                });
            }
        }
        revokeSessions(user);
        return response(user, appAccess.findAllByUserIdIn(List.of(user.getId())));
    }

    @Transactional
    public void delete(UUID userId) {
        CentralAuthUser user = activeUser(userId);
        ensureNotLastNotesAdmin(user.getId(), false, "USER");
        user.logicalDelete();
        revokeSessions(user);
    }

    private CentralAuthUser activeUser(UUID id) {
        return users.findById(id).filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("No encontramos esa cuenta central."));
    }

    private void ensureNotLastNotesAdmin(UUID userId, boolean notesEnabled, String notesRole) {
        if (notesEnabled && "ADMIN".equals(notesRole)) return;
        CentralUserAppAccess current = appAccess.findByUserIdAndAppCode(userId, "notes").orElse(null);
        if (current == null || !current.isEnabled() || !"ADMIN".equals(current.getRole())) return;
        if (appAccess.countOtherActiveNotesAdmins(userId) == 0) {
            throw new BadRequestException("Dejá al menos una cuenta administradora activa en Notes.");
        }
    }

    private void revokeSessions(CentralAuthUser user) {
        refreshTokens.findAllByUser(user).forEach(CentralAuthRefreshToken::revoke);
    }

    private String normalizeRole(AppAccessRequest request) {
        if (request == null || request.role() == null) throw new BadRequestException("Elegí un rol para cada aplicación.");
        String role = request.role().trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(role)) throw new BadRequestException("El rol debe ser USER o ADMIN.");
        return role;
    }

    private CentralAppAccessStatus normalizeStatus(AppAccessRequest request) {
        if (request == null || request.enabled() == null) {
            throw new BadRequestException("El acceso de cada aplicación debe estar definido.");
        }
        CentralAppAccessStatus status = request.status() == null
                ? (request.enabled() ? CentralAppAccessStatus.APPROVED : CentralAppAccessStatus.NONE)
                : request.status();
        if (request.enabled() != status.grantsAccess()) {
            throw new BadRequestException("El estado de acceso no coincide con el permiso habilitado.");
        }
        return status;
    }

    private static String normalizeUsername(String value) {
        String username = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,79}")) {
            throw new BadRequestException("Usá entre 3 y 80 letras, números, puntos, guiones o guiones bajos.");
        }
        return username;
    }

    private static UserAdminResponse response(CentralAuthUser user, List<CentralUserAppAccess> grants) {
        Map<String, CentralUserAppAccess> byApp = grants.stream()
                .collect(Collectors.toMap(CentralUserAppAccess::getAppCode, Function.identity(), (left, right) -> left));
        List<AppAccessResponse> applications = new ArrayList<>(APPLICATIONS.size());
        for (String app : APPLICATIONS) {
            CentralUserAppAccess grant = byApp.get(app);
            applications.add(new AppAccessResponse(app, grant == null ? "USER" : grant.getRole(),
                    grant != null && grant.isEnabled(),
                    grant == null ? CentralAppAccessStatus.NONE : grant.getStatus()));
        }
        return new UserAdminResponse(user.getId(), user.getUsername(), user.getStatus(), user.getCreatedAt(),
                user.getLastLoginAt(), user.isMustChangePassword(), applications);
    }
}
