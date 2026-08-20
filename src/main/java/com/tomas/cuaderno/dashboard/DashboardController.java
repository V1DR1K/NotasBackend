package com.tomas.cuaderno.dashboard;

import com.tomas.cuaderno.common.security.CurrentUser;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/dashboard") public class DashboardController {
    private final DashboardService service; public DashboardController(DashboardService service) { this.service = service; }
    @GetMapping public DashboardDtos.Response get() { return service.get(CurrentUser.id()); }
}
