package com.tomas.cuaderno.notes;

import com.tomas.cuaderno.common.audit.AuditableEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "notes")
public class Note extends AuditableEntity {
    @Column(nullable = false, length = 180) private String title;
    @Column(columnDefinition = "TEXT", nullable = false) private String body;
    @Column(name = "category_code", nullable = false, length = 80) private String categoryCode;
    @Column(name = "date", nullable = false) private LocalDate date;
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getBody() { return body; } public void setBody(String v) { body = v; }
    public String getCategoryCode() { return categoryCode; } public void setCategoryCode(String v) { categoryCode = v; }
    public LocalDate getDate() { return date; } public void setDate(LocalDate v) { date = v; }
}
