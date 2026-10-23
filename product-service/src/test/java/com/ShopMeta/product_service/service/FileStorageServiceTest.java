package com.ShopMeta.product_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.ShopMeta.product_service.exception.InvalidRequestException;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService();
        ReflectionTestUtils.setField(fileStorageService, "storageLocation", tempDir.toString());
        ReflectionTestUtils.setField(fileStorageService, "publicBaseUrl", "/uploads");
    }

    @Test
    void storeImage_writesFileAndReturnsPublicUrl() {

        MockMultipartFile file = new MockMultipartFile("file", "photo.PNG", "image/png", new byte[] { 1, 2, 3 });

        String url = fileStorageService.storeImage(file, "products");

        assertThat(url).startsWith("/uploads/products/").endsWith(".png");
        assertThat(Files.exists(tempDir.resolve("products").resolve(url.substring("/uploads/products/".length()))))
                .isTrue();
    }

    @Test
    void storeImage_emptyFile_throwsInvalidRequestException() {

        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.storeImage(file, "products"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void storeImage_nonImageContentType_throwsInvalidRequestException() {

        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] { 1 });

        assertThatThrownBy(() -> fileStorageService.storeImage(file, "products"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void deleteByUrl_removesStoredFile() {

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] { 1, 2, 3 });

        String url = fileStorageService.storeImage(file, "products");
        Path stored = tempDir.resolve("products").resolve(url.substring("/uploads/products/".length()));

        assertThat(Files.exists(stored)).isTrue();

        fileStorageService.deleteByUrl(url);

        assertThat(Files.exists(stored)).isFalse();
    }
}
