package com.ShopMeta.product_service.service;

import com.ShopMeta.product_service.exception.InvalidRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * Stores uploaded files on the local filesystem and returns a public URL.
 *
 * <p>A future phase may swap this for S3-compatible object storage without changing callers.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${metashop.storage.location:uploads}")
    private String storageLocation;

    @Value("${metashop.storage.public-base-url:/uploads}")
    private String publicBaseUrl;

    /**
     * Validates and stores an uploaded image, returning its public URL.
     */
    public String storeImage(MultipartFile file, String subDirectory) {

        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Image file is required");
        }

        String contentType = file.getContentType();

        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new InvalidRequestException("Only image uploads are allowed");
        }

        String filename = UUID.randomUUID() + resolveExtension(file.getOriginalFilename());

        try {
            Path directory = Paths.get(storageLocation, subDirectory).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            file.transferTo(directory.resolve(filename));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store uploaded image", exception);
        }

        return publicBaseUrl + "/" + subDirectory + "/" + filename;
    }

    /**
     * Best-effort deletion of a previously stored file given its public URL.
     */
    public void deleteByUrl(String url) {

        if (url == null || !url.startsWith(publicBaseUrl + "/")) {
            return;
        }

        String relative = url.substring(publicBaseUrl.length() + 1);

        try {
            Path target = Paths.get(storageLocation).toAbsolutePath().normalize()
                    .resolve(relative).normalize();
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            log.warn("Failed to delete stored file for url {}: {}", url, exception.getMessage());
        }
    }

    private String resolveExtension(String originalFilename) {

        if (originalFilename == null) {
            return "";
        }

        int dot = originalFilename.lastIndexOf('.');

        if (dot < 0) {
            return "";
        }

        String extension = originalFilename.substring(dot).toLowerCase(Locale.ROOT);

        return extension.matches("\\.[a-z0-9]{1,5}") ? extension : "";
    }
}
