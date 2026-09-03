package com.tomas.cuaderno.dashboard;

import com.tomas.cuaderno.day.DayDtos;
import com.tomas.cuaderno.files.FileDtos;
import com.tomas.cuaderno.finance.FinanceDtos;
import com.tomas.cuaderno.notes.NoteDtos;
import com.tomas.cuaderno.calendar.CalendarEventDtos;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {}
    public record Response(long dayEntriesCount, long notesCount, long filesCount, long financeMovementsCount, FinanceDtos.Summary financeSummary, List<NoteDtos.Response> recentNotes, List<FileDtos.FileResponse> recentFiles, List<DayDtos.Response> recentDays, List<FinanceDtos.Response> recentMovements, DayStats dayStats, FinanceSnapshot financeSnapshot, StorageUsage storageUsage, List<CalendarEventDtos.Response> upcomingEvents, List<RecentActivity> recentActivity) {}
    public record DayStats(long monthEntries, long pendingAnalysis, DayDtos.Response today) {}
    public record FinanceSnapshot(FinanceDtos.MoneyResponse currentCash, FinanceDtos.MoneyResponse currentInvested, FinanceDtos.MoneyResponse monthIncome, FinanceDtos.MoneyResponse monthExpense, FinanceDtos.ExchangeRateResponse exchangeRate) {}
    public record StorageUsage(long usedBytes, long quotaBytes) {}
    public record RecentActivity(String section, UUID id, String title, String detail, LocalDate date, Instant updatedAt) {}
}
