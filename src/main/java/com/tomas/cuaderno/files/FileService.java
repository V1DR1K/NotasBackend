package com.tomas.cuaderno.files;

import com.tomas.cuaderno.common.errors.*;
import com.tomas.cuaderno.common.pagination.PageResponse;
import java.io.IOException;
import java.time.*;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service public class FileService {
    private final FileFolderRepository folders; private final FileMetadataRepository files; private final FileStorage storage; private final FileProperties properties;
    public FileService(FileFolderRepository folders, FileMetadataRepository files, FileStorage storage, FileProperties properties) { this.folders = folders; this.files = files; this.storage = storage; this.properties = properties; }
    public PageResponse<FileDtos.FolderResponse> listFolders(UUID owner, Pageable page) { return PageResponse.from(folders.findByOwnerIdAndDeletedAtIsNull(owner, page).map(x -> folderResponse(owner, x))); }
    @Transactional public FileDtos.FolderResponse createFolder(UUID owner, FileDtos.CreateFolderRequest request) { FileFolder folder = new FileFolder(); folder.setOwnerId(owner); folder.setName(request.name().trim()); return folderResponse(owner, folders.save(folder)); }
    @Transactional public FileDtos.FolderResponse patchFolder(UUID owner, UUID id, FileDtos.PatchFolderRequest request) { FileFolder folder = folder(owner, id); folder.setName(request.name().trim()); return folderResponse(owner, folder); }
    @Transactional public void deleteFolder(UUID owner, UUID id) { FileFolder folder = folder(owner, id); if (files.countByOwnerIdAndFolderIdAndDeletedAtIsNull(owner, id) > 0) throw new BadRequestException("Folder contains files"); folder.setDeletedAt(Instant.now()); }
    public PageResponse<FileDtos.FileResponse> list(UUID owner, FileDtos.Filters filters, Pageable page) {
        Specification<FileMetadata> spec = (root, query, cb) -> cb.and(cb.equal(root.get("ownerId"), owner), cb.isNull(root.get("deletedAt")));
        if (filters.folderId() != null) spec = spec.and((r, q, c) -> c.equal(r.get("folderId"), filters.folderId())); if (filters.kind() != null) spec = spec.and((r, q, c) -> c.equal(r.get("kind"), filters.kind())); if (filters.name() != null && !filters.name().isBlank()) spec = spec.and((r, q, c) -> c.like(c.lower(r.get("name")), "%" + filters.name().toLowerCase() + "%")); if (filters.from() != null) spec = spec.and((r, q, c) -> c.greaterThanOrEqualTo(r.get("createdAt"), filters.from().atStartOfDay())); if (filters.to() != null) spec = spec.and((r, q, c) -> c.lessThan(r.get("createdAt"), filters.to().plusDays(1).atStartOfDay()));
        Map<UUID, FileDtos.FolderResponse> folderCache = new HashMap<>();
        return PageResponse.from(files.findAll(spec, page).map(x -> response(owner, x, folderCache)));
    }
    public FileDtos.FileResponse get(UUID owner, UUID id) { return response(owner, file(owner, id), new HashMap<>()); }
    @Transactional public FileDtos.FileResponse upload(UUID owner, UUID folderId, MultipartFile multipart) {
        if (multipart == null || multipart.isEmpty()) throw new BadRequestException("File cannot be empty"); if (folderId != null) folder(owner, folderId); String name = cleanName(multipart.getOriginalFilename()); UUID key = UUID.randomUUID();
        long existing = files.sumSizeByOwnerIdAndDeletedAtIsNull(owner);
        try { FileStorage.StoredFile stored = storage.store(key, multipart.getInputStream()); if (existing > properties.getMaxUserBytes() - stored.size()) { storage.delete(key); throw new BadRequestException("File storage quota exceeded"); } String mimeType = serverMime(name, stored.contentType()); FileMetadata item = new FileMetadata(); item.setOwnerId(owner); item.setFolderId(folderId); item.setName(name); item.setExtension(extension(name)); item.setMimeType(mimeType); item.setKind(kind(mimeType)); item.setStorageKey(key); item.setSizeBytes(stored.size()); item.setChecksum(stored.checksum()); try { return response(owner, files.save(item), new HashMap<>()); } catch (RuntimeException ex) { storage.delete(key); throw ex; } } catch (IOException ex) { throw new BadRequestException("Could not store file"); }
    }
    @Transactional public FileDtos.FileResponse patch(UUID owner, UUID id, FileDtos.PatchFileRequest request) { FileMetadata item = file(owner, id); if (request.name() != null) { String name = cleanName(request.name()); item.setName(name); item.setExtension(extension(name)); } if (request.folderId() != null) { folder(owner, request.folderId()); item.setFolderId(request.folderId()); } return response(owner, item, new HashMap<>()); }
    public Download download(UUID owner, UUID id) { FileMetadata item = file(owner, id); Resource resource = storage.load(item.getStorageKey()); if (!resource.exists() || !resource.isReadable()) throw new NotFoundException("File content not found"); return new Download(item, resource); }
    @Transactional public void delete(UUID owner, UUID id) { FileMetadata item = file(owner, id); item.setDeletedAt(Instant.now()); try { storage.delete(item.getStorageKey()); } catch (IOException ex) { throw new BadRequestException("Could not delete file content"); } }
    public long count(UUID owner) { return files.countByOwnerIdAndDeletedAtIsNull(owner); }
    private FileFolder folder(UUID owner, UUID id) { return folders.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Folder not found")); }
    private FileMetadata file(UUID owner, UUID id) { return files.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("File not found")); }
    private String cleanName(String name) { if (name == null || name.isBlank()) throw new BadRequestException("File name is required"); String clean = java.nio.file.Paths.get(name).getFileName().toString(); if (clean.contains("..") || clean.chars().anyMatch(Character::isISOControl)) throw new BadRequestException("Invalid file name"); return clean; }
    private String extension(String name) { int dot = name.lastIndexOf('.'); return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase() : ""; }
    private String serverMime(String name, String detected) { if (!"application/octet-stream".equals(detected)) return detected; try { String byName = java.nio.file.Files.probeContentType(java.nio.file.Paths.get(name)); return byName == null ? detected : byName; } catch (IOException ex) { return detected; } }
    private FileKind kind(String mime) { if (mime.startsWith("image/")) return FileKind.IMAGE; if (mime.startsWith("video/")) return FileKind.VIDEO; if (mime.startsWith("audio/")) return FileKind.AUDIO; if (mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") || mime.contains("spreadsheet")) return FileKind.DOCUMENT; return FileKind.OTHER; }
    private FileDtos.FolderResponse folderResponse(UUID owner, FileFolder x) { return new FileDtos.FolderResponse(x.getId(), x.getName(), files.countByOwnerIdAndFolderIdAndDeletedAtIsNull(owner, x.getId()), x.getCreatedAt(), x.getUpdatedAt()); }
    private FileDtos.FileResponse response(UUID owner, FileMetadata x, Map<UUID, FileDtos.FolderResponse> folderCache) { FileDtos.FolderResponse folder = null; if (x.getFolderId() != null) folder = folderCache.computeIfAbsent(x.getFolderId(), id -> folderResponse(owner, folders.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Folder not found")))); return new FileDtos.FileResponse(x.getId(), x.getName(), x.getExtension(), x.getMimeType(), x.getSizeBytes(), x.getKind(), folder, "/api/files/" + x.getId() + "/download", x.getUploadedAt(), x.getUpdatedAt()); }
    public record Download(FileMetadata metadata, Resource resource) {}
}
