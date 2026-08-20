package com.tomas.cuaderno.files;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.core.io.Resource;

public interface FileStorage {
    StoredFile store(UUID key, InputStream content) throws IOException;
    Resource load(UUID key);
    record StoredFile(long size, String checksum, String contentType) {}
}
