package com.example.animalpictures.picture;

import com.example.animalpictures.web.InvalidRequestException;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** The animals we can fetch pictures of. The lower-case name is used in URLs, e.g. {@code /api/pictures/duck}. */
public enum AnimalType {
    DUCK,
    DOG,
    BEAR;

    /** Lower-case name used in URLs and JSON. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses a URL path value such as {@code "Dog"} (case-insensitive). */
    public static AnimalType fromValue(String value) {
        if (value != null) {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            for (AnimalType type : values()) {
                if (type.name().equals(normalized)) {
                    return type;
                }
            }
        }
        throw new InvalidRequestException(
                "Unsupported animal '" + value + "'. Supported animals: " + supportedValues());
    }

    public static String supportedValues() {
        return Arrays.stream(values()).map(AnimalType::value).collect(Collectors.joining(", "));
    }
}
