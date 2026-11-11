package by.darkimpulsepoint.orderservice.integration;

import by.darkimpulsepoint.orderservice.dto.ItemDto;
import by.darkimpulsepoint.orderservice.repository.ItemRepository;
import by.darkimpulsepoint.orderservice.repository.OrderRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final String JWT_SECRET = "test-secret-key-for-integration-tests-hs256";
    protected static final String JWT_ISSUER = "auth-service";
    protected static final String USERS = "/api/v1/users";

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

    /** Plays user-service. */
    protected static final WireMockServer USER_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    private static final JwtEncoder JWT_ENCODER = new NimbusJwtEncoder(new ImmutableSecret<>(
            new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));

    static {
        POSTGRES.start();
        USER_SERVICE.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("security.jwt.secret", () -> JWT_SECRET);
        registry.add("security.jwt.issuer", () -> JWT_ISSUER);
        registry.add("user-service.url", USER_SERVICE::baseUrl);
        registry.add("user-service.read-timeout", () -> "1s");
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    protected OrderRepository orderRepository;
    @Autowired
    protected ItemRepository itemRepository;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected CircuitBreakerRegistry circuitBreakerRegistry;

    /** Client authenticated as ADMIN. */
    protected RestClient admin;
    protected RestClient anonymous;

    @BeforeEach
    void setUpClientsAndCleanState() {
        anonymous = clientBuilder().build();
        admin = clientWithToken(adminToken());

        jdbcTemplate.execute("TRUNCATE order_items, orders, items RESTART IDENTITY CASCADE");
        USER_SERVICE.resetAll();
        circuitBreakerRegistry.circuitBreaker("userService").reset();
    }

    protected RestClient clientWithToken(String token) {
        return clientBuilder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }

    protected static String adminToken() {
        return token(null, "ADMIN");
    }

    protected static String userToken(Long userId) {
        return token(userId, "USER");
    }

    private static String token(Long userId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(JWT_ISSUER)
                .subject(userId == null ? "admin" : "user-" + userId)
                .issuedAt(now.minusSeconds(1))
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim("role", role)
                .claim("type", "access");
        if (userId != null) {
            claims.claim("userId", userId);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return JWT_ENCODER.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    protected Long createItem(String name, String price) {
        ItemDto created = admin.post().uri("/api/v1/items")
                .body(new ItemDto(null, name, new BigDecimal(price), null, null))
                .retrieve().body(ItemDto.class);
        assertThat(created).isNotNull();
        return created.id();
    }

    protected static void stubUser(Long id, String email) {
        String body = userJson(id, email);
        USER_SERVICE.stubFor(get(urlPathEqualTo(USERS + "/by-email"))
                .withQueryParam("email", equalTo(email))
                .willReturn(json(body)));
        USER_SERVICE.stubFor(get(urlPathEqualTo(USERS + "/" + id)).willReturn(json(body)));
    }

    protected static String userJson(Long id, String email) {
        return """
                {"id": %d, "name": "Ivan", "surname": "Petrov", "birthDate": "1995-05-20",
                 "email": "%s", "active": true, "createdAt": "2026-01-01T00:00:00Z"}
                """.formatted(id, email);
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(body);
    }

    private RestClient.Builder clientBuilder() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                });
    }
}
