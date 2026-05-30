package umg.edu.gt.floristeria.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import umg.edu.gt.floristeria.service.SyntheticCatalogoSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica el manejo de CORS en {@link GraphRestApi}: ante una petición
 * preflight {@code OPTIONS}, el handler debe responder {@code 204} con
 * las cabeceras {@code Access-Control-Allow-*} ya pobladas. Esto es lo que
 * permite que el frontend (servido en {@code http://localhost:8085}) pueda
 * ser embebido o consumido desde un origen distinto.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphRestApiCorsTest {

    private static HttpServer server;
    private static int port;
    private static HttpClient http;

    @BeforeAll
    void up() throws Exception {
        var src = new SyntheticCatalogoSource(5);
        server = GraphRestApi.iniciarServidor(0, src.cargar(), src.cargarMarcas(), src.cargarTiposCliente());
        port   = server.getAddress().getPort();
        http   = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @AfterAll
    void down() {
        if (server != null) server.stop(0);
    }

    @Test
    @DisplayName("OPTIONS /api/hash/catalogo → 204 con cabeceras CORS pobladas")
    void optionsCatalogo_devuelve204ConCorsHeaders() throws Exception {
        HttpResponse<Void> r = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/hash/catalogo"))
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .build(),
                BodyHandlers.discarding());

        assertEquals(204, r.statusCode());

        var headers = r.headers();
        assertTrue(headers.firstValue("Access-Control-Allow-Origin").isPresent(),
                "falta cabecera Access-Control-Allow-Origin");
        assertEquals("*", headers.firstValue("Access-Control-Allow-Origin").get());

        assertTrue(headers.firstValue("Access-Control-Allow-Methods").isPresent());
        assertTrue(headers.firstValue("Access-Control-Allow-Methods").get().contains("POST"));

        assertTrue(headers.firstValue("Access-Control-Allow-Headers").isPresent());
    }
}
