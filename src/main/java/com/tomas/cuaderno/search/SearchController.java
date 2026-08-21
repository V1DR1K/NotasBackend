package com.tomas.cuaderno.search;

import com.tomas.cuaderno.common.security.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService service;
    public SearchController(SearchService service) { this.service = service; }
    @GetMapping public List<SearchDtos.Result> search(@RequestParam String q) { return service.search(CurrentUser.id(), q); }
}
