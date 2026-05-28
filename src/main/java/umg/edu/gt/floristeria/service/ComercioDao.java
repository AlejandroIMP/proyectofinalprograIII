package umg.edu.gt.floristeria.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Objeto de acceso a datos (DAO) para las <b>escrituras</b> comerciales en
 * Oracle: alta de facturas con sus detalles, alta de ítems florales y de
 * proveedores, además de una lectura ligera de clientes para poblar los
 * formularios de la web.
 * <p>
 * Es el complemento de escritura de {@link DatabaseCatalogoSource} (lectura del
 * catálogo) y {@link umg.edu.gt.floristeria.graph.CommercialGraph} (lectura de
 * grafos). Reutiliza el mismo patrón de credenciales por variables de entorno o
 * propiedades JVM.
 * <p>
 * <b>Generación de IDs:</b> los datos semilla de {@code install.sql} usan IDs
 * explícitos que se solapan con las sequences, por lo que aquí se generan con
 * {@code NVL(MAX(id), base)+1} para evitar choques de clave primaria.
 * <p>
 * Todos los métodos propagan {@link SQLException}; la capa REST decide cómo
 * comunicar el error al cliente.
 */
public final class ComercioDao {

    private final String url;
    private final String user;
    private final String password;

    public ComercioDao() {
        this.url      = normalizarUrl(oracleProp("ORACLE_URL"));
        this.user     = oracleProp("ORACLE_USER");
        this.password = oracleProp("ORACLE_PASS");
    }

    /** @return {@code true} si hay credenciales Oracle configuradas. */
    public boolean disponible() {
        return url != null && user != null && password != null;
    }

    /* ------------------------------------------------------------------ */
    /*  Lectura ligera para formularios                                    */
    /* ------------------------------------------------------------------ */

    /** Par (id, nombre) de un cliente, para poblar el dropdown de la web. */
    public record ClienteRef(int id, String nombre) {}

