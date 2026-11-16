package com.ai.studenthelper.controller;

import com.ai.studenthelper.infra.storage.ImageStorage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** CR-027：历史题面图片只读端点；越界 / 非白名单 / 文件不存在一律 404。 */
@RestController
public class ImageController {

    private final ImageStorage storage;

    public ImageController(ImageStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/images/{sessionId}/{fileName:.+}")
    public ResponseEntity<byte[]> get(@PathVariable Long sessionId,
                                      @PathVariable String fileName) {
        Optional<Path> resolved = storage.resolve(sessionId, fileName);
        if (resolved.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] bytes = Files.readAllBytes(resolved.get());
            return ResponseEntity.ok()
                    .contentType(mediaType(fileName.toLowerCase(Locale.ROOT)))
                    .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private static MediaType mediaType(String lowerName) {
        if (lowerName.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lowerName.endsWith(".gif")) return MediaType.IMAGE_GIF;
        return MediaType.parseMediaType("image/webp");
    }
}
