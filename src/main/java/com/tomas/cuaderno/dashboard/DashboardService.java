package com.tomas.cuaderno.dashboard;

import com.tomas.cuaderno.day.DayDtos;
import com.tomas.cuaderno.day.DayService;
import com.tomas.cuaderno.files.*;
import com.tomas.cuaderno.finance.*;
import com.tomas.cuaderno.notes.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service public class DashboardService {
    private final DayService days; private final NoteService notes; private final FinanceService finance; private final FileService files;
    public DashboardService(DayService days, NoteService notes, FinanceService finance, FileService files) { this.days = days; this.notes = notes; this.finance = finance; this.files = files; }
    public DashboardDtos.Response get(UUID owner) {
        LocalDate from = LocalDate.now().withDayOfMonth(1), to = from.withDayOfMonth(from.lengthOfMonth());
        return new DashboardDtos.Response(days.count(owner), notes.count(owner), files.count(owner), finance.count(owner), finance.summary(owner, from, to), notes.list(owner, null, null, null, null, null, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "date"))).content(), files.list(owner, new FileDtos.Filters(null, null, null, null, null), PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"))).content(), days.list(owner, null, null, null, null, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "date"))).content(), finance.list(owner, null, null, null, null, null, null, null, null, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "date"))).content());
    }
}
