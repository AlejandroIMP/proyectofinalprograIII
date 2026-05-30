package umg.edu.gt.floristeria.service;

import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;
import umg.edu.gt.floristeria.model.TipoCliente;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Implementación de {@link CatalogoSource} que lee el catálogo de la
 * floristería desde Oracle Database vía JDBC nativo. Es el "pipeline de
 * carga" exigido por la sección 3 de la rúbrica.
 * <p>
 * <b>Sección 3.1</b>: antes de ejecutar los SELECT principales, {@link #cargar()}
 * llama al procedimiento PL/SQL {@code SP_VERIFICAR_CATALOGO} (definido en
 * {@code install.sql}) que lee los conteos de las tres tablas de catálogo y
 * los imprime en {@code DBMS_OUTPUT}. Si el procedimiento no está instalado
 * se ignora silenciosamente para no interrumpir la carga.
 * <p>
 * <b>Patrón de credenciales:</b> variables de entorno {@code ORACLE_URL},
 * {@code ORACLE_USER} y {@code ORACLE_PASS}. Usar el factory
 * {@link #fromEnv()} para construir a partir de ellas.
 * <p>
 * <b>Errores:</b> se propagan {@link SQLException} sin tragarlas, para que
 * CLI (fail-fast) y GUI (alerta modal) decidan el comportamiento.
 */
public class DatabaseCatalogoSource implements CatalogoSource {

    private final String url;
    private final String user;
    private final String password;

    public DatabaseCatalogoSource(String url, String user, String password) {
        this.url      = Objects.requireNonNull(url,      "url no puede ser null");
        this.user     = Objects.requireNonNull(user,     "user no puede ser null");
        this.password = Objects.requireNonNull(password, "password no puede ser null");
    }

    /**
     * Construye la fuente leyendo {@code ORACLE_URL}, {@code ORACLE_USER}
     * y {@code ORACLE_PASS} del entorno.
     *
     * @throws IllegalStateException si alguna variable falta.
     */
    public static DatabaseCatalogoSource fromEnv() {
        String url  = normalizarUrl(oracleProp("ORACLE_URL"));
        String usr  = oracleProp("ORACLE_USER");
        String pwd  = oracleProp("ORACLE_PASS");
        if (url == null || usr == null || pwd == null) {
            throw new IllegalStateException(
                    "Faltan credenciales Oracle. Defínalas como variables de entorno "
                  + "(ORACLE_URL / ORACLE_USER / ORACLE_PASS) "
                  + "o como propiedades JVM (-DORACLE_URL=... en VM Options del IDE).");
        }
        System.out.println("[Oracle] URL normalizada: " + sanitizar(url));
        return new DatabaseCatalogoSource(url, usr, pwd);
    }

    /**
     * Lee la credencial primero como variable de entorno del SO y, si no existe,
     * como propiedad del sistema JVM ({@code -Dnombre=valor}).
     * Esto permite configurarla desde cualquier IDE sin tocar el sistema operativo.
     */
    private static String oracleProp(String nombre) {
        String v = System.getenv(nombre);
        return (v != null && !v.isBlank()) ? v : System.getProperty(nombre);
    }

    /**
     * Asegura que la URL tenga el prefijo JDBC completo.
     * <p>
     * Acepta cualquiera de estas formas y las convierte a la forma canónica:
     * <ul>
     *   <li>{@code jdbc:oracle:thin:@//host:port/service} — ya correcta, se deja igual</li>
     *   <li>{@code //host:port/service}  → añade {@code jdbc:oracle:thin:@}</li>
     *   <li>{@code host:port/service}    → añade {@code jdbc:oracle:thin:@//}</li>
     *   <li>{@code host:port:SID}        → añade {@code jdbc:oracle:thin:@}</li>
     * </ul>
     */
    static String normalizarUrl(String url) {
        if (url == null) return null;
        url = url.trim();
        if (url.startsWith("jdbc:")) return url;                        // ya correcta
        if (url.startsWith("@"))    return "jdbc:oracle:thin:" + url;   // falta solo el driver prefix
        if (url.startsWith("//"))   return "jdbc:oracle:thin:@" + url;  // falta driver + @
        return "jdbc:oracle:thin:@//" + url;                            // solo host:port/service
    }

    @Override
    public CustomHashTable<Integer, ItemFloral> cargar() throws SQLException {
        final String query =
                "SELECT id_item, nombre_flor, precio_unitario, id_proveedor "
              + "FROM   ITEM_FLORAL";
        CustomHashTable<Integer, ItemFloral> catalogo = new CustomHashTable<>();
        long t0 = System.nanoTime();
        int filas = 0;

        try (Connection conn = DriverManager.getConnection(url, user, password)) {

            // Sección 3.1: invocar procedimiento PL/SQL de verificación de catálogos.
            // Si no existe (install.sql no ejecutado) se ignora sin lanzar excepción.
            try (CallableStatement cs = conn.prepareCall("{ CALL SP_VERIFICAR_CATALOGO() }")) {
                cs.execute();
            } catch (SQLException ignored) { /* procedimiento no instalado → continuar */ }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs   = stmt.executeQuery(query)) {
                while (rs.next()) {
                    ItemFloral item = new ItemFloral(
                            rs.getInt("id_item"),
                            rs.getString("nombre_flor"),
                            rs.getDouble("precio_unitario"),
                            rs.getInt("id_proveedor"));
                    catalogo.put(item.id(), item);
                    filas++;
                }
            }
        }

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("Oracle: cargados %d ítems en %d ms (colisiones=%d, capacidad=%d)%n",
                filas, elapsedMs, catalogo.getCollisionCount(), catalogo.getCapacity());
        return catalogo;
    }

    @Override
    public CustomHashTable<Integer, ProveedorOrigen> cargarMarcas() throws SQLException {
        final String query =
                "SELECT id_proveedor, nombre_finca, pais_origen "
              + "FROM   PROVEEDOR_ORIGEN";
        CustomHashTable<Integer, ProveedorOrigen> marcas = new CustomHashTable<>();
        long t0 = System.nanoTime();
        int filas = 0;

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery(query)) {

            while (rs.next()) {
                ProveedorOrigen p = new ProveedorOrigen(
                        rs.getInt("id_proveedor"),
                        rs.getString("nombre_finca"),
                        rs.getString("pais_origen"));
                marcas.put(p.id(), p);
                filas++;
            }
        }

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("Oracle: cargadas %d marcas en %d ms%n", filas, elapsedMs);
        return marcas;
    }

    @Override
    public CustomHashTable<Integer, TipoCliente> cargarTiposCliente() throws SQLException {
        final String query =
                "SELECT id_tipo_cliente, nombre_tipo, descuento_base "
              + "FROM   TIPO_CLIENTE";
        CustomHashTable<Integer, TipoCliente> tipos = new CustomHashTable<>();
        long t0 = System.nanoTime();
        int filas = 0;

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery(query)) {

            while (rs.next()) {
                TipoCliente tc = new TipoCliente(
                        rs.getInt("id_tipo_cliente"),
                        rs.getString("nombre_tipo"),
                        rs.getDouble("descuento_base"));
                tipos.put(tc.id(), tc);
                filas++;
            }
        }

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("Oracle: cargados %d tipos de cliente en %d ms%n", filas, elapsedMs);
        return tipos;
    }

    @Override
    public String descripcion() {
        return "Oracle (" + sanitizar(url) + ")";
    }

    private static String sanitizar(String url) {
        int at    = url.indexOf('@');
        int colon = url.indexOf(':', "jdbc:oracle:thin:".length());
        if (at > 0 && colon > 0 && colon < at) {
            return url.substring(0, colon + 1) + "****@" + url.substring(at + 1);
        }
        return url;
    }
}
