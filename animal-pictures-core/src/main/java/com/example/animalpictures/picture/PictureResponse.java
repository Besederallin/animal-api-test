package com.example.animalpictures.picture;

import java.time.Instant;

/** JSON view of a stored picture (metadata only, without the image bytes). */
public record PictureResponse(
        Long id,
        String animal,
        String contentType,
        int sizeBytes,
        String sourceUrl,
        Instant createdAt) {

    public static PictureResponse from(Picture picture) {
        return new PictureResponse(
                picture.getId(),
                picture.getAnimalType().value(),
                picture.getContentType(),
                picture.getSizeBytes(),
                picture.getSourceUrl(),
                picture.getCreatedAt());
    }
}
