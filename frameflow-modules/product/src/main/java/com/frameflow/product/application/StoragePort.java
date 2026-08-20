package com.frameflow.product.application;

import com.frameflow.product.api.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Object-storage boundary (S3 FF-VSL-001). Local adapter is the default. */
public interface StoragePort {

    String put(String key, InputStream data, long size, String mediaType);

    Path localPath(String objectRef);

    static StoragePort local(Path root) {
        return new LocalStorageAdapter(root);
    }
}

/** Local filesystem adapter: writes under a repository-scoped media directory. */
class LocalStorageAdapter implements StoragePort {

    private final Path root;

    LocalStorageAdapter(Path root) {
        this.root = root;
    }

    @Override
    public String put(String key, InputStream data, long size, String mediaType) {
        if (key == null || key.isBlank()) {
            throw ApiException.badRequest("storage key required");
        }
        Path target = root.resolve(key).normalize();
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException ex) {
            throw ApiException.badRequest("storage write failed");
        }
    }

    @Override
    public Path localPath(String objectRef) {
        Path target = root.resolve(objectRef).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("storage path escapes root");
        }
        if (!Files.exists(target)) {
            throw ApiException.notFound("stored object not found");
        }
        return target;
    }
}