    public List<ClienteRef> listarClientes() throws SQLException {
        final String q = "SELECT id_cliente, nombre_completo FROM CLIENTE ORDER BY id_cliente";
        List<ClienteRef> out = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(q)) {
            while (rs.next()) {
                out.add(new ClienteRef(rs.getInt("id_cliente"), rs.getString("nombre_completo")));
            }
        }
        return out;
    }

    /* ------------------------------------------------------------------ */
    /*  Reporte 4.3 — recorrido del grafo por cliente y año                */
    /* ------------------------------------------------------------------ */

    /** Acumulador mutable temporal de una factura mientras se agrupa el ResultSet. */
    private static final class FacAcc {
        LocalDate fecha;
        final List<ReporteGrafoCliente.Linea> lineas = new ArrayList<>();
        double total = 0;
    }

    /**
     * Recorre el grafo comercial a partir de un cliente: facturas emitidas en
     * 2024-2026, con sus detalles y los productos adquiridos. Mide el tiempo de
     * respuesta del recorrido completo (consulta + agrupamiento).
     */
    public ReporteGrafoCliente recorridoCliente(int idCliente) throws SQLException {
        final String q =
                "SELECT c.nombre_completo, "
              + "       EXTRACT(YEAR FROM f.fecha_emision) AS anio, "
              + "       f.id_factura, f.fecha_emision, "
              + "       i.id_item, i.nombre_flor, df.cantidad, df.precio_venta, "
              + "       p.nombre_finca, p.pais_origen "
              + "FROM   CLIENTE c "
              + "JOIN   FACTURA f          ON c.id_cliente   = f.id_cliente "
              + "JOIN   DETALLE_FACTURA df ON f.id_factura   = df.id_factura "
              + "JOIN   ITEM_FLORAL i      ON df.id_item     = i.id_item "
              + "JOIN   PROVEEDOR_ORIGEN p ON i.id_proveedor = p.id_proveedor "
              + "WHERE  c.id_cliente = ? "
              + "AND    EXTRACT(YEAR FROM f.fecha_emision) IN (2024, 2025, 2026) "
              + "ORDER  BY anio, f.id_factura, i.id_item";

        long t0 = System.nanoTime();
        String nombre = "Cliente " + idCliente;
        // Año -> (idFactura -> acumulador). LinkedHashMap preserva el orden del ORDER BY.
        LinkedHashMap<Integer, LinkedHashMap<Integer, FacAcc>> arbol = new LinkedHashMap<>();

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nombre = rs.getString("nombre_completo");
                    int anio  = rs.getInt("anio");
                    int idFac = rs.getInt("id_factura");

                    LinkedHashMap<Integer, FacAcc> facs = arbol.computeIfAbsent(anio, k -> new LinkedHashMap<>());
                    FacAcc fa = facs.computeIfAbsent(idFac, k -> new FacAcc());
                    if (fa.fecha == null) fa.fecha = rs.getDate("fecha_emision").toLocalDate();

                    double subtotal = rs.getDouble("precio_venta");
                    fa.lineas.add(new ReporteGrafoCliente.Linea(
                            rs.getInt("id_item"), rs.getString("nombre_flor"),
                            rs.getInt("cantidad"), subtotal,
                            rs.getString("nombre_finca"), rs.getString("pais_origen")));
                    fa.total += subtotal;
                }
            }
        }

        List<ReporteGrafoCliente.Anio> anios = new ArrayList<>();
        for (var eAnio : arbol.entrySet()) {
            List<ReporteGrafoCliente.Factura> facturas = new ArrayList<>();
            for (var eFac : eAnio.getValue().entrySet()) {
                FacAcc fa = eFac.getValue();
                facturas.add(new ReporteGrafoCliente.Factura(eFac.getKey(), fa.fecha, fa.lineas, fa.total));
            }
            anios.add(new ReporteGrafoCliente.Anio(eAnio.getKey(), facturas));
        }

        long dur = System.nanoTime() - t0;
        return new ReporteGrafoCliente(idCliente, nombre, anios, dur);
    }

    /* ------------------------------------------------------------------ */
    /*  Alta de factura completa (cabecera + detalles) en una transacción  */
    /* ------------------------------------------------------------------ */

    /**
     * Crea una factura con sus líneas de detalle dentro de una sola
     * transacción. El subtotal de cada línea se calcula como
     * {@code cantidad * precio_unitario}, leyendo el precio actual del ítem.
     *
     * @param idCliente cliente existente al que se factura
     * @param lineas    lista de pares {@code {idItem, cantidad}}
     * @return el id de factura generado
     * @throws SQLException si falla cualquier inserción (se hace rollback)
     */
    public int crearFactura(int idCliente, List<int[]> lineas) throws SQLException {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, user, password);
            conn.setAutoCommit(false);

            int idFactura = siguienteId(conn, "SELECT NVL(MAX(id_factura),2000)+1 FROM FACTURA");
            int numero    = siguienteId(conn, "SELECT NVL(MAX(numero_documento),1022)+1 FROM FACTURA WHERE serie='A'");

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO FACTURA (id_factura, fecha_emision, id_cliente, serie, numero_documento) "
                  + "VALUES (?, SYSDATE, ?, 'A', ?)")) {
                ps.setInt(1, idFactura);
                ps.setInt(2, idCliente);
                ps.setInt(3, numero);
                ps.executeUpdate();
            }

            int idDetalle = siguienteId(conn, "SELECT NVL(MAX(id_detalle),0)+1 FROM DETALLE_FACTURA");
            for (int[] linea : lineas) {
                int idItem   = linea[0];
                int cantidad = linea[1];
                double precio = precioUnitario(conn, idItem);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO DETALLE_FACTURA (id_detalle, id_factura, id_item, cantidad, precio_venta) "
                      + "VALUES (?, ?, ?, ?, ?)")) {
                    ps.setInt(1, idDetalle++);
                    ps.setInt(2, idFactura);
                    ps.setInt(3, idItem);
                    ps.setInt(4, cantidad);
                    ps.setDouble(5, cantidad * precio);
                    ps.executeUpdate();
                }
            }

            conn.commit();
            return idFactura;
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) { }
            throw e;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) { }
        }
    }

    private static int siguienteId(Connection conn, String query) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(query)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static double precioUnitario(Connection conn, int idItem) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT precio_unitario FROM ITEM_FLORAL WHERE id_item = ?")) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
                throw new SQLException("El ítem " + idItem + " no existe en ITEM_FLORAL");
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Alta de ítem y de proveedor                                        */
    /* ------------------------------------------------------------------ */

    public void insertarItem(int id, String nombre, double precio, int idProveedor) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO ITEM_FLORAL (id_item, nombre_flor, precio_unitario, id_proveedor) "
                   + "VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, nombre);
            ps.setDouble(3, precio);
            ps.setInt(4, idProveedor);
            ps.executeUpdate();
        }
    }

    public void insertarMarca(int id, String nombreFinca, String pais) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO PROVEEDOR_ORIGEN (id_proveedor, nombre_finca, pais_origen) "
                   + "VALUES (?, ?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, nombreFinca);
            ps.setString(3, pais);
            ps.executeUpdate();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Credenciales (mismo patrón que DatabaseCatalogoSource)             */
    /* ------------------------------------------------------------------ */

    private static String oracleProp(String nombre) {
        String v = System.getenv(nombre);
        return (v != null && !v.isBlank()) ? v : System.getProperty(nombre);
    }

    /** Antepone el prefijo JDBC si la URL viene en forma corta (host:puerto/servicio). */
    static String normalizarUrl(String url) {
        if (url == null) return null;
        url = url.trim();
        if (url.startsWith("jdbc:")) return url;
        if (url.startsWith("@"))     return "jdbc:oracle:thin:" + url;
        if (url.startsWith("//"))    return "jdbc:oracle:thin:@" + url;
        return "jdbc:oracle:thin:@//" + url;
    }
}
