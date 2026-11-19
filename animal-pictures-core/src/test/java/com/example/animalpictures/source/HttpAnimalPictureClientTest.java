package com.example.animalpictures.source;

import com.example.animalpictures.config.AnimalPicturesProperties;
import com.example.animalpictures.picture.AnimalType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/** Tests the HTTP client against a mocked server, so no real network calls are made. */
class HttpAnimalPictureClientTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    private final AnimalPicturesProperties properties = new AnimalPicturesProperties();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private MockRestServiceServer server;
    private HttpAnimalPictureClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpAnimalPictureClient(builder, properties, meterRegistry);
    }

    @Test
    void callsTheConfiguredUrlForEachAnimal() {
        server.expect(requestTo("https://random-d.uk/api/v2/randomimg")).andExpect(method(GET))
                .andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));
        server.expect(requestTo("https://place.dog/500/500"))
                .andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));
        server.expect(requestTo("https://placebear.com/500/500"))
                .andRespond(withSuccess(JPEG, MediaType.IMAGE_PNG));

        assertThat(client.fetch(AnimalType.DUCK).sourceUrl()).isEqualTo("https://random-d.uk/api/v2/randomimg");
        assertThat(client.fetch(AnimalType.DOG).sourceUrl()).isEqualTo("https://place.dog/500/500");
        FetchedPicture bear = client.fetch(AnimalType.BEAR);

        assertThat(bear.data()).isEqualTo(JPEG);
        assertThat(bear.contentType()).isEqualTo("image/png");
        server.verify();
        assertThat(meterRegistry.get("animal.pictures.source.requests").tags("animal", "bear", "outcome", "success")
                .timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("animal.pictures.source.image.size").tag("animal", "bear")
                .summary().totalAmount()).isEqualTo(JPEG.length);
    }

    @Test
    void stripsContentTypeParameters() {
        server.expect(requestTo("https://place.dog/500/500"))
                .andRespond(withSuccess(JPEG, MediaType.parseMediaType("image/jpeg;charset=UTF-8")));

        assertThat(client.fetch(AnimalType.DOG).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void failsOnServerError() {
        server.expect(requestTo("https://place.dog/500/500")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.fetch(AnimalType.DOG))
                .isInstanceOf(PictureSourceException.class)
                .hasMessageContaining("place.dog");
        assertThat(meterRegistry.get("animal.pictures.source.requests").tags("animal", "dog", "outcome", "error")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void failsWhenResponseIsNotAnImage() {
        server.expect(requestTo("https://placebear.com/500/500"))
                .andRespond(withSuccess("<html>oops</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.fetch(AnimalType.BEAR))
                .isInstanceOf(PictureSourceException.class)
                .hasMessageContaining("Expected an image");
    }

    @Test
    void failsOnEmptyBody() {
        server.expect(requestTo("https://random-d.uk/api/v2/randomimg"))
                .andRespond(withSuccess(new byte[0], MediaType.IMAGE_JPEG));

        assertThatThrownBy(() -> client.fetch(AnimalType.DUCK))
                .isInstanceOf(PictureSourceException.class)
                .hasMessageContaining("Empty image");
    }

    @Test
    void failsWhenImageIsTooLarge() {
        properties.setMaxImageBytes(2);
        server.expect(requestTo("https://random-d.uk/api/v2/randomimg"))
                .andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));

        assertThatThrownBy(() -> client.fetch(AnimalType.DUCK))
                .isInstanceOf(PictureSourceException.class)
                .hasMessageContaining("above the limit");
    }
}
