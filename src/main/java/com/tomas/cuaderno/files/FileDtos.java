package com.tomas.cuaderno.files;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class FileDtos {
    private FileDtos() {}
    public record CreateFolderRequest(@NotBlank @Size(max = 120) String name, @Size(max = 80) String projectCode) {}
    public record PatchFolderRequest(@NotBlank @Size(max = 120) String name) {}
    public record FolderResponse(UUID id, String name, long fileCount, Instant createdAt, Instant updatedAt, String projectCode) {}
    public record PatchFileRequest(@Size(max = 255) String name, @Size(max = 255) String description, UUID folderId, @Size(max = 80) String projectCode) {}
    public record FileResponse(UUID id, String name, String description, String extension, String mimeType, long sizeBytes, FileKind kind, FolderResponse folder, String downloadUrl, Instant uploadedAt, Instant updatedAt, String projectCode) {}
    public record Filters(UUID folderId, FileKind kind, String search, LocalDate from, LocalDate to, String projectCode) {}
}
