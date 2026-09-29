package com.tomas.cuaderno.files;

import com.tomas.cuaderno.common.audit.AuditableEntity;
import jakarta.persistence.*;

@Entity @Table(name = "file_folders")
public class FileFolder extends AuditableEntity {
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "project_code", nullable = false, length = 80) private String projectCode = "personal";
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getProjectCode() { return projectCode; } public void setProjectCode(String v) { projectCode = v; }
}
