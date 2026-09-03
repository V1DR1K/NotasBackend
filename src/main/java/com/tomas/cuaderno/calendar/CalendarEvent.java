package com.tomas.cuaderno.calendar;

import com.tomas.cuaderno.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "calendar_events")
public class CalendarEvent extends AuditableEntity {
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "category_code", nullable = false, length = 80)
    private String categoryCode;

    @Column(nullable = false, length = 1000)
    private String description;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate value) { date = value; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String value) { categoryCode = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
}
