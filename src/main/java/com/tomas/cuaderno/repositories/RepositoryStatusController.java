package com.tomas.cuaderno.repositories;

import com.tomas.cuaderno.repositories.RepositoryStatusDtos.Response;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryStatusController {
    private final RepositoryStatusService service;

    public RepositoryStatusController(RepositoryStatusService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Response> getStatus() {
        return noStore(service.getStatus());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Response> refreshStatus() {
        return noStore(service.refreshStatus());
    }

    private ResponseEntity<Response> noStore(Response response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
