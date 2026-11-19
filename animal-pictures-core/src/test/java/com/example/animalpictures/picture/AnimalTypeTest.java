package com.example.animalpictures.picture;

import com.example.animalpictures.web.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnimalTypeTest {

    @ParameterizedTest
    @CsvSource({"duck, DUCK", "Dog, DOG", "BEAR, BEAR", "' bear ', BEAR"})
    void parsesAnimalNamesCaseInsensitively(String value, AnimalType expected) {
        assertThat(AnimalType.fromValue(value)).isEqualTo(expected);
    }

    @Test
    void rejectsUnknownAnimalAndListsSupportedOnes() {
        assertThatThrownBy(() -> AnimalType.fromValue("cat"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cat")
                .hasMessageContaining("duck, dog, bear");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> AnimalType.fromValue(null)).isInstanceOf(InvalidRequestException.class);
    }
}
