package com.tomas.cuaderno.dashboard;

import com.tomas.cuaderno.day.DayDtos;
import com.tomas.cuaderno.files.FileDtos;
import com.tomas.cuaderno.finance.FinanceDtos;
import com.tomas.cuaderno.notes.NoteDtos;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {}
    public record Response(long dayEntriesCount, long notesCount, long filesCount, long financeMovementsCount, FinanceDtos.Summary financeSummary, List<NoteDtos.Response> recentNotes, List<FileDtos.FileResponse> recentFiles, List<DayDtos.Response> recentDays, List<FinanceDtos.Response> recentMovements) {}
}
