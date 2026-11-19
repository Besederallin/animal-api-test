package com.example.animalpictures;

import com.example.animalpictures.picture.AnimalType;
import com.example.animalpictures.picture.Picture;
import com.example.animalpictures.picture.PictureRepository;
import com.example.animalpictures.source.AnimalPictureClient;
import com.example.animalpictures.source.FetchedPicture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the application against a real PostgreSQL started in Docker by Testcontainers, so the Flyway
 * migration and the BYTEA image storage are tested on the same database used in production.
 * The calls to the public picture APIs are replaced with a fake.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PostgresPictureApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PictureRepository repository;

    @MockitoBean
    AnimalPictureClient client;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void storesBinaryImagesAndReturnsTheLatestOne() throws Exception {
        byte[] first = randomBytes(50_000);
        byte[] second = randomBytes(80_000);
        when(client.fetch(any()))
                .thenReturn(new FetchedPicture(first, "image/jpeg", "https://placebear.com/500/500"))
                .thenReturn(new FetchedPicture(second, "image/png", "https://placebear.com/500/500"));

        mockMvc.perform(post("/api/pictures/bear").param("count", "2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].sizeBytes").value(80_000));

        mockMvc.perform(get("/api/pictures/bear/latest"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(second));
    }

    @Test
    void picturesArePersistedInPostgres() {
        repository.save(new Picture(AnimalType.DOG, "image/jpeg", new byte[]{1, 2, 3}, "https://place.dog/500/500",
                java.time.Instant.now()));

        assertThat(repository.findFirstByAnimalTypeOrderByCreatedAtDescIdDesc(AnimalType.DOG))
                .get()
                .satisfies(picture -> assertThat(picture.getImageData()).containsExactly(1, 2, 3));
        assertThat(repository.findFirstByAnimalTypeOrderByCreatedAtDescIdDesc(AnimalType.DUCK)).isEmpty();
    }

    @Test
    void latestReturns404WhenNothingStored() throws Exception {
        mockMvc.perform(get("/api/pictures/duck/latest")).andExpect(status().isNotFound());
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(size).nextBytes(bytes);
        return bytes;
    }
}
