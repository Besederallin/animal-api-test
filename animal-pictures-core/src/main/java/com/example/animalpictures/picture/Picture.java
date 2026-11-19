package com.example.animalpictures.picture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A stored picture: the image bytes plus some metadata about where and when it was fetched. */
@Entity
@Table(name = "picture")
public class Picture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "animal_type", nullable = false, length = 16)
    private AnimalType animalType;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;

    @Column(name = "image_data", nullable = false)
    private byte[] imageData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Picture() {
        // for JPA
    }

    public Picture(AnimalType animalType, String contentType, byte[] imageData, String sourceUrl, Instant createdAt) {
        this.animalType = animalType;
        this.contentType = contentType;
        this.imageData = imageData;
        this.sizeBytes = imageData.length;
        this.sourceUrl = sourceUrl;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public AnimalType getAnimalType() {
        return animalType;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public byte[] getImageData() {
        return imageData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
