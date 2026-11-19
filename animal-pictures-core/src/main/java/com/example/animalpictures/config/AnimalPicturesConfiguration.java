package com.example.animalpictures.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnimalPicturesProperties.class)
public class AnimalPicturesConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Timeouts and redirect handling for the HTTP client that calls the picture APIs. */
    @Bean
    RestClientCustomizer pictureSourceHttpClient(AnimalPicturesProperties properties) {
        return builder -> {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(properties.getConnectTimeout())
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(properties.getReadTimeout());
            builder.requestFactory(requestFactory);
        };
    }
}
