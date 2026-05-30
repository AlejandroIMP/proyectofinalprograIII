package umg.edu.gt.floristeria.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import umg.edu.gt.floristeria.graph.CommercialGraph;
import umg.edu.gt.floristeria.graph.Nodo;
import umg.edu.gt.floristeria.graph.Arista;
import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;
import umg.edu.gt.floristeria.model.TipoCliente;
import umg.edu.gt.floristeria.service.ComercioDao;
import umg.edu.gt.floristeria.service.ReportService;
import umg.edu.gt.floristeria.service.ReporteGrafoCliente;
import umg.edu.gt.floristeria.service.WordReportExporter;
import umg.edu.gt.floristeria.util.Durations;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Servidor HTTP nativo (com.sun.net.httpserver) que expone la API REST
 * de grafos y de la tabla hash del proyecto, además de servir el frontend
 * HTML5/Vis.js.
 *
 * <h2>Endpoints de grafos</h2>
 * <ul>
 *   <li>GET /api/grafo/trazabilidad?factura={id}</li>
 *   <li>GET /api/grafo/cliente-productos?cliente={id}</li>
 *   <li>GET /api/grafo/proveedor-impacto?proveedor={id}</li>
 *   <li>GET /api/grafo/cliente-productos-anio?cliente={id}&amp;anioInicio={y}&amp;anioFin={y}</li>
 *   <li>GET /api/grafo/trazabilidad-inversa?item={id}</li>
 * </ul>
 *
 * <h2>Endpoints de tabla hash</h2>
 * <ul>
 *   <li>GET /api/hash/catalogo  — serializa la tabla hash de ítems florales</li>
 *   <li>GET /api/hash/marcas    — serializa la tabla hash de proveedores</li>
 * </ul>
 *
 * <h2>Frontend</h2>
 * <ul>
 *   <li>GET /  — sirve web/index.html</li>
 * </ul>
 */
public class GraphRestApi {

    // Referencias a las hash tables cargadas al arrancar la aplicación.
    // Se establecen una sola vez en iniciarServidor() y luego son solo-lectura.
    private static CustomHashTable<Integer, ItemFloral>      catalogoRef;
    private static CustomHashTable<Integer, ProveedorOrigen> marcasRef;
    private static CustomHashTable<Integer, TipoCliente>     tiposRef;

