package com.tomas.cuaderno.files;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {
    @TempDir java.nio.file.Path temp;
    @Test void storesServerCalculatedMetadataAndCanReadIt() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(temp.toString()); UUID key = UUID.randomUUID();
        FileStorage.StoredFile stored = storage.store(key, new ByteArrayInputStream("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(stored.size()).isEqualTo(5); assertThat(stored.checksum()).hasSize(64); assertThat(storage.load(key).getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(Files.list(temp).count()).isEqualTo(1);
    }
}
