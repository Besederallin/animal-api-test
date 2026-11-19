package com.example.animalpictures.source;

/** A downloaded image, before it is stored. */
public record FetchedPicture(byte[] data, String contentType, String sourceUrl) {
}
