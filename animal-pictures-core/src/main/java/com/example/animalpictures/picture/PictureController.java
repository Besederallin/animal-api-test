package com.example.animalpictures.picture;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pictures")
public class PictureController {

    private final PictureService service;

    public PictureController(PictureService service) {
        this.service = service;
    }

    /** Fetches {@code count} random pictures of the animal from its public API and stores them. */
    @PostMapping("/{animal}")
    public ResponseEntity<List<PictureResponse>> fetchAndSave(
            @PathVariable String animal,
            @RequestParam(defaultValue = "1") int count) {
        List<PictureResponse> saved = service.fetchAndSave(AnimalType.fromValue(animal), count).stream()
                .map(PictureResponse::from)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** Returns the image of the most recently stored picture of the animal. */
    @GetMapping("/{animal}/latest")
    public ResponseEntity<byte[]> latest(@PathVariable String animal) {
        Picture picture = service.latest(AnimalType.fromValue(animal));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(picture.getContentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Picture-Id", String.valueOf(picture.getId()))
                .header("X-Picture-Created-At", picture.getCreatedAt().toString())
                .header("X-Picture-Source", picture.getSourceUrl())
                .body(picture.getImageData());
    }
}
