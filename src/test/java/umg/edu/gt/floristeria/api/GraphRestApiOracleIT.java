package umg.edu.gt.floristeria.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import umg.edu.gt.floristeria.service.SyntheticCatalogoSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración del servidor REST contra Oracle real.
 * <p>
 * Levanta {@link GraphRestApi} en puerto efímero usando catálogos
 * <em>sintéticos</em> (los hash POST se prueban en {@code GraphRestApiHashTest}),
 * y verifica los endpoints que requieren Oracle:
 * <ul>
 *   <li>{@code GET /api/grafo/trazabilidad?factura=1} con datos seed.</li>
 *   <li>{@code GET /api/grafo/tipo-clientes?tipo=2}.</li>
 *   <li>{@code GET /api/clientes} no vacío.</li>
 *   <li>{@code POST /api/grafo/factura} crea una factura real (se borra al final).</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "ORACLE_URL", matches = ".+")
class GraphRestApiOracleIT {

    private static HttpServer server;
    private static int port;
    private static HttpClient http;

    @BeforeAll
    void up() throws Exception {
        var src = new SyntheticCatalogoSource(10);
        server = GraphRestApi.iniciarServidor(0, src.cargar(), src.cargarMarcas(), src.cargarTiposCliente());
        port   = server.getAddress().getPort();
        http   = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterAll
    void down() {
        if (server != null) server.stop(0);
    }

    private HttpResponse<String> get(String ruta) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta)).build(),
                BodyHandlers.ofString());
    }

    @Test
    @DisplayName("GET /api/grafo/trazabilidad?factura=1 → JSON con nodes/edges no vacíos")
    void trazabilidad_facturaSeed() throws Exception {
        HttpResponse<String> r = get("/api/grafo/trazabilidad?factura=1");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("\"nodes\""));
        assertTrue(b.contains("\"edges\""));
        assertTrue(b.contains("\"group\": \"FACTURA\""),
                "esperaba al menos un nodo con grupo FACTURA en la trazabilidad seed");
    }

    @Test
    @DisplayName("GET /api/grafo/tipo-clientes?tipo=2 → nodos TIPO y CLIENTE")
    void tipoClientes_tipoSeed() throws Exception {
        HttpResponse<String> r = get("/api/grafo/tipo-clientes?tipo=2");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("\"group\": \"TIPO\""),    b);
        assertTrue(b.contains("\"group\": \"CLIENTE\""), b);
        assertTrue(b.contains("\"label\": \"CLASIFICA\""),
                "esperaba aristas CLASIFICA en el grafo tipo-clientes");
    }

    @Test
    @DisplayName("GET /api/clientes → array JSON con los clientes semilla")
    void listarClientes_noVacio() throws Exception {
        HttpResponse<String> r = get("/api/clientes");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.startsWith("["), b);
        assertTrue(b.contains("\"id\":101"));
    }

    @Test
    @DisplayName("POST /api/grafo/factura crea una factura real y la borra al final")
    void crearFactura_postOk() throws Exception {
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/grafo/factura"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(BodyPublishers.ofString("cliente=101&items=50:1"))
                        .build(),
                BodyHandlers.ofString());

        assertEquals(200, r.statusCode(), "body: " + r.body());
        String b = r.body();
        assertTrue(b.contains("\"ok\":true"), b);

        // Extraer el idFactura para limpiar después.
        Matcher m = Pattern.compile("\"idFactura\":(\\d+)").matcher(b);
        assertTrue(m.find(), "esperaba campo idFactura en la respuesta: " + b);
        int idFactura = Integer.parseInt(m.group(1));
        assertTrue(idFactura > 0);

        try (Connection conn = abrirConexion()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM DETALLE_FACTURA WHERE id_factura = ?")) {
                ps.setInt(1, idFactura); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM FACTURA WHERE id_factura = ?")) {
                ps.setInt(1, idFactura); ps.executeUpdate();
            }
        }
    }

    /* ----- Helpers JDBC reutilizables ----- */
    private static Connection abrirConexion() throws SQLException {
        return DriverManager.getConnection(envUrl(), env("ORACLE_USER"), env("ORACLE_PASS"));
    }
    private static String env(String n) {
        String v = System.getenv(n);
        return (v != null && !v.isBlank()) ? v : System.getProperty(n);
    }
    private static String envUrl() {
        String url = env("ORACLE_URL").trim();
        if (url.startsWith("jdbc:")) return url;
        if (url.startsWith("@"))     return "jdbc:oracle:thin:" + url;
        if (url.startsWith("//"))    return "jdbc:oracle:thin:@" + url;
        return "jdbc:oracle:thin:@//" + url;
    }
}
