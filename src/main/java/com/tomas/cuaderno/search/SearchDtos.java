package com.tomas.cuaderno.search;

import java.time.LocalDate;
import java.util.UUID;

public final class SearchDtos {
    private SearchDtos() {}
    public record Result(String section, UUID id, String title, String detail, LocalDate date) {}
}
