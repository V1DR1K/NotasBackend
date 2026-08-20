package com.tomas.cuaderno.files;

import com.tomas.cuaderno.common.audit.AuditableEntity;
import jakarta.persistence.*;

@Entity @Table(name = "file_folders")
public class FileFolder extends AuditableEntity {
    @Column(nullable = false, length = 120) private String name;
    public String getName() { return name; } public void setName(String v) { name = v; }
}
