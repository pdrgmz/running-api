package com.running.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenMeteoElevationProviderTest {

    @Test
    void retriesAfterRateLimitAndReturnsElevations() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

        try {
            server.createContext("/elevation", exchange -> {
                int requestNumber = requests.incrementAndGet();
                byte[] body;
                if (requestNumber == 1) {
                    exchange.getResponseHeaders().add("Retry-After", "0");
                    body = "{\"error\":true,\"reason\":\"rate limit\"}"
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(429, body.length);
                } else {
                    body = "{\"elevation\":[100.0,200.0]}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                }
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();

            String apiUrl = "http://localhost:" + server.getAddress().getPort() + "/elevation";
            OpenMeteoElevationProvider provider = new OpenMeteoElevationProvider(
                    new ObjectMapper(),
                    true,
                    apiUrl,
                    "",
                    10,
                    5,
                    0,
                    0,
                    1,
                    0,
                    1);

            List<Double> elevations = provider.lookup(List.of(
                    new ElevationProvider.Coordinate(40.0, -3.0),
                    new ElevationProvider.Coordinate(41.0, -4.0)));

            assertThat(elevations).containsExactly(100.0, 200.0);
            assertThat(requests).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsApiKeyWhenConfigured() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

        try {
            server.createContext("/elevation", exchange -> {
                query.set(exchange.getRequestURI().getQuery());
                byte[] body = "{\"elevation\":[42.0]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();

            String apiUrl = "http://localhost:" + server.getAddress().getPort() + "/elevation";
            OpenMeteoElevationProvider provider = new OpenMeteoElevationProvider(
                    new ObjectMapper(), true, apiUrl, "test-key", 10, 5, 0, 0, 1, 0, 1);

            List<Double> elevations = provider.lookup(List.of(
                    new ElevationProvider.Coordinate(40.0, -3.0)));

            assertThat(elevations).containsExactly(42.0);
            assertThat(query.get()).contains("apikey=test-key");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesCustomerEndpointWhenApiKeyIsConfigured() {
        assertThat(OpenMeteoElevationProvider.resolveApiUrl(
                "https://api.open-meteo.com/v1/elevation", "test-key"))
                .isEqualTo("https://customer-api.open-meteo.com/v1/elevation");

        assertThat(OpenMeteoElevationProvider.resolveApiUrl(
                "https://api.open-meteo.com/v1/elevation", ""))
                .isEqualTo("https://api.open-meteo.com/v1/elevation");

        assertThat(OpenMeteoElevationProvider.resolveApiUrl("", "test-key"))
                .isEqualTo("https://customer-api.open-meteo.com/v1/elevation");

        assertThat(OpenMeteoElevationProvider.resolveApiUrl(
                "https://mi-proxy.local/elevation", "test-key"))
                .isEqualTo("https://mi-proxy.local/elevation");
    }
}
