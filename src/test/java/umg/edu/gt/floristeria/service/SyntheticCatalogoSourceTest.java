package umg.edu.gt.floristeria.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;
import umg.edu.gt.floristeria.model.TipoCliente;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de {@link SyntheticCatalogoSource}.
 * <p>
 * La fuente sintética no toca red ni filesystem: cada test es 100 %
 * determinista en memoria. Sirve además como red de seguridad para los
 * IDs/datos semilla que asume el resto del sistema (catálogo desde 1000,
 * 3 proveedores con IDs 5/6/7, 4 tipos de cliente con IDs 1-4).
 */
class SyntheticCatalogoSourceTest {

    @Test
    @DisplayName("constructor con N negativo lanza IllegalArgumentException")
    void constructor_rechazaNegativos() {
        assertThrows(IllegalArgumentException.class, () -> new SyntheticCatalogoSource(-1));
    }

    @Test
    @DisplayName("cargar(N) devuelve N ítems con IDs consecutivos desde 1000")
    void cargar_devuelveNItemsConIdsDesde1000() throws Exception {
        SyntheticCatalogoSource src = new SyntheticCatalogoSource(5);
        CustomHashTable<Integer, ItemFloral> cat = src.cargar();

        assertEquals(5, cat.getSize());
        for (int i = 0; i < 5; i++) {
            int id = SyntheticCatalogoSource.ID_INICIAL + i;
            ItemFloral item = cat.get(id).value();
            assertNotNull(item, "esperaba ítem con id " + id);
            assertEquals(id, item.id());
            assertTrue(item.precio() > 0, "precio debe ser positivo");
            // El proveedor debe ser uno de los 3 base.
            assertTrue(item.idProveedor() >= 5 && item.idProveedor() <= 7,
                    "idProveedor fuera del rango base: " + item.idProveedor());
        }
    }

    @Test
    @DisplayName("cargar(0) devuelve una tabla hash vacía")
    void cargar_cero_devuelveTablaVacia() throws Exception {
        CustomHashTable<Integer, ItemFloral> cat = new SyntheticCatalogoSource(0).cargar();
        assertEquals(0, cat.getSize());
    }

    @Test
    @DisplayName("cargarMarcas() devuelve 3 fincas con IDs 5, 6 y 7")
    void cargarMarcas_devuelve3MarcasConIdsConocidos() throws Exception {
        CustomHashTable<Integer, ProveedorOrigen> marcas =
                new SyntheticCatalogoSource(10).cargarMarcas();

        assertEquals(3, marcas.getSize());
        assertNotNull(marcas.get(5).value());
        assertNotNull(marcas.get(6).value());
        assertNotNull(marcas.get(7).value());

        // Los países conocidos del install.sql semilla.
        assertEquals("Holanda",   marcas.get(5).value().pais());
        assertEquals("Ecuador",   marcas.get(6).value().pais());
        assertEquals("Guatemala", marcas.get(7).value().pais());
    }

    @Test
    @DisplayName("cargarTiposCliente() devuelve 4 tipos con IDs 1-4 y descuentos esperados")
    void cargarTiposCliente_devuelve4TiposConIdsConocidos() throws Exception {
        CustomHashTable<Integer, TipoCliente> tipos =
                new SyntheticCatalogoSource(0).cargarTiposCliente();

        assertEquals(4, tipos.getSize());

        TipoCliente t1 = tipos.get(1).value();
        TipoCliente t2 = tipos.get(2).value();
        TipoCliente t3 = tipos.get(3).value();
        TipoCliente t4 = tipos.get(4).value();

        assertNotNull(t1); assertNotNull(t2); assertNotNull(t3); assertNotNull(t4);

        // Los 4 descuentos coinciden con install.sql.
        assertEquals( 0.00, t1.descuento(), 1e-6);
        assertEquals(10.50, t2.descuento(), 1e-6);
        assertEquals( 5.00, t3.descuento(), 1e-6);
        assertEquals(15.00, t4.descuento(), 1e-6);
    }

    @Test
    @DisplayName("descripcion() incluye el número de ítems y de marcas")
    void descripcion_mencionaCantidades() {
        String d = new SyntheticCatalogoSource(50).descripcion();
        assertTrue(d.contains("50"),  "descripcion debe mencionar el número de ítems: " + d);
        assertTrue(d.toLowerCase().contains("sintétic") || d.toLowerCase().contains("sintetic"),
                "debería identificarse como sintética: " + d);
    }
}
