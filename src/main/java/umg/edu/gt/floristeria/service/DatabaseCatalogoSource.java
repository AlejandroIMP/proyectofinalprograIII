package umg.edu.gt.floristeria.service;

import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;

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
 * <b>Patrón de credenciales:</b> consistente con el resto del proyecto
 * (ver {@code graph/CommercialGraph.java}), las credenciales se leen de
 * variables de entorno {@code ORACLE_URL}, {@code ORACLE_USER} y
 * {@code ORACLE_PASS}. Usar el factory estático {@link #fromEnv()} para
 * construir una instancia que las consuma; o el constructor explícito
 * cuando se necesite proveerlas directamente (p. ej. en tests).
 * <p>
 * <b>Driver:</b> con {@code ojdbc11} (JDBC 4+) el driver se auto-registra
 * vía SPI; no se requiere {@code Class.forName(...)}.
 * <p>
 * <b>Errores:</b> esta clase <em>no</em> traga excepciones. {@link #cargar()}
 * y {@link #cargarMarcas()} propagan {@link SQLException} para que la
 * capa de presentación decida el comportamiento (fail-fast en CLI, alerta
 * modal en GUI).
 */
public class DatabaseCatalogoSource implements CatalogoSource {

    private final String url;
    private final String user;
    private final String password;

    /**
     * Construye una fuente que se conecta a la URL JDBC indicada con las
     * credenciales dadas. Falla rápido si alguno de los tres parámetros
     * es {@code null}.
     */
    public DatabaseCatalogoSource(String url, String user, String password) {
        this.url      = Objects.requireNonNull(url,      "url no puede ser null");
        this.user     = Objects.requireNonNull(user,     "user no puede ser null");
        this.password = Objects.requireNonNull(password, "password no puede ser null");
    }

    /**
     * Construye la fuente leyendo {@code ORACLE_URL}, {@code ORACLE_USER}
     * y {@code ORACLE_PASS} del entorno.
     *
     * @throws IllegalStateException si alguna variable falta, indicando
     *         cuáles son requeridas. Esto se prefiere sobre {@code null}
     *         silencioso para dar un mensaje accionable al desarrollador.
     */
    public static DatabaseCatalogoSource fromEnv() {
        String url  = System.getenv("ORACLE_URL");
        String usr  = System.getenv("ORACLE_USER");
        String pwd  = System.getenv("ORACLE_PASS");
        if (url == null || usr == null || pwd == null) {
            throw new IllegalStateException(
                    "Faltan variables de entorno para Oracle. Defina ORACLE_URL, "
                  + "ORACLE_USER y ORACLE_PASS antes de usar DatabaseCatalogoSource.");
        }
        return new DatabaseCatalogoSource(url, usr, pwd);
    }

    @Override
    public CustomHashTable<Integer, ItemFloral> cargar() throws SQLException {
        final String query =
                "SELECT id_item, nombre_flor, precio_unitario, id_proveedor "
              + "FROM   ITEM_FLORAL";
        CustomHashTable<Integer, ItemFloral> catalogo = new CustomHashTable<>();
        long t0 = System.nanoTime();
        int filas = 0;

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery(query)) {

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
    public String descripcion() {
        return "Oracle (" + sanitizar(url) + ")";
    }

    /**
     * Si la URL contiene credenciales embebidas (formato no recomendado pero
     * posible: {@code jdbc:oracle:thin:user/pass@host:1521/svc}), las oculta
     * antes de mostrarla en logs o en la GUI.
     */
    private static String sanitizar(String url) {
        int at = url.indexOf('@');
        int colon = url.indexOf(':', "jdbc:oracle:thin:".length());
        if (at > 0 && colon > 0 && colon < at) {
            return url.substring(0, colon + 1) + "****@" + url.substring(at + 1);
        }
        return url;
    }
}
