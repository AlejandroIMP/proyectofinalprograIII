package umg.edu.gt.floristeria.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;
import umg.edu.gt.floristeria.model.TipoCliente;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración del pipeline JDBC contra Oracle real.
 * <p>
 * Estos tests se ejecutan únicamente cuando la variable de entorno
 * {@code ORACLE_URL} está definida. En CI o desarrollo local sin VM
 * el archivo se compila pero los tests se saltan silenciosamente
 * (visibles como {@code "Tests skipped: N"} en la salida de Maven).
 * <p>
 * Requisitos antes de correrlo:
 * <ol>
 *   <li>Tener Oracle accesible en {@code ORACLE_URL}.</li>
 *   <li>Haber ejecutado {@code install.sql} contra esa instancia.</li>
 *   <li>Exportar también {@code ORACLE_USER} y {@code ORACLE_PASS}.</li>
 * </ol>
 *
 * <h2>Por qué no usar H2 / mock JDBC</h2>
 * El comportamiento que se valida (tipos {@code NUMBER(10)},
 * particionamiento, sequences) es específico de Oracle. Un test contra
 * H2 daría una falsa sensación de cobertura.
 */
@EnabledIfEnvironmentVariable(named = "ORACLE_URL", matches = ".+")
class DatabaseCatalogoSourceIT {

    @Test
    @DisplayName("cargar() trae al menos los ítems sembrados por install.sql")
    void cargar_traeItemsDelCatalogo() throws Exception {
        DatabaseCatalogoSource source = DatabaseCatalogoSource.fromEnv();
        CustomHashTable<Integer, ItemFloral> catalogo = source.cargar();

        assertTrue(catalogo.getSize() > 0,
                "el catálogo debe tener al menos los 4 ítems del seed de install.sql");
        // El ítem 50 (Tulipanes Holandeses Premium) está sembrado en install.sql
        assertNotNull(catalogo.get(50).value(),
                "esperaba encontrar el ítem id=50 sembrado por install.sql");
    }

    @Test
    @DisplayName("cargarMarcas() trae las 3 fincas sembradas por install.sql")
    void cargarMarcas_traeMarcasDelCatalogo() throws Exception {
        DatabaseCatalogoSource source = DatabaseCatalogoSource.fromEnv();
        CustomHashTable<Integer, ProveedorOrigen> marcas = source.cargarMarcas();

        assertTrue(marcas.getSize() >= 3,
                "esperaba al menos las 3 fincas sembradas (Holanda/Ecuador/Guatemala)");
        ProveedorOrigen holanda = marcas.get(5).value();
        assertNotNull(holanda);
        assertEquals("Holanda", holanda.pais());
    }

    @Test
    @DisplayName("descripcion() identifica claramente la fuente Oracle")
    void descripcion_indicaOracle() {
        DatabaseCatalogoSource source = DatabaseCatalogoSource.fromEnv();
        assertTrue(source.descripcion().startsWith("Oracle"),
                "la descripción debe comenzar con 'Oracle' para distinguirla del sintético");
    }

    @Test
    @DisplayName("cargarTiposCliente() trae los 4 tipos sembrados por install.sql")
    void cargarTiposCliente_trae4Tipos() throws Exception {
        DatabaseCatalogoSource source = DatabaseCatalogoSource.fromEnv();
        CustomHashTable<Integer, TipoCliente> tipos = source.cargarTiposCliente();

        assertTrue(tipos.getSize() >= 4,
                "esperaba al menos los 4 tipos sembrados en install.sql");
        // Tipo 2 = "Mayorista Alianzas" con descuento 10.50
        TipoCliente t2 = tipos.get(2).value();
        assertNotNull(t2, "esperaba el tipo de cliente id=2");
        assertEquals(10.50, t2.descuento(), 1e-6,
                "el descuento base del tipo 2 (Mayorista Alianzas) en install.sql es 10.50");
    }
}
