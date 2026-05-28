package umg.edu.gt.floristeria.graph;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Construye grafos dirigidos en memoria a partir de consultas JDBC a Oracle.
 * <p>
 * Los campos de credenciales se resuelven desde variables de entorno en el
 * momento de la construcción. Si {@code ORACLE_URL} es {@code null} (Oracle
 * no configurado), cada método {@code construirGrafoXxx} retorna con el grafo
 * vacío en lugar de lanzar {@code "The url cannot be null"}.
 * <p>
 * Consultas disponibles:
 * <ol>
 *   <li>Trazabilidad de factura (Cliente → Factura → Ítem → Proveedor)</li>
 *   <li>Productos de un cliente (Cliente → Facturas → Ítems → Proveedor)</li>
 *   <li>Impacto de un proveedor (Proveedor → Ítems → Facturas → Clientes)</li>
 *   <li>Productos de un cliente en rango de años (igual que 2 con filtro de año)</li>
 *   <li>Trazabilidad inversa: dado un ítem, todos los clientes que lo compraron</li>
 * </ol>
 */
public class CommercialGraph {

    private final List<Nodo>   nodos   = new ArrayList<>();
    private final List<Arista> aristas = new ArrayList<>();

    private final String url      = System.getenv("ORACLE_URL");
    private final String user     = System.getenv("ORACLE_USER");
    private final String password = System.getenv("ORACLE_PASS");

    public List<Nodo>   getNodos()   { return nodos; }
    public List<Arista> getAristas() { return aristas; }

    public void agregarNodo(String id, String label, String tipo) {
        if (nodos.stream().noneMatch(n -> n.getId().equals(id))) {
            nodos.add(new Nodo(id, label, tipo));
        }
    }

    public void agregarArista(String origen, String destino, String relacion) {
        aristas.add(new Arista(origen, destino, relacion));
    }

