package com.tomas.cuaderno.task;

import com.tomas.cuaderno.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tasks")
public class Task extends AuditableEntity {
    @Column(nullable = false, length = 180)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "category_code", nullable = false, length = 80)
    private String categoryCode;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getDetail() { return detail; }
    public void setDetail(String value) { detail = value; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus value) { status = value; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String value) { categoryCode = value; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate value) { dueDate = value; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant value) { completedAt = value; }
}
