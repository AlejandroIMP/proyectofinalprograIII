package umg.edu.gt.floristeria.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import umg.edu.gt.floristeria.service.SyntheticCatalogoSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests end-to-end de los handlers de tabla hash en {@link GraphRestApi}.
 * <p>
 * Levanta el servidor en un puerto efímero con datos sintéticos
 * (20 ítems + 3 marcas + 4 tipos de cliente). Verifica:
 * <ul>
 *   <li>{@code GET /api/hash/{catalogo,marcas,tipos-cliente}} → 200 con JSON
 *       que contiene los metadatos y el arreglo de entries.</li>
 *   <li>{@code ?buscar=ID} → respuesta con slot/probes/durationNs/tiempo,
 *       distinguiendo {@code found:true} de {@code found:false}.</li>
 *   <li>{@code POST} con form-urlencoded → {@code ok:true} con detalles de
 *       slot/colisión (los POST se saltan si {@code ORACLE_URL} está activo:
 *       eso se cubre en {@code GraphRestApiOracleIT}).</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphRestApiHashTest {

    private static HttpServer server;
    private static int port;
    private static HttpClient http;

    @BeforeAll
    void up() throws Exception {
        var src = new SyntheticCatalogoSource(20);
        server = GraphRestApi.iniciarServidor(0, src.cargar(), src.cargarMarcas(), src.cargarTiposCliente());
        port   = server.getAddress().getPort();
        http   = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @AfterAll
    void down() {
        if (server != null) server.stop(0);
    }

    private HttpResponse<String> get(String ruta) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta)).build(),
                BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String ruta, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(BodyPublishers.ofString(body)).build(),
                BodyHandlers.ofString());
    }

    /* ============================ GET catálogo ============================ */

    @Test
    @Order(1)
    @DisplayName("GET /api/hash/catalogo → 200 con tabla, capacity, size y entries")
    void getCatalogo_devuelveJsonCompleto() throws Exception {
        HttpResponse<String> r = get("/api/hash/catalogo");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("\"tabla\": \"Catalogo de Items Florales\""), b);
        assertTrue(b.contains("\"capacity\""));
        assertTrue(b.contains("\"size\": 20"));
        assertTrue(b.contains("\"entries\""));
        assertTrue(b.contains("\"nombre\""), "cada entrada debe traer nombre del ítem");
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/hash/marcas → 200 con tabla y 3 entradas")
    void getMarcas_devuelve3Entradas() throws Exception {
        HttpResponse<String> r = get("/api/hash/marcas");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("\"tabla\": \"Proveedores / Marcas\""), b);
        assertTrue(b.contains("\"size\": 3"));
        assertTrue(b.contains("Holanda"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/hash/tipos-cliente → 200 con 4 entradas y campos nombre/descuento")
    void getTipos_devuelve4Entradas() throws Exception {
        HttpResponse<String> r = get("/api/hash/tipos-cliente");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("\"tabla\": \"Tipos de Cliente\""), b);
        assertTrue(b.contains("\"size\": 4"));
        assertTrue(b.contains("\"descuento\""));
        assertTrue(b.contains("Minorista Regular"));
    }

    /* ============================ ?buscar= ============================ */

    @Test
    @Order(4)
    @DisplayName("?buscar=1000 en catálogo → found:true con métricas y nombre")
    void buscarCatalogo_hit() throws Exception {
        HttpResponse<String> r = get("/api/hash/catalogo?buscar=1000");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("\"buscar\":true"));
        assertTrue(b.contains("\"id\":1000"));
        assertTrue(b.contains("\"found\":true"), b);
        assertTrue(b.contains("\"slot\":"));
        assertTrue(b.contains("\"probes\":"));
        assertTrue(b.contains("\"durationNs\":"));
        assertTrue(b.contains("\"tiempo\":"));
        assertTrue(b.contains("\"nombre\":"));
    }

    @Test
    @Order(5)
    @DisplayName("?buscar=999999 en catálogo → found:false con slot/probes/tiempo")
    void buscarCatalogo_miss() throws Exception {
        HttpResponse<String> r = get("/api/hash/catalogo?buscar=999999");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("\"found\":false"), b);
        assertTrue(b.contains("\"slot\":"));
        assertTrue(b.contains("\"probes\":"));
    }

    @Test
    @Order(6)
    @DisplayName("?buscar=5 en marcas → found:true con nombreFinca y pais")
    void buscarMarcas_hit() throws Exception {
        HttpResponse<String> r = get("/api/hash/marcas?buscar=5");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("\"found\":true"), b);
        assertTrue(b.contains("\"nombreFinca\":"));
        assertTrue(b.contains("\"pais\":"));
    }

    @Test
    @Order(7)
    @DisplayName("?buscar=2 en tipos-cliente → found:true con nombre y descuento")
    void buscarTipos_hit() throws Exception {
        HttpResponse<String> r = get("/api/hash/tipos-cliente?buscar=2");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("\"found\":true"), b);
        assertTrue(b.contains("\"nombre\":"));
        assertTrue(b.contains("\"descuento\":"));
    }

    /* ============================ POST ============================ */

    /**
     * Los POST verifican el alta en memoria. Cuando {@code ORACLE_URL} está
     * configurado, el handler intenta persistir y la prueba dejaría de ser
     * autocontenida — esa ruta se cubre en {@code GraphRestApiOracleIT}.
     */
    @Test
    @Order(10)
    @DisplayName("POST /api/hash/catalogo → ok:true con slot y persistidoEnOracle:false")
    @DisabledIfEnvironmentVariable(named = "ORACLE_URL", matches = ".+",
            disabledReason = "El POST se prueba sin Oracle en el unit test; con Oracle lo cubre el IT")
    void postCatalogo_insertaEnMemoria() throws Exception {
        HttpResponse<String> r = postForm("/api/hash/catalogo",
                "id=9101&nombre=RosaTest&precio=12.34&idProveedor=5");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("\"ok\":true"), b);
        assertTrue(b.contains("\"id\":9101"));
        assertTrue(b.contains("\"slot\":"));
        assertTrue(b.contains("\"chainLength\":"));
        assertTrue(b.contains("\"persistidoEnOracle\":false"));
    }

    @Test
    @Order(11)
    @DisplayName("POST /api/hash/marcas → ok:true con datos del proveedor nuevo")
    @DisabledIfEnvironmentVariable(named = "ORACLE_URL", matches = ".+")
    void postMarcas_insertaEnMemoria() throws Exception {
        HttpResponse<String> r = postForm("/api/hash/marcas",
                "id=9202&nombreFinca=Finca+Test&pais=Chile");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("\"ok\":true"), b);
        assertTrue(b.contains("\"id\":9202"));
        assertTrue(b.contains("\"persistidoEnOracle\":false"));
    }

    @Test
    @Order(12)
    @DisplayName("POST /api/hash/tipos-cliente → ok:true con descuento y slot")
    @DisabledIfEnvironmentVariable(named = "ORACLE_URL", matches = ".+")
    void postTipos_insertaEnMemoria() throws Exception {
        HttpResponse<String> r = postForm("/api/hash/tipos-cliente",
                "id=9303&nombre=Corporativo&descuento=8.5");

        assertEquals(200, r.statusCode());
        String b = r.body();
        assertTrue(b.contains("\"ok\":true"), b);
        assertTrue(b.contains("\"id\":9303"));
        assertTrue(b.contains("\"persistidoEnOracle\":false"));
    }

    @Test
    @Order(13)
    @DisplayName("POST con cuerpo inválido (sin nombre) → 400 con ok:false")
    @DisabledIfEnvironmentVariable(named = "ORACLE_URL", matches = ".+")
    void postCatalogo_invalido_devuelve400() throws Exception {
        HttpResponse<String> r = postForm("/api/hash/catalogo",
                "id=9400&precio=10&idProveedor=5"); // falta nombre

        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("\"ok\":false"));
    }
}
