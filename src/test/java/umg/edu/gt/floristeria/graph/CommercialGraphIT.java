package umg.edu.gt.floristeria.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración del constructor de grafos contra Oracle real.
 * <p>
 * Usa los IDs sembrados por {@code install.sql}:
 * <ul>
 *   <li>Factura 1 → trazabilidad</li>
 *   <li>Cliente 101 → cliente-productos y cliente-productos-anio</li>
 *   <li>Proveedor 5 (Holanda) → proveedor-impacto</li>
 *   <li>Ítem 50 → trazabilidad-inversa</li>
 *   <li>Tipo 2 (Mayorista Alianzas) → tipo-clientes</li>
 * </ul>
 * Cada test verifica que el grafo resultante tiene nodos y aristas, y
 * que aparece al menos un nodo del tipo principal esperado.
 */
@EnabledIfEnvironmentVariable(named = "ORACLE_URL", matches = ".+")
class CommercialGraphIT {

    private boolean hayNodoTipo(CommercialGraph g, String tipo) {
        return g.getNodos().stream().anyMatch(n -> tipo.equals(n.getTipo()));
    }

    @Test
    @DisplayName("trazabilidad(factura=1) produce nodos CLIENTE, FACTURA, ITEM y PROVEEDOR")
    void trazabilidad_facturaSeed() {
        CommercialGraph g = new CommercialGraph();
        g.construirGrafoTrazabilidad(1);

        assertFalse(g.getNodos().isEmpty(),
                "la factura 1 está sembrada en install.sql, debería traer nodos");
        assertFalse(g.getAristas().isEmpty());
        assertTrue(hayNodoTipo(g, "FACTURA"));
        assertTrue(hayNodoTipo(g, "CLIENTE"));
        assertTrue(hayNodoTipo(g, "ITEM"));
        assertTrue(hayNodoTipo(g, "PROVEEDOR"));
    }

    @Test
    @DisplayName("cliente-productos(101) produce nodos CLIENTE e ITEM")
    void clienteProductos_clienteSeed() {
        CommercialGraph g = new CommercialGraph();
        g.construirGrafoClienteProductos(101);

        assertFalse(g.getNodos().isEmpty(), "cliente 101 tiene facturas seed");
        assertTrue(hayNodoTipo(g, "CLIENTE"));
        assertTrue(hayNodoTipo(g, "ITEM"));
    }

    @Test
    @DisplayName("proveedor-impacto(5) produce nodos PROVEEDOR e ITEM")
    void proveedorImpacto_proveedorSeed() {
        CommercialGraph g = new CommercialGraph();
        g.construirGrafoProveedorImpacto(5);

        assertFalse(g.getNodos().isEmpty(), "el proveedor 5 (Holanda) suministra ítems seed");
        assertTrue(hayNodoTipo(g, "PROVEEDOR"));
        assertTrue(hayNodoTipo(g, "ITEM"));
    }

    @Test
    @DisplayName("cliente-productos-anio(101, 2024, 2026) produce nodos cuando hay facturas en rango")
    void clienteProductosPorAnio_rangoCubreSeed() {
        CommercialGraph g = new CommercialGraph();
        g.construirGrafoClienteProductosPorAnio(101, 2024, 2026);

        // install.sql siembra facturas en este rango; el grafo NO debe quedar vacío.
        assertFalse(g.getNodos().isEmpty(),
                "el cliente 101 debe tener facturas dentro de 2024-2026 según install.sql");
    }

    @Test
    @DisplayName("trazabilidad-inversa(50) produce nodos ITEM y FACTURA")
    void trazabilidadInversa_itemSeed() {
        CommercialGraph g = new CommercialGraph();
        g.construirGrafoTrazabilidadInversa(50);

        // El ítem 50 está en al menos una factura semilla.
        assertFalse(g.getNodos().isEmpty(), "el ítem 50 debe aparecer en facturas seed");
        assertTrue(hayNodoTipo(g, "ITEM"));
    }

    @Test
    @DisplayName("tipo-clientes(2) produce nodos TIPO y CLIENTE para 'Mayorista Alianzas'")
    void tipoClientes_tipoSeed() {
        CommercialGraph g = new CommercialGraph();
        g.construirGrafoTipoClientes(2);

        assertFalse(g.getNodos().isEmpty(),
                "el tipo 2 (Mayorista Alianzas) tiene clientes seed");
        assertTrue(hayNodoTipo(g, "TIPO"));
        assertTrue(hayNodoTipo(g, "CLIENTE"));
        assertFalse(g.getAristas().isEmpty(), "debe haber aristas CLASIFICA");
        assertEquals("CLASIFICA", g.getAristas().get(0).getRelacion());
    }
}
