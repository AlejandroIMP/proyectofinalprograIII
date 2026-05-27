package umg.edu.gt.floristeria.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import umg.edu.gt.floristeria.graph.CommercialGraph;
import umg.edu.gt.floristeria.graph.Nodo;
import umg.edu.gt.floristeria.graph.Arista;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Servidor HTTP nativo (com.sun.net.httpserver) que expone la API REST
 * de grafos del proyecto y sirve el frontend HTML5/Vis.js.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>GET /api/grafo/trazabilidad?factura={id}     — trazabilidad de una factura</li>
 *   <li>GET /api/grafo/cliente-productos?cliente={id} — todos los productos de un cliente</li>
 *   <li>GET /api/grafo/proveedor-impacto?proveedor={id} — facturas que contienen productos del proveedor</li>
 *   <li>GET /                                         — sirve web/index.html</li>
 * </ul>
 *
 * Todos los endpoints JSON incluyen headers CORS para que el navegador
 * pueda hacer fetch desde cualquier origen.
 */
public class GraphRestApi {

    public static void iniciarServidor() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8085), 0);

            server.createContext("/api/grafo/trazabilidad",     new TrazabilidadHandler());
            server.createContext("/api/grafo/cliente-productos", new ClienteProductosHandler());
            server.createContext("/api/grafo/proveedor-impacto", new ProveedorImpactoHandler());
            server.createContext("/",                            new StaticFileHandler());

            server.setExecutor(null); // ejecutor por defecto (hilo por petición)
            server.start();
            System.out.println("\n====================================================");
            System.out.println(" API REST NATIVA ESCUCHANDO EN: http://localhost:8085");
            System.out.println(" Frontend:  http://localhost:8085/");
            System.out.println("====================================================");
        } catch (IOException e) {
            System.err.println("Error al iniciar el servidor HTTP: " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Utilidades compartidas                                             */
    /* ------------------------------------------------------------------ */

    /** Aplica headers CORS y maneja pre-flight OPTIONS. Retorna true si el
     *  handler debe detenerse (fue una petición OPTIONS). */
    private static boolean aplicarCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin",  "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    /** Extrae el valor de un parámetro de la query string. Retorna {@code defaultValue}
     *  si el parámetro no está presente o no es un entero válido. */
    private static int extraerParam(String queryString, String nombre, int defaultValue) {
        if (queryString == null) return defaultValue;
        for (String parte : queryString.split("&")) {
            if (parte.startsWith(nombre + "=")) {
                try { return Integer.parseInt(parte.substring(nombre.length() + 1)); }
                catch (NumberFormatException ignored) { return defaultValue; }
            }
        }
        return defaultValue;
    }

    /** Serializa listas de nodos y aristas al formato JSON que consume Vis.js. */
    private static String construirJson(List<Nodo> nodos, List<Arista> aristas) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"nodes\": [\n");
        for (int i = 0; i < nodos.size(); i++) {
            Nodo n = nodos.get(i);
            // Escapado básico de comillas en label
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

    private static void enviarJson(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Handler 1 — Trazabilidad de factura                               */
    /* ------------------------------------------------------------------ */

    private static class TrazabilidadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (aplicarCors(exchange)) return;
            int id = extraerParam(exchange.getRequestURI().getQuery(), "factura", 1);
            CommercialGraph grafo = new CommercialGraph();
            grafo.construirGrafoTrazabilidad(id);
            enviarJson(exchange, construirJson(grafo.getNodos(), grafo.getAristas()));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Handler 2 — Productos de un cliente                               */
    /* ------------------------------------------------------------------ */

    private static class ClienteProductosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (aplicarCors(exchange)) return;
            int id = extraerParam(exchange.getRequestURI().getQuery(), "cliente", 1);
            CommercialGraph grafo = new CommercialGraph();
            grafo.construirGrafoClienteProductos(id);
            enviarJson(exchange, construirJson(grafo.getNodos(), grafo.getAristas()));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Handler 3 — Impacto de un proveedor                               */
    /* ------------------------------------------------------------------ */

    private static class ProveedorImpactoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (aplicarCors(exchange)) return;
            int id = extraerParam(exchange.getRequestURI().getQuery(), "proveedor", 5);
            CommercialGraph grafo = new CommercialGraph();
            grafo.construirGrafoProveedorImpacto(id);
            enviarJson(exchange, construirJson(grafo.getNodos(), grafo.getAristas()));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Handler 4 — Archivos estáticos (sirve web/index.html)             */
    /* ------------------------------------------------------------------ */

    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Solo GET al raíz redirige al visualizador
            Path archivo = Paths.get("web", "index.html");
            if (!Files.exists(archivo)) {
                byte[] cuerpo = "web/index.html no encontrado. Crea el directorio web/ con index.html."
                        .getBytes("UTF-8");
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(404, cuerpo.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(cuerpo); }
                return;
            }
            byte[] body = Files.readAllBytes(archivo);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        }
    }
}
