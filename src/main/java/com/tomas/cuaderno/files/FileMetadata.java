package com.tomas.cuaderno.files;

import com.tomas.cuaderno.common.audit.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "files")
public class FileMetadata extends AuditableEntity {
    @Column(name = "folder_id") private UUID folderId;
    @Column(nullable = false, length = 255) private String name;
    @Column(nullable = false, length = 255) private String description;
    @Column(nullable = false, length = 32) private String extension;
    @Column(name = "mime_type", nullable = false, length = 150) private String mimeType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FileKind kind;
    @Column(name = "storage_key", nullable = false, unique = true) private UUID storageKey;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 128) private String checksum;
    public UUID getFolderId() { return folderId; } public void setFolderId(UUID v) { folderId = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public String getExtension() { return extension; } public void setExtension(String v) { extension = v; }
    public String getMimeType() { return mimeType; } public void setMimeType(String v) { mimeType = v; }
    public FileKind getKind() { return kind; } public void setKind(FileKind v) { kind = v; }
    public UUID getStorageKey() { return storageKey; } public void setStorageKey(UUID v) { storageKey = v; }
    public long getSizeBytes() { return sizeBytes; } public void setSizeBytes(long v) { sizeBytes = v; }
    public String getChecksum() { return checksum; } public void setChecksum(String v) { checksum = v; }
    public java.time.Instant getUploadedAt() { return getCreatedAt(); }
}
