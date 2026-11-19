package com.example.animalpictures.picture;

import com.example.animalpictures.config.AnimalPicturesProperties;
import com.example.animalpictures.source.AnimalPictureClient;
import com.example.animalpictures.source.FetchedPicture;
import com.example.animalpictures.web.InvalidRequestException;
import com.example.animalpictures.web.PictureNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Service
public class PictureService {

    private static final Logger log = LoggerFactory.getLogger(PictureService.class);

    private final AnimalPictureClient client;
    private final PictureRepository repository;
    private final AnimalPicturesProperties properties;
    private final Clock clock;

    public PictureService(AnimalPictureClient client, PictureRepository repository,
                          AnimalPicturesProperties properties, Clock clock) {
        this.client = client;
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Downloads {@code count} pictures of the animal and stores them.
     * All pictures are downloaded first and then saved in one transaction, so either all of them
     * are stored or (if any download fails) none are.
     */
    public List<Picture> fetchAndSave(AnimalType animal, int count) {
        if (count < 1 || count > properties.getMaxCount()) {
            throw new InvalidRequestException(
                    "count must be between 1 and " + properties.getMaxCount() + ", but was " + count);
        }

        List<Picture> pictures = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            FetchedPicture fetched = client.fetch(animal);
            pictures.add(new Picture(animal, fetched.contentType(), fetched.data(), fetched.sourceUrl(), clock.instant()));
        }

        List<Picture> saved = repository.saveAll(pictures);
        log.info("Stored {} {} picture(s)", saved.size(), animal.value());
        return saved;
    }

    /** The most recently stored picture of the animal. */
    @Transactional(readOnly = true)
    public Picture latest(AnimalType animal) {
        return repository.findFirstByAnimalTypeOrderByCreatedAtDescIdDesc(animal)
                .orElseThrow(() -> new PictureNotFoundException(
                        "No " + animal.value() + " pictures stored yet. Fetch some with POST /api/pictures/" + animal.value()));
    }
}
