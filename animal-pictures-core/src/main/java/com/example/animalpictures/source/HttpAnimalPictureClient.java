package com.example.animalpictures.source;

import com.example.animalpictures.config.AnimalPicturesProperties;
import com.example.animalpictures.picture.AnimalType;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

/**
 * Calls the public duck / dog / bear picture APIs configured in {@link AnimalPicturesProperties}.
 * <p>
 * Monitoring: every call is timed in the {@code animal.pictures.source.requests} metric (tags {@code animal},
 * {@code outcome}), and the image sizes go to {@code animal.pictures.source.image.size}.
 * Logging: each call is logged at DEBUG; failures are logged by {@code ApiExceptionHandler}.
 */
@Component
public class HttpAnimalPictureClient implements AnimalPictureClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAnimalPictureClient.class);

    private final RestClient restClient;
    private final AnimalPicturesProperties properties;
    private final MeterRegistry meterRegistry;

    public HttpAnimalPictureClient(RestClient.Builder restClientBuilder, AnimalPicturesProperties properties,
                                   MeterRegistry meterRegistry) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", "animal-pictures/1.0")
                .build();
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public FetchedPicture fetch(AnimalType animal) {
        URI uri = properties.sourceFor(animal);
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "error";
        try {
            FetchedPicture picture = download(animal, uri);
            outcome = "success";
            DistributionSummary.builder("animal.pictures.source.image.size")
                    .description("Size of images downloaded from the picture APIs")
                    .baseUnit("bytes")
                    .tag("animal", animal.value())
                    .register(meterRegistry)
                    .record(picture.data().length);
            return picture;
        } finally {
            long nanos = sample.stop(Timer.builder("animal.pictures.source.requests")
                    .description("Calls to the public picture APIs")
                    .tag("animal", animal.value())
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
            log.debug("GET {} -> {} in {} ms", uri, outcome, nanos / 1_000_000);
        }
    }

    private FetchedPicture download(AnimalType animal, URI uri) {
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
