package com.ttg.devknowledgeplatform.ecommerce.service.seed;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Byte-array-backed {@link MultipartFile}, for {@code ProductImageSeeder} to feed a
 * programmatically-generated placeholder image into {@code ProductService.uploadImage} the same
 * way a real HTTP multipart request would.
 *
 * <p>Spring's own {@code MockMultipartFile} (from {@code spring-test}) would do the same job, but
 * that dependency is test-scoped in this reactor — not available to main source. This class is the
 * minimal implementation of the interface's contract needed for {@code StorageService.uploadImage}
 * (content type + size validation, then a stream read) to work against in-memory bytes.
 *
 * @author ttg
 */
public class InMemoryMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    public InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.content = content;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(Path dest) throws IOException {
        Files.write(dest, content);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException {
        try (OutputStream out = Files.newOutputStream(dest.toPath())) {
            out.write(content);
        }
    }
}
