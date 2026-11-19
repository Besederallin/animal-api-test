package com.example.animalpictures.picture;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PictureRepository extends JpaRepository<Picture, Long> {

    /** Newest picture of an animal; the id breaks ties between pictures saved in the same batch. */
    Optional<Picture> findFirstByAnimalTypeOrderByCreatedAtDescIdDesc(AnimalType animalType);

    long countByAnimalType(AnimalType animalType);
}
