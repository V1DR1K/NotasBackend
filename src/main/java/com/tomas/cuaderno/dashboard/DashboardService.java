package com.tomas.cuaderno.dashboard;

import com.tomas.cuaderno.day.DayDtos;
import com.tomas.cuaderno.day.DayService;
import com.tomas.cuaderno.calendar.CalendarEventDtos;
import com.tomas.cuaderno.calendar.CalendarEventService;
import com.tomas.cuaderno.files.*;
import com.tomas.cuaderno.finance.*;
import com.tomas.cuaderno.notes.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service public class DashboardService {
    private final DayService days; private final NoteService notes; private final FinanceService finance; private final FinanceAccountService accounts; private final FileService files; private final CalendarEventService calendar;
    public DashboardService(DayService days, NoteService notes, FinanceService finance, FinanceAccountService accounts, FileService files, CalendarEventService calendar) { this.days = days; this.notes = notes; this.finance = finance; this.accounts = accounts; this.files = files; this.calendar = calendar; }
    public DashboardDtos.Response get(UUID owner) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.withDayOfMonth(1), to = from.withDayOfMonth(from.lengthOfMonth());
        var summary = finance.summary(owner, from, to);
        var recentNotes = notes.list(owner, null, null, null, null, null, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "updatedAt"))).content();
        var recentFiles = files.list(owner, new FileDtos.Filters(null, null, null, null, null), PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "updatedAt"))).content();
        var recentDays = days.list(owner, null, null, null, null, null, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "updatedAt"))).content();
        var recentMovements = finance.list(owner, null, null, null, null, null, null, null, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "updatedAt"))).content();
        var upcomingEvents = calendar.list(owner, null, today, today.plusDays(14), null, PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "date"))).content();
        var dayStats = new DashboardDtos.DayStats(days.count(owner, from, to), days.countPendingAnalysis(owner, from, to), days.today(owner, today));
        var financeSnapshot = financeSnapshot(summary, accounts.list(owner));
        var storage = files.storageUsage(owner);
        var activity = recentActivity(recentNotes, recentFiles, recentDays, recentMovements, upcomingEvents);
        return new DashboardDtos.Response(days.count(owner), notes.count(owner), files.count(owner), finance.count(owner), summary, recentNotes, recentFiles, recentDays, recentMovements, dayStats, financeSnapshot, new DashboardDtos.StorageUsage(storage.usedBytes(), storage.quotaBytes()), upcomingEvents, activity);
    }

    private DashboardDtos.FinanceSnapshot financeSnapshot(FinanceDtos.Summary summary, List<FinanceDtos.AccountResponse> accountList) {
        BigDecimal rate = summary.exchangeRate().average();
        BigDecimal cash = accountList.stream().filter(account -> account.type() == FinanceAccountType.CASH).map(FinanceDtos.AccountResponse::balanceArs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invested = accountList.stream().filter(account -> account.type() == FinanceAccountType.INVESTMENT || account.type() == FinanceAccountType.CRYPTO).map(FinanceDtos.AccountResponse::balanceArs).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardDtos.FinanceSnapshot(money(cash, rate), money(invested, rate), summary.income(), summary.expense(), summary.exchangeRate());
    }

    private FinanceDtos.MoneyResponse money(BigDecimal ars, BigDecimal rate) {
        return new FinanceDtos.MoneyResponse(ars.setScale(2, RoundingMode.HALF_UP), ars.divide(rate, 2, RoundingMode.HALF_UP), rate);
    }

    private List<DashboardDtos.RecentActivity> recentActivity(List<NoteDtos.Response> notes, List<FileDtos.FileResponse> files, List<DayDtos.Response> days, List<FinanceDtos.Response> movements, List<CalendarEventDtos.Response> events) {
        List<DashboardDtos.RecentActivity> result = new ArrayList<>();
        notes.forEach(note -> result.add(new DashboardDtos.RecentActivity("notes", note.id(), "Nota guardada", note.title(), note.date(), note.updatedAt())));
        files.forEach(file -> result.add(new DashboardDtos.RecentActivity("files", file.id(), "Archivo agregado", file.name(), file.uploadedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate(), file.updatedAt())));
        days.forEach(day -> result.add(new DashboardDtos.RecentActivity("day", day.id(), "Día registrado", day.description(), day.date(), day.updatedAt())));
        movements.forEach(movement -> result.add(new DashboardDtos.RecentActivity("finances", movement.id(), "Movimiento financiero", movement.item().label(), movement.date(), movement.updatedAt())));
        events.forEach(event -> result.add(new DashboardDtos.RecentActivity("calendar", event.id(), "Evento agendado", event.description(), event.date(), event.updatedAt())));
        return result.stream().sorted(Comparator.comparing(DashboardDtos.RecentActivity::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))).limit(8).toList();
    }
}
