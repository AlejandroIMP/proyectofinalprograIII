package umg.edu.gt.floristeria.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración de {@link ComercioDao} contra Oracle real.
 * <p>
 * Cubre los métodos de escritura (insertarItem, insertarMarca,
 * insertarTipoCliente, crearFactura) y el recorrido de lectura
 * (recorridoCliente) usando los datos sembrados por {@code install.sql}.
 * Los IDs de inserción se eligen en un rango alto (9000+) que no choca con
 * la semilla y se eliminan en {@link #limpiar()} para que las re-ejecuciones
 * de {@code mvn verify} sean idempotentes.
 */
@EnabledIfEnvironmentVariable(named = "ORACLE_URL", matches = ".+")
class ComercioDaoIT {

    private static final int ID_ITEM   = 9050;
    private static final int ID_MARCA  = 9060;
    private static final int ID_TIPO   = 9070;
    /** Cliente semilla con facturas para el recorrido. */
    private static final int ID_CLIENTE_SEED = 101;
    /** Ítem semilla para construir factura. */
    private static final int ID_ITEM_SEED    = 50;

    private final ComercioDao dao = new ComercioDao();

    /** Borra cualquier resto de corridas anteriores antes de empezar y tras terminar. */
    @BeforeEach
    @AfterEach
    void limpiar() throws SQLException {
        if (!dao.disponible()) return;
        try (Connection conn = abrirConexion()) {
            ejecutar(conn, "DELETE FROM DETALLE_FACTURA WHERE id_item = ?", ID_ITEM);
            ejecutar(conn, "DELETE FROM ITEM_FLORAL     WHERE id_item = ?", ID_ITEM);
            ejecutar(conn, "DELETE FROM PROVEEDOR_ORIGEN WHERE id_proveedor = ?", ID_MARCA);
            ejecutar(conn, "DELETE FROM TIPO_CLIENTE    WHERE id_tipo_cliente = ?", ID_TIPO);
            // Las facturas creadas con ID >= 9000 (test) se borran junto con su detalle.
            ejecutar(conn, "DELETE FROM DETALLE_FACTURA WHERE id_factura IN (SELECT id_factura FROM FACTURA WHERE id_factura >= 9000)", null);
            ejecutar(conn, "DELETE FROM FACTURA         WHERE id_factura >= 9000", null);
        }
    }

    @Test
    @DisplayName("disponible() = true cuando el entorno Oracle está completo")
    void disponible_true() {
        assertTrue(dao.disponible(), "ORACLE_URL/USER/PASS deben estar configurados");
    }

    @Test
    @DisplayName("listarClientes() devuelve al menos los 4 clientes semilla")
    void listarClientes_traeAlMenos4() throws SQLException {
        List<ComercioDao.ClienteRef> cs = dao.listarClientes();
        assertTrue(cs.size() >= 4,
                "esperaba ≥ 4 clientes seed (101-104), obtenidos: " + cs.size());
        // Y todos deben venir con nombre no nulo.
        for (ComercioDao.ClienteRef c : cs) {
            assertNotNull(c.nombre());
            assertTrue(c.id() > 0);
        }
    }

    @Test
    @DisplayName("insertarMarca persiste la fila y se puede leer con SELECT")
    void insertarMarca_persiste() throws SQLException {
        dao.insertarMarca(ID_MARCA, "Finca Test IT", "ChileTest");

        try (Connection conn = abrirConexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT nombre_finca, pais_origen FROM PROVEEDOR_ORIGEN WHERE id_proveedor = ?")) {
            ps.setInt(1, ID_MARCA);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "la marca insertada debe existir en la BD");
                assertEquals("Finca Test IT", rs.getString(1));
                assertEquals("ChileTest",     rs.getString(2));
            }
        }
    }

    @Test
    @DisplayName("insertarItem persiste el ítem con su precio y proveedor")
    void insertarItem_persiste() throws SQLException {
        // Necesita un proveedor existente — usamos uno semilla (5 = Holanda).
        dao.insertarItem(ID_ITEM, "Flor Test IT", 17.75, 5);

        try (Connection conn = abrirConexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT nombre_flor, precio_unitario, id_proveedor FROM ITEM_FLORAL WHERE id_item = ?")) {
            ps.setInt(1, ID_ITEM);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Flor Test IT", rs.getString(1));
                assertEquals(17.75, rs.getDouble(2), 1e-6);
                assertEquals(5,     rs.getInt(3));
            }
        }
    }

    @Test
    @DisplayName("insertarTipoCliente persiste el tipo con su descuento")
    void insertarTipoCliente_persiste() throws SQLException {
        dao.insertarTipoCliente(ID_TIPO, "Corporativo IT", 8.25);

        try (Connection conn = abrirConexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT nombre_tipo, descuento_base FROM TIPO_CLIENTE WHERE id_tipo_cliente = ?")) {
            ps.setInt(1, ID_TIPO);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Corporativo IT", rs.getString(1));
                assertEquals(8.25, rs.getDouble(2), 1e-6);
            }
        }
    }

    @Test
    @DisplayName("crearFactura genera cabecera + detalle y devuelve id > 0")
    void crearFactura_persisteCabezeraYDetalle() throws SQLException {
        int idFactura = dao.crearFactura(ID_CLIENTE_SEED,
                List.of(new int[]{ ID_ITEM_SEED, 2 }));

        assertTrue(idFactura > 0);

        try (Connection conn = abrirConexion();
             PreparedStatement psCab = conn.prepareStatement(
                     "SELECT id_cliente, serie FROM FACTURA WHERE id_factura = ?");
             PreparedStatement psDet = conn.prepareStatement(
                     "SELECT cantidad, precio_venta FROM DETALLE_FACTURA WHERE id_factura = ?")) {

            psCab.setInt(1, idFactura);
            try (ResultSet rs = psCab.executeQuery()) {
                assertTrue(rs.next(), "la factura debe existir");
                assertEquals(ID_CLIENTE_SEED, rs.getInt(1));
                assertEquals("A", rs.getString(2));
            }

            psDet.setInt(1, idFactura);
            try (ResultSet rs = psDet.executeQuery()) {
                assertTrue(rs.next(), "la factura debe tener al menos 1 detalle");
                assertEquals(2, rs.getInt(1));
                assertTrue(rs.getDouble(2) > 0, "precio_venta = cantidad * precio_unitario > 0");
            }
        }
    }

    @Test
    @DisplayName("recorridoCliente devuelve el nombre del cliente y mide tiempo > 0")
    void recorridoCliente_traeNombreYDuracion() throws SQLException {
        ReporteGrafoCliente data = dao.recorridoCliente(ID_CLIENTE_SEED);

        assertEquals(ID_CLIENTE_SEED, data.idCliente());
        assertNotNull(data.nombreCliente());
        assertFalse(data.nombreCliente().isBlank(),
                "el nombre del cliente debe venir poblado desde Oracle");
        assertTrue(data.durationNs() > 0,
                "la medición de duración debe ser > 0 nanosegundos");
        // anios puede ser vacío si el cliente no tiene facturas en 2024-2026 — el test
        // solo exige consistencia estructural.
        assertNotNull(data.anios());
    }

    /* ----- Helpers JDBC compartidos por todos los IT ----- */

    private static Connection abrirConexion() throws SQLException {
        return DriverManager.getConnection(envUrlNormalizada(), env("ORACLE_USER"), env("ORACLE_PASS"));
    }

    private static String env(String n) {
        String v = System.getenv(n);
        return (v != null && !v.isBlank()) ? v : System.getProperty(n);
    }

    /** Replica la normalización de ComercioDao para construir la URL JDBC completa. */
    private static String envUrlNormalizada() {
        String url = env("ORACLE_URL").trim();
        if (url.startsWith("jdbc:")) return url;
        if (url.startsWith("@"))     return "jdbc:oracle:thin:" + url;
        if (url.startsWith("//"))    return "jdbc:oracle:thin:@" + url;
        return "jdbc:oracle:thin:@//" + url;
    }

    private static void ejecutar(Connection conn, String sql, Integer arg) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (arg != null) ps.setInt(1, arg);
            ps.executeUpdate();
        }
    }
}
