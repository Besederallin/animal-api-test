package com.example.animalpictures.picture;

import com.example.animalpictures.config.AnimalPicturesProperties;
import com.example.animalpictures.source.AnimalPictureClient;
import com.example.animalpictures.source.FetchedPicture;
import com.example.animalpictures.source.PictureSourceException;
import com.example.animalpictures.web.InvalidRequestException;
import com.example.animalpictures.web.PictureNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PictureServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final AnimalPictureClient client = mock(AnimalPictureClient.class);
    private final PictureRepository repository = mock(PictureRepository.class);
    private final AnimalPicturesProperties properties = new AnimalPicturesProperties();
    private PictureService service;

    @BeforeEach
    void setUp() {
        properties.setMaxCount(5);
        service = new PictureService(client, repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void fetchesAndSavesRequestedNumberOfPictures() {
        when(client.fetch(AnimalType.DOG))
                .thenReturn(new FetchedPicture(new byte[]{1, 2, 3}, "image/jpeg", "https://place.dog/500/500"));

        List<Picture> saved = service.fetchAndSave(AnimalType.DOG, 3);

        assertThat(saved).hasSize(3).allSatisfy(picture -> {
            assertThat(picture.getAnimalType()).isEqualTo(AnimalType.DOG);
            assertThat(picture.getContentType()).isEqualTo("image/jpeg");
            assertThat(picture.getSizeBytes()).isEqualTo(3);
            assertThat(picture.getSourceUrl()).isEqualTo("https://place.dog/500/500");
            assertThat(picture.getCreatedAt()).isEqualTo(NOW);
        });
        verify(client, times(3)).fetch(AnimalType.DOG);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 6})
    void rejectsCountOutsideAllowedRange(int count) {
        assertThatThrownBy(() -> service.fetchAndSave(AnimalType.DUCK, count))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("between 1 and 5");
        verifyNoInteractions(client);
    }

    @Test
    void savesNothingWhenAnyDownloadFails() {
        when(client.fetch(AnimalType.BEAR))
                .thenReturn(new FetchedPicture(new byte[]{1}, "image/jpeg", "https://placebear.com/500/500"))
                .thenThrow(new PictureSourceException("boom"));

        assertThatThrownBy(() -> service.fetchAndSave(AnimalType.BEAR, 2)).isInstanceOf(PictureSourceException.class);
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void returnsLatestPicture() {
        Picture picture = new Picture(AnimalType.DUCK, "image/jpeg", new byte[]{9}, "https://random-d.uk", NOW);
        when(repository.findFirstByAnimalTypeOrderByCreatedAtDescIdDesc(AnimalType.DUCK)).thenReturn(Optional.of(picture));

        assertThat(service.latest(AnimalType.DUCK)).isSameAs(picture);
    }

    @Test
    void latestFailsWhenNothingStored() {
        when(repository.findFirstByAnimalTypeOrderByCreatedAtDescIdDesc(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.latest(AnimalType.BEAR))
                .isInstanceOf(PictureNotFoundException.class)
                .hasMessageContaining("bear");
    }
}
