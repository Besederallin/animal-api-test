package com.example.animalpictures.web;

/** The request has an invalid value (unknown animal, count out of range). Reported as 400 Bad Request. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
