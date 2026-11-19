package com.example.animalpictures.source;

import com.example.animalpictures.picture.AnimalType;

/** Downloads one random picture of an animal from its public picture API. */
public interface AnimalPictureClient {

    /**
     * @throws PictureSourceException if the picture API is unreachable or does not return an image
     */
    FetchedPicture fetch(AnimalType animal);
}
