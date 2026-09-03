package com.tomas.cuaderno.files;

import com.tomas.cuaderno.common.errors.*;
import com.tomas.cuaderno.common.pagination.PageResponse;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service public class FileService {
    private final FileFolderRepository folders; private final FileMetadataRepository files; private final FileStorage storage; private final FileProperties properties; private final Map<UUID, Object> quotaLocks = new ConcurrentHashMap<>();
    public FileService(FileFolderRepository folders, FileMetadataRepository files, FileStorage storage, FileProperties properties) { this.folders = folders; this.files = files; this.storage = storage; this.properties = properties; }
    public PageResponse<FileDtos.FolderResponse> listFolders(UUID owner, Pageable page) {
        var result = folders.findByOwnerIdAndDeletedAtIsNull(owner, page);
        Map<UUID, Long> counts = folderCounts(owner, result.getContent().stream().map(FileFolder::getId).toList());
        return PageResponse.from(result.map(x -> folderResponse(x, counts.getOrDefault(x.getId(), 0L))));
    }
    @Transactional public FileDtos.FolderResponse createFolder(UUID owner, FileDtos.CreateFolderRequest request) { FileFolder folder = new FileFolder(); folder.setOwnerId(owner); folder.setName(request.name().trim()); return folderResponse(folders.save(folder), 0); }
    @Transactional public FileDtos.FolderResponse patchFolder(UUID owner, UUID id, FileDtos.PatchFolderRequest request) { FileFolder folder = folder(owner, id); folder.setName(request.name().trim()); return folderResponse(folder, files.countByOwnerIdAndFolderIdAndDeletedAtIsNull(owner, id)); }
    @Transactional public void deleteFolder(UUID owner, UUID id) { FileFolder folder = folder(owner, id); if (files.countByOwnerIdAndFolderIdAndDeletedAtIsNull(owner, id) > 0) throw new BadRequestException("Folder contains files"); folder.setDeletedAt(Instant.now()); }
    public PageResponse<FileDtos.FileResponse> list(UUID owner, FileDtos.Filters filters, Pageable page) {
        Specification<FileMetadata> spec = (root, query, cb) -> cb.and(cb.equal(root.get("ownerId"), owner), cb.isNull(root.get("deletedAt")));
        if (filters.folderId() != null) spec = spec.and((r, q, c) -> c.equal(r.get("folderId"), filters.folderId())); if (filters.kind() != null) spec = spec.and((r, q, c) -> c.equal(r.get("kind"), filters.kind())); if (filters.search() != null && !filters.search().isBlank()) { if (filters.search().length() > 120) throw new BadRequestException("File search is too long"); String pattern = "%" + escapeLike(filters.search().trim().toLowerCase(Locale.ROOT)) + "%"; spec = spec.and((r, q, c) -> c.or(c.like(c.lower(r.get("name")), pattern, '\\'), c.like(c.lower(r.get("description")), pattern, '\\'))); } if (filters.from() != null) spec = spec.and((r, q, c) -> c.greaterThanOrEqualTo(r.get("createdAt"), filters.from().atStartOfDay())); if (filters.to() != null) spec = spec.and((r, q, c) -> c.lessThan(r.get("createdAt"), filters.to().plusDays(1).atStartOfDay()));
        var result = files.findAll(spec, page);
        Map<UUID, FileDtos.FolderResponse> folderCache = folderResponses(owner, result.getContent().stream().map(FileMetadata::getFolderId).filter(Objects::nonNull).distinct().toList());
        return PageResponse.from(result.map(x -> response(x, folderCache)));
    }
    public FileDtos.FileResponse get(UUID owner, UUID id) { return response(owner, file(owner, id), new HashMap<>()); }
    @Transactional public FileDtos.FileResponse upload(UUID owner, UUID folderId, MultipartFile multipart, String requestedName) {
        if (multipart == null || multipart.isEmpty()) throw new BadRequestException("File cannot be empty");
        if (folderId != null) folder(owner, folderId);
        Object lock = quotaLocks.computeIfAbsent(owner, ignored -> new Object());
        synchronized (lock) { return uploadLocked(owner, folderId, multipart, requestedName); }
    }
    private FileDtos.FileResponse uploadLocked(UUID owner, UUID folderId, MultipartFile multipart, String requestedName) {
        String originalName = cleanName(multipart.getOriginalFilename()); String name = requestedName == null || requestedName.isBlank() ? originalName : cleanName(requestedName); UUID key = UUID.randomUUID();
        long existing = files.sumSizeByOwnerIdAndDeletedAtIsNull(owner);
         try { FileStorage.StoredFile stored = storage.store(key, multipart.getInputStream()); if (existing > properties.getMaxUserBytes() - stored.size()) { storage.delete(key); throw new BadRequestException("File storage quota exceeded"); } String mimeType = serverMime(originalName, stored.contentType()); validateMime(mimeType); FileMetadata item = new FileMetadata(); item.setOwnerId(owner); item.setFolderId(folderId); item.setName(name); item.setDescription(name); item.setExtension(extension(name)); item.setMimeType(mimeType); item.setKind(kind(mimeType)); item.setStorageKey(key); item.setSizeBytes(stored.size()); item.setChecksum(stored.checksum()); try { return response(owner, files.save(item), new HashMap<>()); } catch (RuntimeException ex) { storage.delete(key); throw ex; } } catch (IOException ex) { throw new BadRequestException("Could not store file"); }
    }
    @Transactional public FileDtos.FileResponse patch(UUID owner, UUID id, FileDtos.PatchFileRequest request) { FileMetadata item = file(owner, id); String requestedName = request.name() != null ? request.name() : request.description(); if (requestedName != null) { String name = cleanName(requestedName); item.setName(name); item.setDescription(name); item.setExtension(extension(name)); } if (request.folderId() != null) { folder(owner, request.folderId()); item.setFolderId(request.folderId()); } return response(owner, item, new HashMap<>()); }
    public Download download(UUID owner, UUID id) { FileMetadata item = file(owner, id); Resource resource = storage.load(item.getStorageKey()); if (!resource.exists() || !resource.isReadable()) throw new NotFoundException("File content not found"); return new Download(item, resource); }
    @Transactional public void delete(UUID owner, UUID id) { FileMetadata item = file(owner, id); item.setDeletedAt(Instant.now()); try { storage.delete(item.getStorageKey()); } catch (IOException ex) { throw new BadRequestException("Could not delete file content"); } }
    public long count(UUID owner) { return files.countByOwnerIdAndDeletedAtIsNull(owner); }
    public StorageUsage storageUsage(UUID owner) { return new StorageUsage(files.sumSizeByOwnerIdAndDeletedAtIsNull(owner), properties.getMaxUserBytes()); }
    private FileFolder folder(UUID owner, UUID id) { return folders.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Folder not found")); }
    private FileMetadata file(UUID owner, UUID id) { return files.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("File not found")); }
    private String cleanName(String name) { if (name == null || name.isBlank()) throw new BadRequestException("File name is required"); String clean = java.nio.file.Paths.get(name).getFileName().toString(); if (clean.contains("..") || clean.chars().anyMatch(Character::isISOControl)) throw new BadRequestException("Invalid file name"); return clean; }
    private String extension(String name) { int dot = name.lastIndexOf('.'); return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase() : ""; }
    private String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
    private String serverMime(String name, String detected) { if (!"application/octet-stream".equals(detected)) return detected; try { String byName = java.nio.file.Files.probeContentType(java.nio.file.Paths.get(name)); return byName == null ? detected : byName; } catch (IOException ex) { return detected; } }
    private void validateMime(String mime) { if (Set.of("text/html", "application/xhtml+xml", "application/javascript", "text/javascript", "application/x-sh", "application/x-httpd-php").contains(mime.toLowerCase(Locale.ROOT))) throw new BadRequestException("This file type is not allowed"); }
    private FileKind kind(String mime) { if (mime.startsWith("image/")) return FileKind.IMAGE; if (mime.startsWith("video/")) return FileKind.VIDEO; if (mime.startsWith("audio/")) return FileKind.AUDIO; if (mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") || mime.contains("spreadsheet")) return FileKind.DOCUMENT; return FileKind.OTHER; }
    private Map<UUID, Long> folderCounts(UUID owner, Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, Long> counts = new HashMap<>();
        files.countByOwnerAndFolderIds(owner, ids).forEach(row -> counts.put(row.getFolderId(), row.getFileCount()));
        return counts;
    }
    private Map<UUID, FileDtos.FolderResponse> folderResponses(UUID owner, Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, Long> counts = folderCounts(owner, ids);
        Map<UUID, FileDtos.FolderResponse> result = new HashMap<>();
        folders.findActiveByOwnerAndIds(owner, ids).forEach(folder -> result.put(folder.getId(), folderResponse(folder, counts.getOrDefault(folder.getId(), 0L))));
        return result;
    }
    private FileDtos.FolderResponse folderResponse(FileFolder x, long fileCount) { return new FileDtos.FolderResponse(x.getId(), x.getName(), fileCount, x.getCreatedAt(), x.getUpdatedAt()); }
    private FileDtos.FileResponse response(UUID owner, FileMetadata x, Map<UUID, FileDtos.FolderResponse> folderCache) { return response(x, folderCache.isEmpty() ? folderResponses(owner, x.getFolderId() == null ? List.of() : List.of(x.getFolderId())) : folderCache); }
    private FileDtos.FileResponse response(FileMetadata x, Map<UUID, FileDtos.FolderResponse> folderCache) { FileDtos.FolderResponse folder = x.getFolderId() == null ? null : folderCache.get(x.getFolderId()); return new FileDtos.FileResponse(x.getId(), x.getName(), x.getDescription(), x.getExtension(), x.getMimeType(), x.getSizeBytes(), x.getKind(), folder, "/api/files/" + x.getId() + "/download", x.getUploadedAt(), x.getUpdatedAt()); }
    public record Download(FileMetadata metadata, Resource resource) {}
    public record StorageUsage(long usedBytes, long quotaBytes) {}
}
