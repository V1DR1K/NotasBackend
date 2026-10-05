package com.tomas.cuaderno.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class GeminiMarkdownDtos {
    private GeminiMarkdownDtos() {}

    public enum Kind { NOTE, TASK }

    public record Request(@NotNull Kind kind, String title, @NotBlank String content) {}

    public record Response(String markdown) {}
}
