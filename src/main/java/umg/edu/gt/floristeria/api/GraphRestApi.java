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
import java.util.List;

public class GraphRestApi {

    public static void iniciarServidor() {
        try {
            // Levanta el servidor en el puerto 8080
            HttpServer server = HttpServer.create(new InetSocketAddress(8085), 0);

            // Registra la ruta que va a buscar el archivo index.html del Paso 5
            server.createContext("/api/grafo/trazabilidad", new TrazabilidadHandler());

            server.setExecutor(null); // Ejecutor por defecto
            server.start();
            System.out.println("\n====================================================");
            System.out.println(" API REST NATIVA ESCUCHANDO EN: http://localhost:8085");
            System.out.println("====================================================");
        } catch (IOException e) {
            System.err.println("Error al iniciar el servidor HTTP: " + e.getMessage());
        }
    }

    private static class TrazabilidadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Habilitar CORS para que el navegador no bloquee la petición desde el HTML
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Por defecto buscaremos la factura 1, o puedes extraer el parámetro de la URL
            int idFactura = 1;
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("factura=")) {
                try {
                    idFactura = Integer.parseInt(query.split("factura=")[1].split("&")[0]);
                } catch (Exception e) {
                    idFactura = 1;
                }
            }

            // Procesar el grafo con la consulta a Oracle
            CommercialGraph grafo = new CommercialGraph();
            grafo.construirGrafoTrazabilidad(idFactura);

            // Construir la respuesta en formato JSON de forma manual sin librerías externas
            String jsonResponse = construirJson(grafo.getNodos(), grafo.getAristas());

            byte[] responseBytes = jsonResponse.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        // Conversor manual a JSON estructurado para el front-end
        private String construirJson(List<Nodo> nodos, List<Arista> aristas) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"nodes\": [\n");
            for (int i = 0; i < nodos.size(); i++) {
                Nodo n = nodos.get(i);
                sb.append(String.format("    {\"id\": \"%s\", \"label\": \"%s\", \"group\": \"%s\"}",
                        n.getId(), n.getLabel(), n.getTipo()));
                if (i < nodos.size() - 1) sb.append(",\n");
            }
            sb.append("\n  ],\n  \"edges\": [\n");
            for (int i = 0; i < aristas.size(); i++) {
                Arista a = aristas.get(i);
                sb.append(String.format("    {\"from\": \"%s\", \"to\": \"%s\", \"label\": \"%s\"}",
                        a.getOrigenId(), a.getDestinoId(), a.getRelacion()));
                if (i < aristas.size() - 1) sb.append(",\n");
            }
            sb.append("\n  ]\n}");
            return sb.toString();
        }
    }
}
