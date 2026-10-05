package com.tomas.cuaderno.ai;

import com.tomas.cuaderno.ai.GeminiMarkdownDtos.Request;
import com.tomas.cuaderno.ai.GeminiMarkdownDtos.Response;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class GeminiMarkdownController {
    private final GeminiMarkdownService service;

    public GeminiMarkdownController(GeminiMarkdownService service) { this.service = service; }

    @PostMapping("/markdown")
    public Response format(@Valid @RequestBody Request request) { return service.format(request); }
}
