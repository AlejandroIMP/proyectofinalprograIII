package umg.edu.gt.floristeria.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;
import umg.edu.gt.floristeria.service.ReportService.ProductoMarcaRow;
import umg.edu.gt.floristeria.service.ReportService.ProductoRow;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de {@link ReportService}.
 * <p>
 * Se construyen tablas hash en memoria con datos controlados para verificar:
 * <ul>
 *   <li>{@code reporteProductos}: una fila por ítem, con {@code claveHash} igual a
 *       {@code Integer.hashCode(id)} y {@code slot} coincidente con el slot real.</li>
 *   <li>{@code reporteProductoMarca}: empareja ítem ↔ marca y reporta "(no encontrado)"
 *       cuando el proveedor referido no existe en la tabla de marcas, sin perder
 *       la métrica del intento.</li>
 * </ul>
 */
class ReportServiceTest {

    private CustomHashTable<Integer, ItemFloral> catalogo;
    private CustomHashTable<Integer, ProveedorOrigen> marcas;
    private ReportService rs;

    @BeforeEach
    void setUp() {
        catalogo = new CustomHashTable<>();
        marcas   = new CustomHashTable<>();
        rs = new ReportService();

        // 3 ítems con proveedor existente; 1 con proveedor huérfano (id=99)
        catalogo.put(1000, new ItemFloral(1000, "Rosa Roja",       12.50,  5));
        catalogo.put(1001, new ItemFloral(1001, "Tulipán Naranja", 15.00,  6));
        catalogo.put(1002, new ItemFloral(1002, "Lirio Blanco",    18.00,  5));
        catalogo.put(1003, new ItemFloral(1003, "Flor Huérfana",   20.00, 99));

        marcas.put(5, new ProveedorOrigen(5, "Finca Países Bajos", "Holanda"));
        marcas.put(6, new ProveedorOrigen(6, "Floricola Quiteña",  "Ecuador"));
    }

    /* ---------- 4.1 reporteProductos ---------- */

    @Test
    @DisplayName("reporteProductos genera una fila por ítem del catálogo")
    void reporteProductos_unaFilaPorItem() {
        List<ProductoRow> filas = rs.reporteProductos(catalogo);
        assertEquals(catalogo.getSize(), filas.size());
    }

    @Test
    @DisplayName("reporteProductos: claveHash coincide con Integer.hashCode y slot con get()")
    void reporteProductos_claveHashYSlotCoherentes() {
        List<ProductoRow> filas = rs.reporteProductos(catalogo);

        for (ProductoRow r : filas) {
            assertEquals(Integer.hashCode(r.idProducto()), r.claveHash(),
                    "claveHash debe ser Integer.hashCode(id)");
            // El slot reportado debe coincidir con el slot que la tabla devuelve.
            int slotEsperado = catalogo.get(r.idProducto()).tablePosition();
            assertEquals(slotEsperado, r.slot(),
                    "el slot del reporte debe ser el real de la tabla");
            assertTrue(r.probes() >= 1, "un hit reporta al menos 1 probe");
            assertTrue(r.durationNs() >= 0, "duración no puede ser negativa");
        }
    }

    @Test
    @DisplayName("reporteProductos copia fielmente nombre y precio del ítem")
    void reporteProductos_copiaCamposDelItem() {
        Map<Integer, ProductoRow> porId = rs.reporteProductos(catalogo).stream()
                .collect(Collectors.toMap(ProductoRow::idProducto, r -> r));

        assertEquals("Rosa Roja", porId.get(1000).nombreProducto());
        assertEquals(12.50,       porId.get(1000).precio(), 1e-6);
    }

    /* ---------- 4.2 reporteProductoMarca ---------- */

    @Test
    @DisplayName("reporteProductoMarca empareja ítem con su marca por idProveedor")
    void reporteProductoMarca_empareja() {
        Map<Integer, ProductoMarcaRow> porId = rs.reporteProductoMarca(catalogo, marcas).stream()
                .collect(Collectors.toMap(ProductoMarcaRow::idProducto, r -> r));

        ProductoMarcaRow rosa = porId.get(1000);
        assertEquals(5,                    rosa.idMarca());
        assertEquals("Finca Países Bajos", rosa.nombreMarca());
        assertEquals("Holanda",            rosa.paisMarca());

        ProductoMarcaRow tulipan = porId.get(1001);
        assertEquals(6,                    tulipan.idMarca());
        assertEquals("Ecuador",            tulipan.paisMarca());
    }

    @Test
    @DisplayName("reporteProductoMarca marca '(no encontrado)' cuando el proveedor no existe")
    void reporteProductoMarca_proveedorInexistente_marcaNoEncontrado() {
        Map<Integer, ProductoMarcaRow> porId = rs.reporteProductoMarca(catalogo, marcas).stream()
                .collect(Collectors.toMap(ProductoMarcaRow::idProducto, r -> r));

        ProductoMarcaRow huerf = porId.get(1003);
        assertEquals(99, huerf.idMarca(), "se conserva el idProveedor referido");
        assertEquals("(no encontrado)", huerf.nombreMarca());
        assertEquals("(no encontrado)", huerf.paisMarca());
        // Aunque no haya marca, la métrica del intento debe existir.
        assertTrue(huerf.marcaDurationNs() >= 0);
        assertTrue(huerf.slotMarca()       >= 0);
    }

    @Test
    @DisplayName("reporteProductoMarca produce una fila por ítem (incluidos los huérfanos)")
    void reporteProductoMarca_unaFilaPorItem() {
        List<ProductoMarcaRow> filas = rs.reporteProductoMarca(catalogo, marcas);
        assertEquals(catalogo.getSize(), filas.size());
    }
}