    /**
     * Lanza el servidor HTTP en el puerto 8085 y registra todos los endpoints.
     *
     * @param catalogo tabla hash del catálogo de ítems (puede ser null si no hay Oracle)
     * @param marcas   tabla hash de proveedores/marcas (puede ser null)
     * @param tipos    tabla hash de tipos de cliente (puede ser null)
     */
    public static void iniciarServidor(
            CustomHashTable<Integer, ItemFloral>      catalogo,
            CustomHashTable<Integer, ProveedorOrigen> marcas,
            CustomHashTable<Integer, TipoCliente>     tipos) {
        catalogoRef = catalogo;
        marcasRef   = marcas;
        tiposRef    = tipos;

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8085), 0);

            // Grafos
            server.createContext("/api/grafo/trazabilidad",            new TrazabilidadHandler());
            server.createContext("/api/grafo/cliente-productos-anio",  new ClienteProductosAnioHandler());
            server.createContext("/api/grafo/cliente-productos",       new ClienteProductosHandler());
            server.createContext("/api/grafo/proveedor-impacto",       new ProveedorImpactoHandler());
            server.createContext("/api/grafo/trazabilidad-inversa",    new TrazabilidadInversaHandler());
            server.createContext("/api/grafo/tipo-clientes",           new TipoClientesGrafoHandler());

            // Tabla hash (GET = leer, POST = agregar con detección de colisión)
            server.createContext("/api/hash/catalogo",                 new HashCatalogoHandler());
            server.createContext("/api/hash/marcas",                   new HashMarcasHandler());
            server.createContext("/api/hash/tipos-cliente",            new HashTiposClienteHandler());

            // Escrituras comerciales y soporte de formularios
            server.createContext("/api/grafo/factura",                 new FacturaHandler());
            server.createContext("/api/clientes",                      new ClientesHandler());

            // Reportes Microsoft Word (.docx) descargables
            server.createContext("/api/reporte/productos.docx",        new ReporteProductosHandler());
            server.createContext("/api/reporte/producto-marca.docx",   new ReporteMarcaHandler());
            server.createContext("/api/reporte/grafo-cliente.docx",    new ReporteGrafoHandler());

            // Frontend estático
            server.createContext("/",                                  new StaticFileHandler());

            server.setExecutor(null);
            server.start();

            System.out.println("\n====================================================");
            System.out.println(" API REST NATIVA ESCUCHANDO EN: http://localhost:8085");
            System.out.println(" Frontend  : http://localhost:8085/");
            System.out.println(" Hash REST : http://localhost:8085/api/hash/catalogo");
            System.out.println("====================================================");
        } catch (IOException e) {
            System.err.println("Error al iniciar el servidor HTTP: " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Utilidades compartidas por todos los handlers                     */
    /* ------------------------------------------------------------------ */

    /** Aplica CORS y gestiona pre-flight OPTIONS. Retorna true si el handler debe parar. */
    private static boolean aplicarCors(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    /** Extrae un parámetro entero de la query string. */
    private static int param(String qs, String nombre, int defecto) {
        if (qs == null) return defecto;
        for (String p : qs.split("&")) {
            if (p.startsWith(nombre + "=")) {
                try { return Integer.parseInt(p.substring(nombre.length() + 1)); }
                catch (NumberFormatException ignored) { return defecto; }
            }
        }
        return defecto;
    }

    /** Serializa listas de nodos y aristas al JSON que consume Vis.js. */
    private static String grafoJson(List<Nodo> nodos, List<Arista> aristas) {
        StringBuilder sb = new StringBuilder("{\n  \"nodes\": [\n");
        for (int i = 0; i < nodos.size(); i++) {
            Nodo n = nodos.get(i);
            String label = n.getLabel() == null ? "" : n.getLabel().replace("\"", "\\\"");
            sb.append(String.format(
                    "    {\"id\": \"%s\", \"label\": \"%s\", \"group\": \"%s\"}",
                    n.getId(), label, n.getTipo()));
            if (i < nodos.size() - 1) sb.append(",\n");
        }
        sb.append("\n  ],\n  \"edges\": [\n");
        for (int i = 0; i < aristas.size(); i++) {
            Arista a = aristas.get(i);
            sb.append(String.format(
                    "    {\"from\": \"%s\", \"to\": \"%s\", \"label\": \"%s\"}",
                    a.getOrigenId(), a.getDestinoId(), a.getRelacion()));
            if (i < aristas.size() - 1) sb.append(",\n");
        }
        sb.append("\n  ]\n}");
        return sb.toString();
    }

    private static void enviarJson(HttpExchange ex, String json, int status) throws IOException {
        byte[] body = json.getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private static void enviarJson(HttpExchange ex, String json) throws IOException {
        enviarJson(ex, json, 200);
    }

    /** Responde un archivo binario como descarga (Content-Disposition: attachment). */
    private static void enviarArchivo(HttpExchange ex, byte[] body, String filename) throws IOException {
        ex.getResponseHeaders().set("Content-Type",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    /** Lee y parsea un cuerpo application/x-www-form-urlencoded en un mapa. */
    private static Map<String, String> leerForm(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> m = new HashMap<>();
        if (body.isBlank()) return m;
        for (String par : body.split("&")) {
            int i = par.indexOf('=');
            if (i > 0) {
                String k = URLDecoder.decode(par.substring(0, i), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8);
                m.put(k, v);
            }
        }
        return m;
    }

    /** Escapa comillas dobles y backslash para incrustar texto en JSON. */
    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Construye el JSON común de una búsqueda por ID en una tabla hash:
     * slot consultado, probes recorridos y tiempo de la operación {@code get()}.
     *
     * @param id          clave buscada
     * @param r           resultado de {@code tabla.get(id)} (slot/probes/tiempo)
     * @param extraCampos campos específicos del valor ya formateados como JSON
     *                    (p. ej. {@code "nombre":"...","precio":1.50}), o "" si no se encontró
     */
    private static String buscarJson(int id, CustomHashTable.SearchResult<?> r, String extraCampos) {
        boolean found = r.value() != null;
        return "{\"buscar\":true,\"id\":" + id
            + ",\"found\":" + found
            + ",\"slot\":" + r.tablePosition()
            + ",\"probes\":" + r.probes()
            + ",\"durationNs\":" + r.durationNanoseconds()
            + ",\"tiempo\":\"" + esc(Durations.human(r.durationNanoseconds())) + "\""
            + (found && !extraCampos.isEmpty() ? "," + extraCampos : "")
            + "}";
    }

    /* ------------------------------------------------------------------ */
    /*  Handlers de grafos                                                 */
    /* ------------------------------------------------------------------ */

    private static class TrazabilidadHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            int id = param(ex.getRequestURI().getQuery(), "factura", 1);
            CommercialGraph g = new CommercialGraph();
            g.construirGrafoTrazabilidad(id);
            enviarJson(ex, grafoJson(g.getNodos(), g.getAristas()));
        }
    }

    private static class ClienteProductosHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            int id = param(ex.getRequestURI().getQuery(), "cliente", 1);
            CommercialGraph g = new CommercialGraph();
            g.construirGrafoClienteProductos(id);
            enviarJson(ex, grafoJson(g.getNodos(), g.getAristas()));
        }
    }

    private static class ProveedorImpactoHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            int id = param(ex.getRequestURI().getQuery(), "proveedor", 5);
            CommercialGraph g = new CommercialGraph();
            g.construirGrafoProveedorImpacto(id);
            enviarJson(ex, grafoJson(g.getNodos(), g.getAristas()));
        }
    }

    private static class ClienteProductosAnioHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            String qs = ex.getRequestURI().getQuery();
            int cliente    = param(qs, "cliente",    1);
            int anioInicio = param(qs, "anioInicio", 2020);
            int anioFin    = param(qs, "anioFin",    Year.now().getValue());
            CommercialGraph g = new CommercialGraph();
            g.construirGrafoClienteProductosPorAnio(cliente, anioInicio, anioFin);
            enviarJson(ex, grafoJson(g.getNodos(), g.getAristas()));
        }
    }

    private static class TrazabilidadInversaHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            int id = param(ex.getRequestURI().getQuery(), "item", 50);
            CommercialGraph g = new CommercialGraph();
            g.construirGrafoTrazabilidadInversa(id);
            enviarJson(ex, grafoJson(g.getNodos(), g.getAristas()));
        }
    }

    private static class TipoClientesGrafoHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            int id = param(ex.getRequestURI().getQuery(), "tipo", 2);
            CommercialGraph g = new CommercialGraph();
            g.construirGrafoTipoClientes(id);
            enviarJson(ex, grafoJson(g.getNodos(), g.getAristas()));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Handlers de tabla hash                                             */
    /* ------------------------------------------------------------------ */

    private static class HashCatalogoHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) { agregarItem(ex); return; }
            if (catalogoRef == null) {
                enviarJson(ex, "{\"error\":\"Tabla no inicializada\"}", 503);
                return;
            }
            String qs = ex.getRequestURI().getQuery();
            if (qs != null && qs.contains("buscar=")) {
                int id = param(qs, "buscar", Integer.MIN_VALUE);
                var r = catalogoRef.get(id);
                ItemFloral v = r.value();
                String extra = v == null ? "" : String.format(Locale.ROOT,
                        "\"nombre\":\"%s\",\"precio\":%.2f,\"idProveedor\":%d",
                        esc(v.nombreFlor()), v.precio(), v.idProveedor());
                enviarJson(ex, buscarJson(id, r, extra));
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"tabla\": \"Catalogo de Items Florales\",\n");
            sb.append("  \"capacity\": ").append(catalogoRef.getCapacity()).append(",\n");
            sb.append("  \"size\": ").append(catalogoRef.getSize()).append(",\n");
            sb.append("  \"collisionCount\": ").append(catalogoRef.getCollisionCount()).append(",\n");
            double lf = (double) catalogoRef.getSize() / catalogoRef.getCapacity();
            sb.append(String.format(Locale.ROOT, "  \"loadFactor\": \"%.4f\",\n", lf));
            sb.append("  \"entries\": [\n");
            var entries = catalogoRef.entries();
            for (int i = 0; i < entries.size(); i++) {
                var e = entries.get(i);
                ItemFloral item = (ItemFloral) e.value();
                String nombre = item.nombreFlor() == null ? ""
                        : item.nombreFlor().replace("\"", "\\\"");
                sb.append(String.format(Locale.ROOT,
                        "    {\"slot\": %d, \"id\": %d, \"nombre\": \"%s\", \"precio\": %.2f, \"idProveedor\": %d}",
                        e.slot(), item.id(), nombre, item.precio(), item.idProveedor()));
                if (i < entries.size() - 1) sb.append(",\n");
            }
            sb.append("\n  ]\n}");
            enviarJson(ex, sb.toString());
        }
    }

    private static class HashMarcasHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) { agregarMarca(ex); return; }
            if (marcasRef == null) {
                enviarJson(ex, "{\"error\":\"Tabla no inicializada\"}", 503);
                return;
            }
            String qs = ex.getRequestURI().getQuery();
            if (qs != null && qs.contains("buscar=")) {
                int id = param(qs, "buscar", Integer.MIN_VALUE);
                var r = marcasRef.get(id);
                ProveedorOrigen v = r.value();
                String extra = v == null ? "" :
                        "\"nombreFinca\":\"" + esc(v.nombreFinca())
                      + "\",\"pais\":\"" + esc(v.pais()) + "\"";
                enviarJson(ex, buscarJson(id, r, extra));
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"tabla\": \"Proveedores / Marcas\",\n");
            sb.append("  \"capacity\": ").append(marcasRef.getCapacity()).append(",\n");
            sb.append("  \"size\": ").append(marcasRef.getSize()).append(",\n");
            sb.append("  \"collisionCount\": ").append(marcasRef.getCollisionCount()).append(",\n");
            double lf = (double) marcasRef.getSize() / marcasRef.getCapacity();
            sb.append(String.format(Locale.ROOT, "  \"loadFactor\": \"%.4f\",\n", lf));
            sb.append("  \"entries\": [\n");
            var entries = marcasRef.entries();
            for (int i = 0; i < entries.size(); i++) {
                var e = entries.get(i);
                ProveedorOrigen p = (ProveedorOrigen) e.value();
                String finca = p.nombreFinca() == null ? "" : p.nombreFinca().replace("\"", "\\\"");
                String pais  = p.pais()        == null ? "" : p.pais().replace("\"", "\\\"");
                sb.append(String.format(
                        "    {\"slot\": %d, \"id\": %d, \"nombreFinca\": \"%s\", \"pais\": \"%s\"}",
                        e.slot(), p.id(), finca, pais));
                if (i < entries.size() - 1) sb.append(",\n");
            }
            sb.append("\n  ]\n}");
            enviarJson(ex, sb.toString());
        }
    }

    private static class HashTiposClienteHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) { agregarTipoCliente(ex); return; }
            if (tiposRef == null) {
                enviarJson(ex, "{\"error\":\"Tabla no inicializada\"}", 503);
                return;
            }
            String qs = ex.getRequestURI().getQuery();
            if (qs != null && qs.contains("buscar=")) {
                int id = param(qs, "buscar", Integer.MIN_VALUE);
                var r = tiposRef.get(id);
                TipoCliente v = r.value();
                String extra = v == null ? "" : String.format(Locale.ROOT,
                        "\"nombre\":\"%s\",\"descuento\":%.2f", esc(v.nombre()), v.descuento());
                enviarJson(ex, buscarJson(id, r, extra));
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"tabla\": \"Tipos de Cliente\",\n");
            sb.append("  \"capacity\": ").append(tiposRef.getCapacity()).append(",\n");
            sb.append("  \"size\": ").append(tiposRef.getSize()).append(",\n");
            sb.append("  \"collisionCount\": ").append(tiposRef.getCollisionCount()).append(",\n");
            double lf = (double) tiposRef.getSize() / tiposRef.getCapacity();
            sb.append(String.format(Locale.ROOT, "  \"loadFactor\": \"%.4f\",\n", lf));
            sb.append("  \"entries\": [\n");
            var entries = tiposRef.entries();
            for (int i = 0; i < entries.size(); i++) {
                var e = entries.get(i);
                TipoCliente tc = (TipoCliente) e.value();
                String nombre = tc.nombre() == null ? "" : tc.nombre().replace("\"", "\\\"");
                sb.append(String.format(Locale.ROOT,
                        "    {\"slot\": %d, \"id\": %d, \"nombre\": \"%s\", \"descuento\": %.2f}",
                        e.slot(), tc.id(), nombre, tc.descuento()));
                if (i < entries.size() - 1) sb.append(",\n");
            }
            sb.append("\n  ]\n}");
            enviarJson(ex, sb.toString());
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Altas en la tabla hash (POST) con detección de colisión            */
    /* ------------------------------------------------------------------ */

    /** POST /api/hash/catalogo — agrega un ItemFloral y reporta la colisión. */
    private static void agregarItem(HttpExchange ex) throws IOException {
        if (catalogoRef == null) { enviarJson(ex, "{\"ok\":false,\"error\":\"Tabla no inicializada\"}", 503); return; }
        Map<String, String> f = leerForm(ex);
        int    id;
        double precio;
        int    idProveedor;
        String nombre = f.getOrDefault("nombre", "").trim();
        try {
            id          = Integer.parseInt(f.getOrDefault("id", "").trim());
            precio      = Double.parseDouble(f.getOrDefault("precio", "0").trim());
            idProveedor = Integer.parseInt(f.getOrDefault("idProveedor", "").trim());
        } catch (NumberFormatException nfe) {
            enviarJson(ex, "{\"ok\":false,\"error\":\"id, precio e idProveedor deben ser numéricos\"}", 400);
            return;
        }
        if (nombre.isEmpty()) { enviarJson(ex, "{\"ok\":false,\"error\":\"el nombre es obligatorio\"}", 400); return; }

        // 1) Persistir en Oracle primero (si está disponible) para no divergir memoria/BD
        boolean persistido = false;
        ComercioDao dao = new ComercioDao();
        if (dao.disponible()) {
            try {
                dao.insertarItem(id, nombre, precio, idProveedor);
                persistido = true;
            } catch (SQLException e) {
                enviarJson(ex, "{\"ok\":false,\"error\":\"Oracle: " + esc(e.getMessage()) + "\"}", 409);
                return;
            }
        }

        // 2) Insertar en la tabla hash en memoria, midiendo la colisión
        String resultado;
        synchronized (catalogoRef) {
            int capAntes      = catalogoRef.getCapacity();
            int colAntes      = catalogoRef.getCollisionCount();
            boolean esUpdate  = catalogoRef.containsKey(id);

            catalogoRef.put(id, new ItemFloral(id, nombre, precio, idProveedor));

            var r       = catalogoRef.get(id);
            int slot    = r.tablePosition();
            int probes  = r.probes();
            int chain   = catalogoRef.chainLengthAt(slot);
            boolean rehash   = catalogoRef.getCapacity() != capAntes;
            boolean colision = !esUpdate && chain > 1;

            StringBuilder claves = new StringBuilder("[");
            var ks = catalogoRef.keysAt(slot);
            for (int i = 0; i < ks.size(); i++) { if (i > 0) claves.append(","); claves.append(ks.get(i)); }
            claves.append("]");

            resultado = "{"
                + "\"ok\":true,"
                + "\"id\":" + id + ","
                + "\"slot\":" + slot + ","
                + "\"esActualizacion\":" + esUpdate + ","
                + "\"colision\":" + colision + ","
                + "\"chainLength\":" + chain + ","
                + "\"clavesEnSlot\":" + claves + ","
                + "\"probes\":" + probes + ","
                + "\"size\":" + catalogoRef.getSize() + ","
                + "\"capacity\":" + catalogoRef.getCapacity() + ","
                + "\"collisionCount\":" + catalogoRef.getCollisionCount() + ","
                + "\"collisionDelta\":" + (catalogoRef.getCollisionCount() - colAntes) + ","
                + "\"rehash\":" + rehash + ","
                + "\"persistidoEnOracle\":" + persistido
                + "}";
        }
        enviarJson(ex, resultado);
    }

    /** POST /api/hash/marcas — agrega un ProveedorOrigen y reporta la colisión. */
    private static void agregarMarca(HttpExchange ex) throws IOException {
        if (marcasRef == null) { enviarJson(ex, "{\"ok\":false,\"error\":\"Tabla no inicializada\"}", 503); return; }
        Map<String, String> f = leerForm(ex);
        int id;
        String finca = f.getOrDefault("nombreFinca", "").trim();
        String pais  = f.getOrDefault("pais", "").trim();
        try {
            id = Integer.parseInt(f.getOrDefault("id", "").trim());
        } catch (NumberFormatException nfe) {
            enviarJson(ex, "{\"ok\":false,\"error\":\"el id debe ser numérico\"}", 400);
            return;
        }
        if (finca.isEmpty() || pais.isEmpty()) {
            enviarJson(ex, "{\"ok\":false,\"error\":\"nombreFinca y pais son obligatorios\"}", 400);
            return;
        }

        boolean persistido = false;
        ComercioDao dao = new ComercioDao();
        if (dao.disponible()) {
            try { dao.insertarMarca(id, finca, pais); persistido = true; }
            catch (SQLException e) {
                enviarJson(ex, "{\"ok\":false,\"error\":\"Oracle: " + esc(e.getMessage()) + "\"}", 409);
                return;
            }
        }

        String resultado;
        synchronized (marcasRef) {
            int capAntes     = marcasRef.getCapacity();
            int colAntes     = marcasRef.getCollisionCount();
            boolean esUpdate = marcasRef.containsKey(id);

            marcasRef.put(id, new ProveedorOrigen(id, finca, pais));

            var r      = marcasRef.get(id);
            int slot   = r.tablePosition();
            int chain  = marcasRef.chainLengthAt(slot);
            boolean rehash   = marcasRef.getCapacity() != capAntes;
            boolean colision = !esUpdate && chain > 1;

            StringBuilder claves = new StringBuilder("[");
            var ks = marcasRef.keysAt(slot);
            for (int i = 0; i < ks.size(); i++) { if (i > 0) claves.append(","); claves.append(ks.get(i)); }
            claves.append("]");

            resultado = "{"
                + "\"ok\":true,"
                + "\"id\":" + id + ","
                + "\"slot\":" + slot + ","
                + "\"esActualizacion\":" + esUpdate + ","
                + "\"colision\":" + colision + ","
                + "\"chainLength\":" + chain + ","
                + "\"clavesEnSlot\":" + claves + ","
                + "\"probes\":" + r.probes() + ","
                + "\"size\":" + marcasRef.getSize() + ","
                + "\"capacity\":" + marcasRef.getCapacity() + ","
                + "\"collisionCount\":" + marcasRef.getCollisionCount() + ","
                + "\"collisionDelta\":" + (marcasRef.getCollisionCount() - colAntes) + ","
                + "\"rehash\":" + rehash + ","
                + "\"persistidoEnOracle\":" + persistido
                + "}";
        }
        enviarJson(ex, resultado);
    }

    /** POST /api/hash/tipos-cliente — agrega un TipoCliente y reporta la colisión. */
    private static void agregarTipoCliente(HttpExchange ex) throws IOException {
        if (tiposRef == null) { enviarJson(ex, "{\"ok\":false,\"error\":\"Tabla no inicializada\"}", 503); return; }
        Map<String, String> f = leerForm(ex);
        int    id;
        double descuento;
        String nombre = f.getOrDefault("nombre", "").trim();
        try {
            id        = Integer.parseInt(f.getOrDefault("id", "").trim());
            descuento = Double.parseDouble(f.getOrDefault("descuento", "0").trim());
        } catch (NumberFormatException nfe) {
            enviarJson(ex, "{\"ok\":false,\"error\":\"id y descuento deben ser numéricos\"}", 400);
            return;
        }
        if (nombre.isEmpty()) { enviarJson(ex, "{\"ok\":false,\"error\":\"el nombre es obligatorio\"}", 400); return; }

        boolean persistido = false;
        ComercioDao dao = new ComercioDao();
        if (dao.disponible()) {
            try { dao.insertarTipoCliente(id, nombre, descuento); persistido = true; }
            catch (SQLException e) {
                enviarJson(ex, "{\"ok\":false,\"error\":\"Oracle: " + esc(e.getMessage()) + "\"}", 409);
                return;
            }
        }

        String resultado;
        synchronized (tiposRef) {
            int capAntes     = tiposRef.getCapacity();
            int colAntes     = tiposRef.getCollisionCount();
            boolean esUpdate = tiposRef.containsKey(id);

            tiposRef.put(id, new TipoCliente(id, nombre, descuento));

            var r      = tiposRef.get(id);
            int slot   = r.tablePosition();
            int chain  = tiposRef.chainLengthAt(slot);
            boolean rehash   = tiposRef.getCapacity() != capAntes;
            boolean colision = !esUpdate && chain > 1;

            StringBuilder claves = new StringBuilder("[");
            var ks = tiposRef.keysAt(slot);
            for (int i = 0; i < ks.size(); i++) { if (i > 0) claves.append(","); claves.append(ks.get(i)); }
            claves.append("]");

            resultado = "{"
                + "\"ok\":true,"
                + "\"id\":" + id + ","
                + "\"slot\":" + slot + ","
                + "\"esActualizacion\":" + esUpdate + ","
                + "\"colision\":" + colision + ","
                + "\"chainLength\":" + chain + ","
                + "\"clavesEnSlot\":" + claves + ","
                + "\"probes\":" + r.probes() + ","
                + "\"size\":" + tiposRef.getSize() + ","
                + "\"capacity\":" + tiposRef.getCapacity() + ","
                + "\"collisionCount\":" + tiposRef.getCollisionCount() + ","
                + "\"collisionDelta\":" + (tiposRef.getCollisionCount() - colAntes) + ","
                + "\"rehash\":" + rehash + ","
                + "\"persistidoEnOracle\":" + persistido
                + "}";
        }
        enviarJson(ex, resultado);
    }

    /* ------------------------------------------------------------------ */
    /*  Alta de factura (POST) y listado de clientes                       */
    /* ------------------------------------------------------------------ */

    /** POST /api/grafo/factura — crea una factura con sus detalles en Oracle. */
    private static class FacturaHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                enviarJson(ex, "{\"ok\":false,\"error\":\"Use POST\"}", 405);
                return;
            }
            ComercioDao dao = new ComercioDao();
            if (!dao.disponible()) {
                enviarJson(ex, "{\"ok\":false,\"error\":\"Oracle no configurado: los grafos requieren la base de datos\"}", 503);
                return;
            }
            Map<String, String> f = leerForm(ex);
            int cliente;
            try {
                cliente = Integer.parseInt(f.getOrDefault("cliente", "").trim());
            } catch (NumberFormatException nfe) {
                enviarJson(ex, "{\"ok\":false,\"error\":\"cliente inválido\"}", 400);
                return;
            }
            // items = "50:3,51:2"
            List<int[]> lineas = new ArrayList<>();
            String itemsRaw = f.getOrDefault("items", "").trim();
            if (!itemsRaw.isEmpty()) {
                for (String par : itemsRaw.split(",")) {
                    String[] kv = par.split(":");
                    if (kv.length == 2) {
                        try {
                            int idItem = Integer.parseInt(kv[0].trim());
                            int cant   = Integer.parseInt(kv[1].trim());
                            if (cant > 0) lineas.add(new int[]{ idItem, cant });
                        } catch (NumberFormatException ignored) { /* salta línea inválida */ }
                    }
                }
            }
            if (lineas.isEmpty()) {
                enviarJson(ex, "{\"ok\":false,\"error\":\"Agregue al menos una línea (ítem y cantidad)\"}", 400);
                return;
            }
            try {
                int idFactura = dao.crearFactura(cliente, lineas);
                enviarJson(ex, "{\"ok\":true,\"idFactura\":" + idFactura + "}");
            } catch (SQLException e) {
                enviarJson(ex, "{\"ok\":false,\"error\":\"Oracle: " + esc(e.getMessage()) + "\"}", 409);
            }
        }
    }

    /** GET /api/clientes — lista de clientes para poblar el formulario. */
    private static class ClientesHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            ComercioDao dao = new ComercioDao();
            if (!dao.disponible()) { enviarJson(ex, "[]"); return; }
            try {
                List<ComercioDao.ClienteRef> cs = dao.listarClientes();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < cs.size(); i++) {
                    var c = cs.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{\"id\":").append(c.id())
                      .append(",\"nombre\":\"").append(esc(c.nombre())).append("\"}");
                }
                sb.append("]");
                enviarJson(ex, sb.toString());
            } catch (SQLException e) {
                enviarJson(ex, "{\"error\":\"" + esc(e.getMessage()) + "\"}", 500);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Reportes Microsoft Word (.docx)                                    */
    /* ------------------------------------------------------------------ */

    /** GET /api/reporte/productos.docx — Reporte 4.1 desde la tabla hash actual. */
    private static class ReporteProductosHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            if (catalogoRef == null) { enviarJson(ex, "{\"error\":\"Tabla no inicializada\"}", 503); return; }
            var filas = new ReportService().reporteProductos(catalogoRef);
            byte[] doc = new WordReportExporter().productos(filas);
            enviarArchivo(ex, doc, "4.1_productos.docx");
        }
    }

    /** GET /api/reporte/producto-marca.docx — Reporte 4.2. */
    private static class ReporteMarcaHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            if (catalogoRef == null || marcasRef == null) {
                enviarJson(ex, "{\"error\":\"Tablas no inicializadas\"}", 503); return;
            }
            var filas = new ReportService().reporteProductoMarca(catalogoRef, marcasRef);
            byte[] doc = new WordReportExporter().productoMarca(filas);
            enviarArchivo(ex, doc, "4.2_producto_marca.docx");
        }
    }

    /** GET /api/reporte/grafo-cliente.docx?cliente=ID — Reporte 4.3 (requiere Oracle). */
    private static class ReporteGrafoHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            ComercioDao dao = new ComercioDao();
            if (!dao.disponible()) {
                enviarJson(ex, "{\"error\":\"Oracle requerido para el reporte 4.3\"}", 503);
                return;
            }
            int idCliente = param(ex.getRequestURI().getQuery(), "cliente", 101);
            try {
                ReporteGrafoCliente data = dao.recorridoCliente(idCliente);
                byte[] doc = new WordReportExporter().grafoCliente(data);
                enviarArchivo(ex, doc, "4.3_grafo_cliente_" + idCliente + ".docx");
            } catch (java.sql.SQLException e) {
                enviarJson(ex, "{\"error\":\"Oracle: " + esc(e.getMessage()) + "\"}", 409);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Handler estático — sirve web/index.html                           */
    /* ------------------------------------------------------------------ */

    private static class StaticFileHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            Path archivo = Paths.get("web", "index.html");
            if (!Files.exists(archivo)) {
                byte[] cuerpo = "web/index.html no encontrado.".getBytes("UTF-8");
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                ex.sendResponseHeaders(404, cuerpo.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(cuerpo); }
                return;
            }
            byte[] body = Files.readAllBytes(archivo);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        }
    }
}
