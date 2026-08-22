package com.tomas.cuaderno.files;

import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api") public class FileController {
    private final FileService service; public FileController(FileService service) { this.service = service; }
    @GetMapping("/file-folders") public PageResponse<FileDtos.FolderResponse> folders(@PageableDefault(size = 50, sort = "name") Pageable page) { return service.listFolders(CurrentUser.id(), page); }
    @PostMapping("/file-folders") @ResponseStatus(HttpStatus.CREATED) public FileDtos.FolderResponse createFolder(@Valid @RequestBody FileDtos.CreateFolderRequest request) { return service.createFolder(CurrentUser.id(), request); }
    @PatchMapping("/file-folders/{id}") public FileDtos.FolderResponse patchFolder(@PathVariable UUID id, @Valid @RequestBody FileDtos.PatchFolderRequest request) { return service.patchFolder(CurrentUser.id(), id, request); }
    @DeleteMapping("/file-folders/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteFolder(@PathVariable UUID id) { service.deleteFolder(CurrentUser.id(), id); }
    @GetMapping("/files") public PageResponse<FileDtos.FileResponse> list(@RequestParam(required = false) UUID folderId, @RequestParam(required = false) String kind, @RequestParam(required = false) String name, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable page) { return service.list(CurrentUser.id(), new FileDtos.Filters(folderId, kind == null ? null : FileKind.fromValue(kind), name, from, to), page); }
    @GetMapping("/files/{id}") public FileDtos.FileResponse get(@PathVariable UUID id) { return service.get(CurrentUser.id(), id); }
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) public FileDtos.FileResponse upload(@RequestPart("file") MultipartFile file, @RequestParam(value = "folderId", required = false) UUID folderId) { return service.upload(CurrentUser.id(), folderId, file); }
    @PatchMapping("/files/{id}") public FileDtos.FileResponse patch(@PathVariable UUID id, @Valid @RequestBody FileDtos.PatchFileRequest request) { return service.patch(CurrentUser.id(), id, request); }
    @GetMapping("/files/{id}/download") public ResponseEntity<Resource> download(@PathVariable UUID id) { FileService.Download result = service.download(CurrentUser.id(), id); return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.metadata().getMimeType())).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.metadata().getName()).build().toString()).header("X-Content-Type-Options", "nosniff").contentLength(result.metadata().getSizeBytes()).body(result.resource()); }
    @DeleteMapping("/files/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(CurrentUser.id(), id); }
}
