package com.tomas.cuaderno.auth;

import com.tomas.cuaderno.auth.CentralAuthDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class CentralUserAdminController {
    private final CentralUserAdministrationService users;

    public CentralUserAdminController(CentralUserAdministrationService users) { this.users = users; }

    @GetMapping
    public List<UserAdminResponse> list() { return users.list(); }

    @PostMapping
    public ResponseEntity<UserAdminResponse> create(@Valid @RequestBody CreateUserRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(users.create(body));
    }

    @PutMapping("/{userId}")
    public UserAdminResponse update(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest body) {
        return users.update(userId, body);
    }

    @PutMapping("/{userId}/applications")
    public UserAdminResponse updateApplications(@PathVariable UUID userId, @Valid @RequestBody UpdateApplicationsRequest body) {
        return users.updateApplications(userId, body);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@PathVariable UUID userId) {
        users.delete(userId);
        return ResponseEntity.noContent().build();
    }
}
