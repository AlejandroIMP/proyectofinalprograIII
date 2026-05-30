package umg.edu.gt.floristeria.graph;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de {@link CommercialGraph} que NO requieren Oracle.
 * <p>
 * Cubren:
 * <ul>
 *   <li>{@code agregarNodo} deduplica por id.</li>
 *   <li>{@code agregarArista} acumula sin deduplicar (las múltiples relaciones
 *       entre los mismos nodos son válidas).</li>
 *   <li>Cuando {@code ORACLE_URL} no está configurada, cada
 *       {@code construirGrafoXxx(...)} retorna sin lanzar excepción y deja el
 *       grafo vacío.</li>
 * </ul>
 * La clase entera se desactiva si {@code ORACLE_URL} viene como variable de
 * entorno del SO, porque entonces el constructor de {@link CommercialGraph}
 * captura el valor y los tests de "grafo vacío" pasarían a intentar la conexión.
 * Ese caso queda cubierto por {@code CommercialGraphIT}.
 */
@DisabledIfEnvironmentVariable(named = "ORACLE_URL", matches = ".+",
        disabledReason = "Con ORACLE_URL en el entorno el constructor lo captura; el grafo vacío se valida vía IT")
class CommercialGraphUnitTest {

    private String urlPrevia;

    @BeforeEach
    void setUp() {
        urlPrevia = System.getProperty("ORACLE_URL");
        System.clearProperty("ORACLE_URL");
    }

    @AfterEach
    void tearDown() {
        if (urlPrevia == null) System.clearProperty("ORACLE_URL");
        else                   System.setProperty("ORACLE_URL", urlPrevia);
    }

    /* ---------- API en memoria ---------- */

    @Test
    @DisplayName("agregarNodo deduplica por id (no añade dos veces el mismo)")
    void agregarNodo_deduplica() {
        CommercialGraph g = new CommercialGraph();
        g.agregarNodo("CLI_1", "Eventos Sky", "CLIENTE");
        g.agregarNodo("CLI_1", "Eventos Sky (otra etiqueta)", "CLIENTE");
        g.agregarNodo("FAC_1", "Factura #1", "FACTURA");

        assertEquals(2, g.getNodos().size(), "el nodo CLI_1 solo debe existir 1 vez");
        // El primer label inserto se conserva.
        Nodo cli = g.getNodos().get(0);
        assertEquals("CLI_1", cli.getId());
        assertEquals("Eventos Sky", cli.getLabel());
    }

    @Test
    @DisplayName("agregarArista no deduplica: cada llamada añade una arista")
    void agregarArista_acumula() {
        CommercialGraph g = new CommercialGraph();
        g.agregarArista("A", "B", "REL1");
        g.agregarArista("A", "B", "REL1");
        g.agregarArista("A", "C", "REL2");

        assertEquals(3, g.getAristas().size());
        assertEquals("REL1", g.getAristas().get(0).getRelacion());
        assertEquals("REL2", g.getAristas().get(2).getRelacion());
    }

    /* ---------- Sin Oracle, los 6 métodos devuelven grafo vacío ---------- */

    @Test
    @DisplayName("Sin ORACLE_URL, los 6 construirGrafo* dejan el grafo vacío sin lanzar")
    void sinOracle_todosLosGrafosVaciosSinExcepcion() {
        CommercialGraph g = new CommercialGraph();
        // Pre-cargamos contenido para verificar que clear() lo borra aunque no haya BD.
        g.agregarNodo("X", "X", "CLIENTE");
        g.agregarArista("X", "Y", "REL");

        assertDoesNotThrow(() -> g.construirGrafoTrazabilidad(1));
        assertTrue(g.getNodos().isEmpty(), "construirGrafoTrazabilidad debió limpiar el grafo");
        assertTrue(g.getAristas().isEmpty());

        assertDoesNotThrow(() -> g.construirGrafoClienteProductos(101));
        assertTrue(g.getNodos().isEmpty());

        assertDoesNotThrow(() -> g.construirGrafoProveedorImpacto(5));
        assertTrue(g.getNodos().isEmpty());

        assertDoesNotThrow(() -> g.construirGrafoClienteProductosPorAnio(101, 2024, 2025));
        assertTrue(g.getNodos().isEmpty());

        assertDoesNotThrow(() -> g.construirGrafoTrazabilidadInversa(50));
        assertTrue(g.getNodos().isEmpty());

        assertDoesNotThrow(() -> g.construirGrafoTipoClientes(2));
        assertTrue(g.getNodos().isEmpty());
    }
}
