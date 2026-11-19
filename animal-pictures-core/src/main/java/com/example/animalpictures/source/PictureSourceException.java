package com.example.animalpictures.source;

/** A public picture API failed. Reported to callers as 502 Bad Gateway. */
public class PictureSourceException extends RuntimeException {

    public PictureSourceException(String message) {
        super(message);
    }

    public PictureSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
