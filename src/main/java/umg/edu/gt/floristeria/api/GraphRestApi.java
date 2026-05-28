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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Year;
import java.util.List;
import java.util.Locale;

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

    /**
     * Lanza el servidor HTTP en el puerto 8085 y registra todos los endpoints.
     *
     * @param catalogo tabla hash del catálogo de ítems (puede ser null si no hay Oracle)
     * @param marcas   tabla hash de proveedores/marcas (puede ser null)
     */
    public static void iniciarServidor(
            CustomHashTable<Integer, ItemFloral>      catalogo,
            CustomHashTable<Integer, ProveedorOrigen> marcas) {
        catalogoRef = catalogo;
        marcasRef   = marcas;

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8085), 0);

            // Grafos
            server.createContext("/api/grafo/trazabilidad",            new TrazabilidadHandler());
            server.createContext("/api/grafo/cliente-productos-anio",  new ClienteProductosAnioHandler());
            server.createContext("/api/grafo/cliente-productos",       new ClienteProductosHandler());
            server.createContext("/api/grafo/proveedor-impacto",       new ProveedorImpactoHandler());
            server.createContext("/api/grafo/trazabilidad-inversa",    new TrazabilidadInversaHandler());

            // Tabla hash
            server.createContext("/api/hash/catalogo",                 new HashCatalogoHandler());
            server.createContext("/api/hash/marcas",                   new HashMarcasHandler());

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
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
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

    /* ------------------------------------------------------------------ */
    /*  Handlers de tabla hash                                             */
    /* ------------------------------------------------------------------ */

    private static class HashCatalogoHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (aplicarCors(ex)) return;
            if (catalogoRef == null) {
                enviarJson(ex, "{\"error\":\"Tabla no inicializada\"}", 503);
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
            if (marcasRef == null) {
                enviarJson(ex, "{\"error\":\"Tabla no inicializada\"}", 503);
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