    /** Retorna true y registra aviso si Oracle no está configurado. */
    private boolean oracleAusente(String contexto) {
        if (url == null) {
            System.err.println("[CommercialGraph] ORACLE_URL no configurado — "
                    + contexto + " retorna grafo vacío.");
            return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSULTA 1 — Trazabilidad de una factura completa
    //              Cliente → Factura → Ítem → Proveedor / Finca
    // ─────────────────────────────────────────────────────────────────────────
    public void construirGrafoTrazabilidad(int idFactura) {
        nodos.clear();
        aristas.clear();
        if (oracleAusente("trazabilidad")) return;

        String query =
                "SELECT c.id_cliente, c.nombre_completo AS cliente, " +
                "f.id_factura, f.fecha_emision, " +
                "i.id_item, i.nombre_flor, " +
                "p.id_proveedor, p.nombre_finca " +
                "FROM   FACTURA f " +
                "JOIN   CLIENTE c          ON f.id_cliente   = c.id_cliente " +
                "JOIN   DETALLE_FACTURA df ON f.id_factura   = df.id_factura " +
                "JOIN   ITEM_FLORAL i      ON df.id_item     = i.id_item " +
                "JOIN   PROVEEDOR_ORIGEN p ON i.id_proveedor = p.id_proveedor " +
                "WHERE  f.id_factura = ?";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, idFactura);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String cliId  = "CLI_" + rs.getInt("id_cliente");
                    String facId  = "FAC_" + rs.getInt("id_factura");
                    String itemId = "ITM_" + rs.getInt("id_item");
                    String provId = "PRV_" + rs.getInt("id_proveedor");

                    agregarNodo(cliId,  rs.getString("cliente"),     "CLIENTE");
                    agregarNodo(facId,  "Factura #" + rs.getInt("id_factura"), "FACTURA");
                    agregarNodo(itemId, rs.getString("nombre_flor"), "ITEM");
                    agregarNodo(provId, rs.getString("nombre_finca"), "PROVEEDOR");

                    agregarArista(cliId,  facId,  "COMPRO_EN");
                    agregarArista(facId,  itemId, "CONTIENE");
                    agregarArista(itemId, provId, "PROVIENE_DE");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al construir grafo de trazabilidad: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSULTA 2 — Todos los productos comprados por un cliente
    //              Cliente → Facturas → Ítems → Proveedor / Finca
    // ─────────────────────────────────────────────────────────────────────────
    public void construirGrafoClienteProductos(int idCliente) {
        nodos.clear();
        aristas.clear();
        if (oracleAusente("cliente-productos")) return;

        String query =
                "SELECT c.id_cliente, c.nombre_completo AS cliente, " +
                "f.id_factura, " +
                "i.id_item, i.nombre_flor, " +
                "p.id_proveedor, p.nombre_finca " +
                "FROM   CLIENTE c " +
                "JOIN   FACTURA f          ON c.id_cliente   = f.id_cliente " +
                "JOIN   DETALLE_FACTURA df ON f.id_factura   = df.id_factura " +
                "JOIN   ITEM_FLORAL i      ON df.id_item     = i.id_item " +
                "JOIN   PROVEEDOR_ORIGEN p ON i.id_proveedor = p.id_proveedor " +
                "WHERE  c.id_cliente = ?";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, idCliente);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String cliId  = "CLI_" + rs.getInt("id_cliente");
                    String facId  = "FAC_" + rs.getInt("id_factura");
                    String itemId = "ITM_" + rs.getInt("id_item");
                    String provId = "PRV_" + rs.getInt("id_proveedor");

                    agregarNodo(cliId,  rs.getString("cliente"),      "CLIENTE");
                    agregarNodo(facId,  "Factura #" + rs.getInt("id_factura"), "FACTURA");
                    agregarNodo(itemId, rs.getString("nombre_flor"),  "ITEM");
                    agregarNodo(provId, rs.getString("nombre_finca"), "PROVEEDOR");

                    agregarArista(cliId,  facId,  "COMPRO_EN");
                    agregarArista(facId,  itemId, "CONTIENE");
                    agregarArista(itemId, provId, "PROVIENE_DE");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al construir grafo cliente-productos: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSULTA 3 — Impacto de un proveedor
    //              Proveedor → Ítems → Facturas → Clientes
    // ─────────────────────────────────────────────────────────────────────────
    public void construirGrafoProveedorImpacto(int idProveedor) {
        nodos.clear();
        aristas.clear();
        if (oracleAusente("proveedor-impacto")) return;

        String query =
                "SELECT p.id_proveedor, p.nombre_finca, " +
                "i.id_item, i.nombre_flor, " +
                "f.id_factura, " +
                "c.id_cliente, c.nombre_completo AS cliente " +
                "FROM   PROVEEDOR_ORIGEN p " +
                "JOIN   ITEM_FLORAL i      ON p.id_proveedor = i.id_proveedor " +
                "JOIN   DETALLE_FACTURA df ON i.id_item       = df.id_item " +
                "JOIN   FACTURA f          ON df.id_factura   = f.id_factura " +
                "JOIN   CLIENTE c          ON f.id_cliente    = c.id_cliente " +
                "WHERE  p.id_proveedor = ?";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, idProveedor);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String provId = "PRV_" + rs.getInt("id_proveedor");
                    String itemId = "ITM_" + rs.getInt("id_item");
                    String facId  = "FAC_" + rs.getInt("id_factura");
                    String cliId  = "CLI_" + rs.getInt("id_cliente");

                    agregarNodo(provId, rs.getString("nombre_finca"),  "PROVEEDOR");
                    agregarNodo(itemId, rs.getString("nombre_flor"),   "ITEM");
                    agregarNodo(facId,  "Factura #" + rs.getInt("id_factura"), "FACTURA");
                    agregarNodo(cliId,  rs.getString("cliente"),       "CLIENTE");

                    agregarArista(provId, itemId, "SUMINISTRA");
                    agregarArista(itemId, facId,  "APARECE_EN");
                    agregarArista(facId,  cliId,  "PERTENECE_A");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al construir grafo proveedor-impacto: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSULTA 4 — Productos de un cliente filtrados por rango de años
    //              (Sección 3.3: "todos los productos … en un rango de años")
    // ─────────────────────────────────────────────────────────────────────────
    public void construirGrafoClienteProductosPorAnio(
            int idCliente, int anioInicio, int anioFin) {
        nodos.clear();
        aristas.clear();
        if (oracleAusente("cliente-productos-anio")) return;

        String query =
                "SELECT c.id_cliente, c.nombre_completo AS cliente, " +
                "f.id_factura, f.fecha_emision, " +
                "i.id_item, i.nombre_flor, " +
                "p.id_proveedor, p.nombre_finca " +
                "FROM   CLIENTE c " +
                "JOIN   FACTURA f          ON c.id_cliente   = f.id_cliente " +
                "JOIN   DETALLE_FACTURA df ON f.id_factura   = df.id_factura " +
                "JOIN   ITEM_FLORAL i      ON df.id_item     = i.id_item " +
                "JOIN   PROVEEDOR_ORIGEN p ON i.id_proveedor = p.id_proveedor " +
                "WHERE  c.id_cliente = ? " +
                "AND    EXTRACT(YEAR FROM f.fecha_emision) BETWEEN ? AND ?";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, idCliente);
            pstmt.setInt(2, anioInicio);
            pstmt.setInt(3, anioFin);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String cliId  = "CLI_" + rs.getInt("id_cliente");
                    String facId  = "FAC_" + rs.getInt("id_factura");
                    String itemId = "ITM_" + rs.getInt("id_item");
                    String provId = "PRV_" + rs.getInt("id_proveedor");

                    agregarNodo(cliId,  rs.getString("cliente"),      "CLIENTE");
                    agregarNodo(facId,  "Factura #" + rs.getInt("id_factura")
                            + " (" + rs.getDate("fecha_emision").toLocalDate().getYear() + ")", "FACTURA");
                    agregarNodo(itemId, rs.getString("nombre_flor"),  "ITEM");
                    agregarNodo(provId, rs.getString("nombre_finca"), "PROVEEDOR");

                    agregarArista(cliId,  facId,  "COMPRO_EN");
                    agregarArista(facId,  itemId, "CONTIENE");
                    agregarArista(itemId, provId, "PROVIENE_DE");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al construir grafo cliente-productos-anio: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSULTA 5 — Trazabilidad inversa: dado un ítem, todos los clientes
    //              que lo han comprado  (Sección 3.3: "trazabilidad inversa")
    // ─────────────────────────────────────────────────────────────────────────
    public void construirGrafoTrazabilidadInversa(int idItem) {
        nodos.clear();
        aristas.clear();
        if (oracleAusente("trazabilidad-inversa")) return;

        String query =
                "SELECT i.id_item, i.nombre_flor, " +
                "f.id_factura, " +
                "c.id_cliente, c.nombre_completo AS cliente " +
                "FROM   ITEM_FLORAL i " +
                "JOIN   DETALLE_FACTURA df ON i.id_item     = df.id_item " +
                "JOIN   FACTURA f          ON df.id_factura = f.id_factura " +
                "JOIN   CLIENTE c          ON f.id_cliente  = c.id_cliente " +
                "WHERE  i.id_item = ?";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, idItem);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String itemId = "ITM_" + rs.getInt("id_item");
                    String facId  = "FAC_" + rs.getInt("id_factura");
                    String cliId  = "CLI_" + rs.getInt("id_cliente");

                    agregarNodo(itemId, rs.getString("nombre_flor"), "ITEM");
                    agregarNodo(facId,  "Factura #" + rs.getInt("id_factura"), "FACTURA");
                    agregarNodo(cliId,  rs.getString("cliente"),     "CLIENTE");

                    agregarArista(itemId, facId, "APARECE_EN");
                    agregarArista(facId,  cliId, "COMPRADA_POR");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al construir grafo trazabilidad-inversa: " + e.getMessage());
        }
    }
}
