package br.com.brasil_saas.shared.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StagingServiceSecurityTest {
    @TempDir Path directory;
    @Test void uploadWithClientPathStaysInStagingAndCanBeConfirmed() throws Exception {
        StagingService service = new StagingService(directory.resolve("staging").toString());
        Path file = service.gravar(new MockMultipartFile("file", "../../secret.txt", "text/plain", new byte[]{1,2}), "documento");
        assertEquals(service.getRaiz(), file.getParent());
        assertArrayEquals(new byte[]{1,2}, service.ler(file));
        service.confirmar(file);
        assertFalse(Files.exists(file));
    }
    @Test void clientPrefixCannotEscapeStaging() {
        StagingService service = new StagingService(directory.resolve("staging").toString());
        assertThrows(IllegalArgumentException.class, () -> service.gravar(
                new MockMultipartFile("file", new byte[]{1}), "../../outside"));
    }
    @Test void readAndDeleteCannotAccessFilesOutsideStaging() throws Exception {
        StagingService service = new StagingService(directory.resolve("staging").toString());
        Path outside = Files.writeString(directory.resolve("outside.txt"), "private");
        assertThrows(IllegalArgumentException.class, () -> service.ler(outside));
        assertThrows(IllegalArgumentException.class, () -> service.confirmar(outside));
        assertTrue(Files.exists(outside));
    }
    @Test void symlinkToExternalFileIsRejected() throws Exception {
        StagingService service = new StagingService(directory.resolve("staging").toString());
        Path outside = Files.writeString(directory.resolve("outside.txt"), "private");
        Path link = Files.createSymbolicLink(service.getRaiz().resolve("link"), outside);
        assertThrows(IllegalArgumentException.class, () -> service.ler(link));
    }
}
