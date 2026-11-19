package com.example.animalpictures.web;

/** No picture stored for the requested animal. Reported as 404 Not Found. */
public class PictureNotFoundException extends RuntimeException {

    public PictureNotFoundException(String message) {
        super(message);
    }
}
