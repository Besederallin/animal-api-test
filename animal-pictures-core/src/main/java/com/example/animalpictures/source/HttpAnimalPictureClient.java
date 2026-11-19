package com.example.animalpictures.source;

import com.example.animalpictures.config.AnimalPicturesProperties;
import com.example.animalpictures.picture.AnimalType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

/** Calls the public duck / dog / bear picture APIs configured in {@link AnimalPicturesProperties}. */
@Component
public class HttpAnimalPictureClient implements AnimalPictureClient {

    private final RestClient restClient;
    private final AnimalPicturesProperties properties;

    public HttpAnimalPictureClient(RestClient.Builder restClientBuilder, AnimalPicturesProperties properties) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", "animal-pictures/1.0")
                .build();
        this.properties = properties;
    }

    @Override
    public FetchedPicture fetch(AnimalType animal) {
        URI uri = properties.sourceFor(animal);
        ResponseEntity<byte[]> response;
        try {
            response = restClient.get()
                    .uri(uri)
                    .accept(MediaType.parseMediaType("image/*"))
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientException e) {
            throw new PictureSourceException("Could not fetch a " + animal.value() + " picture from " + uri, e);
        }

        MediaType contentType = response.getHeaders().getContentType();
        if (contentType == null || !"image".equalsIgnoreCase(contentType.getType())) {
            throw new PictureSourceException(
                    "Expected an image from " + uri + " but got content type " + contentType);
        }
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new PictureSourceException("Empty image returned by " + uri);
        }
        if (body.length > properties.getMaxImageBytes()) {
            throw new PictureSourceException("Image from " + uri + " is " + body.length
                    + " bytes, above the limit of " + properties.getMaxImageBytes());
        }

        // Drop parameters such as "; charset=..." and keep e.g. "image/jpeg".
        String normalizedType = contentType.getType() + "/" + contentType.getSubtype();
        return new FetchedPicture(body, normalizedType, uri.toString());
    }
}
