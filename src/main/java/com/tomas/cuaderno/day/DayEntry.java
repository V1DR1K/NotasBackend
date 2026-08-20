package com.tomas.cuaderno.day;

import com.tomas.cuaderno.common.audit.AuditableEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name = "day_entries")
public class DayEntry extends AuditableEntity {
    @Column(name = "date", nullable = false) private LocalDate date;
    @Column(name = "status_code", nullable = false, length = 80) private String statusCode;
    @Column(name = "feeling", nullable = false, length = 120) private String feeling;
    @Column(columnDefinition = "TEXT", nullable = false) private String description;
    public LocalDate getDate() { return date; } public void setDate(LocalDate v) { date = v; }
    public String getStatusCode() { return statusCode; } public void setStatusCode(String v) { statusCode = v; }
    public String getFeeling() { return feeling; } public void setFeeling(String v) { feeling = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
}
