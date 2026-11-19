package com.example.animalpictures;

import com.example.animalpictures.picture.AnimalType;
import com.example.animalpictures.picture.PictureRepository;
import com.example.animalpictures.source.AnimalPictureClient;
import com.example.animalpictures.source.FetchedPicture;
import com.example.animalpictures.source.PictureSourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the whole application (REST API, service, Flyway schema, JPA) against an in-memory H2 database.
 * Only the calls to the public picture APIs are replaced with a fake.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmbeddedPictureApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PictureRepository repository;

    @MockitoBean
    AnimalPictureClient client;

    private final AtomicInteger downloads = new AtomicInteger();

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        // Every fake download returns different bytes, so we can tell which picture is the latest.
        when(client.fetch(any())).thenAnswer(invocation -> {
            AnimalType animal = invocation.getArgument(0);
            byte[] image = (animal.value() + "-" + downloads.incrementAndGet()).getBytes();
            return new FetchedPicture(image, "image/jpeg", "https://example.test/" + animal.value());
        });
    }

    @Test
    void fetchesAndStoresPictures() throws Exception {
        mockMvc.perform(post("/api/pictures/dog").param("count", "3"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].animal").value("dog"))
                .andExpect(jsonPath("$[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$[0].sourceUrl").value("https://example.test/dog"));

        assertThat(repository.countByAnimalType(AnimalType.DOG)).isEqualTo(3);
    }

    @Test
    void countDefaultsToOne() throws Exception {
        mockMvc.perform(post("/api/pictures/duck"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void returnsLatestStoredPictureOfTheRequestedAnimal() throws Exception {
        mockMvc.perform(post("/api/pictures/bear").param("count", "2")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/pictures/duck")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/pictures/bear/latest"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes("bear-2".getBytes()))
                .andExpect(header().exists("X-Picture-Id"))
                .andExpect(header().exists("X-Picture-Created-At"));

        mockMvc.perform(get("/api/pictures/DUCK/latest"))
                .andExpect(status().isOk())
                .andExpect(content().bytes("duck-3".getBytes()));
    }

    @Test
    void latestReturns404WhenNothingStored() throws Exception {
        mockMvc.perform(get("/api/pictures/dog/latest"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("No dog pictures")));
    }

    @Test
    void rejectsUnknownAnimal() throws Exception {
        mockMvc.perform(post("/api/pictures/cat"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("duck, dog, bear")));
        mockMvc.perform(get("/api/pictures/cat/latest")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidCount() throws Exception {
        mockMvc.perform(post("/api/pictures/dog").param("count", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/pictures/dog").param("count", "11")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/pictures/dog").param("count", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void returns502AndStoresNothingWhenPictureApiFails() throws Exception {
        when(client.fetch(AnimalType.BEAR)).thenThrow(new PictureSourceException("placebear.com is down"));

        mockMvc.perform(post("/api/pictures/bear").param("count", "2"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("placebear.com is down")));

        assertThat(repository.count()).isZero();
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
