package com.example.animalpictures.config;

import com.example.animalpictures.picture.AnimalType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Settings under the {@code animal-pictures.*} prefix. Every value has a default, so nothing has to be
 * configured; each can be overridden with an environment variable, e.g. {@code ANIMAL_PICTURES_MAX_COUNT=20}.
 */
@ConfigurationProperties(prefix = "animal-pictures")
public class AnimalPicturesProperties {

    /** Largest number of pictures one request may fetch. */
    private int maxCount = 10;

    /** Largest image (in bytes) we accept from a picture API. */
    private int maxImageBytes = 10 * 1024 * 1024;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(15);

    /** Picture API URL for each animal. */
    private Map<AnimalType, URI> sources = new EnumMap<>(Map.of(
            AnimalType.DUCK, URI.create("https://random-d.uk/api/v2/randomimg"),
            AnimalType.DOG, URI.create("https://place.dog/500/500"),
            AnimalType.BEAR, URI.create("https://placebear.com/500/500")));

    public URI sourceFor(AnimalType animal) {
        URI uri = sources.get(animal);
        if (uri == null) {
            throw new IllegalStateException("No picture source configured for " + animal.value());
        }
        return uri;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    public int getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(int maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Map<AnimalType, URI> getSources() {
        return sources;
    }

    public void setSources(Map<AnimalType, URI> sources) {
        this.sources = sources;
    }
}
