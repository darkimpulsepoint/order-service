package by.darkimpulsepoint.orderservice.config;

import by.darkimpulsepoint.orderservice.client.BearerTokenRelayInterceptor;
import by.darkimpulsepoint.orderservice.client.UserServiceProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class UserServiceClientConfig {

    @Bean
    public RestClient userServiceRestClient(RestClient.Builder builder, UserServiceProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return builder
                .baseUrl(properties.url())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .requestInterceptor(new BearerTokenRelayInterceptor())
                .build();
    }
}
