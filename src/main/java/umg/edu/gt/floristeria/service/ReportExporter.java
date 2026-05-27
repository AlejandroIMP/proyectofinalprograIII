package umg.edu.gt.floristeria.service;

import umg.edu.gt.floristeria.service.ReportService.ProductoMarcaRow;
import umg.edu.gt.floristeria.service.ReportService.ProductoRow;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exportador de los reportes 4.1 y 4.2 a archivos planos en formatos
 * CSV y JSON, sin dependencias externas.
 * <p>
 * Decisiones de formato:
 * <ul>
 *   <li><b>CSV</b>: codificación UTF-8 con BOM, separador coma, encabezado
 *       en la primera fila. Los campos de texto que contengan coma, comilla
 *       o salto de línea van entre comillas dobles, con las comillas
 *       internas escapadas duplicándolas (RFC 4180).</li>
 *   <li><b>JSON</b>: objeto raíz con metadatos (reporte, generadoEn,
 *       totalFilas, tiempoPromedioNs, tiempoMaximoNs) y un arreglo
 *       {@code filas} con los registros. Cadena UTF-8. Escapes de
 *       {@code \"} y {@code \\}.</li>
 * </ul>
 * Los tiempos siempre se escriben en <b>nanosegundos</b> en los archivos
 * (campo {@code durationNs}) para evitar pérdida de precisión; la
 * presentación humana (µs/ms/s) es responsabilidad de la capa de UI.
 */
public final class ReportExporter {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* =====================================================================
     *  CSV
     * ===================================================================== */

    public void exportProductosCsv(List<ProductoRow> filas, Path destino) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write('﻿'); // BOM UTF-8 para que Excel reconozca acentos
            w.write("id,nombre,precio,claveHash,slot,probes,durationNs");
            w.newLine();
            for (ProductoRow r : filas) {
                w.write(String.valueOf(r.idProducto()));        w.write(',');
                w.write(csv(r.nombreProducto()));               w.write(',');
                w.write(String.format("%.2f", r.precio()));     w.write(',');
                w.write(String.valueOf(r.claveHash()));         w.write(',');
                w.write(String.valueOf(r.slot()));              w.write(',');
                w.write(String.valueOf(r.probes()));            w.write(',');
                w.write(String.valueOf(r.durationNs()));
                w.newLine();
            }
        }
    }

    public void exportProductoMarcaCsv(List<ProductoMarcaRow> filas, Path destino) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write('﻿');
            w.write("idProducto,nombreProducto,idMarca,nombreMarca,paisMarca,slotMarca,probesMarca,marcaDurationNs");
            w.newLine();
            for (ProductoMarcaRow r : filas) {
                w.write(String.valueOf(r.idProducto()));       w.write(',');
                w.write(csv(r.nombreProducto()));              w.write(',');
                w.write(String.valueOf(r.idMarca()));          w.write(',');
                w.write(csv(r.nombreMarca()));                 w.write(',');
                w.write(csv(r.paisMarca()));                   w.write(',');
                w.write(String.valueOf(r.slotMarca()));        w.write(',');
                w.write(String.valueOf(r.probesMarca()));      w.write(',');
                w.write(String.valueOf(r.marcaDurationNs()));
                w.newLine();
            }
        }
    }

    /* =====================================================================
     *  JSON
     * ===================================================================== */

    public void exportProductosJson(List<ProductoRow> filas, Path destino) throws IOException {
        long total = filas.stream().mapToLong(ProductoRow::durationNs).sum();
        long max   = filas.stream().mapToLong(ProductoRow::durationNs).max().orElse(0);
        long prom  = filas.isEmpty() ? 0 : total / filas.size();

        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write('{');
            w.newLine();
            w.write(meta("4.1 Productos registrados", filas.size(), prom, max));
            w.write("  \"filas\": [");
            w.newLine();
            boolean first = true;
            for (ProductoRow r : filas) {
                if (!first) { w.write(','); w.newLine(); }
                first = false;
                w.write("    {");
                w.write("\"id\":");          w.write(String.valueOf(r.idProducto()));        w.write(',');
                w.write("\"nombre\":\"");    w.write(json(r.nombreProducto()));              w.write("\",");
                w.write("\"precio\":");      w.write(String.format("%.2f", r.precio()));     w.write(',');
                w.write("\"claveHash\":");   w.write(String.valueOf(r.claveHash()));         w.write(',');
                w.write("\"slot\":");        w.write(String.valueOf(r.slot()));              w.write(',');
                w.write("\"probes\":");      w.write(String.valueOf(r.probes()));            w.write(',');
                w.write("\"durationNs\":");  w.write(String.valueOf(r.durationNs()));
                w.write('}');
            }
            w.newLine();
            w.write("  ]");
            w.newLine();
            w.write('}');
            w.newLine();
        }
    }

    public void exportProductoMarcaJson(List<ProductoMarcaRow> filas, Path destino) throws IOException {
        long total = filas.stream().mapToLong(ProductoMarcaRow::marcaDurationNs).sum();
        long max   = filas.stream().mapToLong(ProductoMarcaRow::marcaDurationNs).max().orElse(0);
        long prom  = filas.isEmpty() ? 0 : total / filas.size();

        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write('{');
            w.newLine();
            w.write(meta("4.2 Producto y su marca", filas.size(), prom, max));
            w.write("  \"filas\": [");
            w.newLine();
            boolean first = true;
            for (ProductoMarcaRow r : filas) {
                if (!first) { w.write(','); w.newLine(); }
                first = false;
                w.write("    {");
                w.write("\"idProducto\":");      w.write(String.valueOf(r.idProducto()));        w.write(',');
                w.write("\"nombreProducto\":\""); w.write(json(r.nombreProducto()));              w.write("\",");
                w.write("\"idMarca\":");          w.write(String.valueOf(r.idMarca()));           w.write(',');
                w.write("\"nombreMarca\":\"");    w.write(json(r.nombreMarca()));                 w.write("\",");
                w.write("\"paisMarca\":\"");      w.write(json(r.paisMarca()));                   w.write("\",");
                w.write("\"slotMarca\":");        w.write(String.valueOf(r.slotMarca()));         w.write(',');
                w.write("\"probesMarca\":");      w.write(String.valueOf(r.probesMarca()));       w.write(',');
                w.write("\"marcaDurationNs\":");  w.write(String.valueOf(r.marcaDurationNs()));
                w.write('}');
            }
            w.newLine();
            w.write("  ]");
            w.newLine();
            w.write('}');
            w.newLine();
        }
    }

    /* =====================================================================
     *  Helpers de escape
     * ===================================================================== */

    private static String meta(String reporte, int total, long promNs, long maxNs) {
        return "  \"reporte\": \"" + json(reporte) + "\",\n"
             + "  \"generadoEn\": \"" + LocalDateTime.now().format(ISO) + "\",\n"
             + "  \"totalFilas\": " + total + ",\n"
             + "  \"tiempoPromedioNs\": " + promNs + ",\n"
             + "  \"tiempoMaximoNs\": " + maxNs + ",\n";
    }

    /** Escapa un campo CSV según RFC 4180: comillas dobles si contiene , " o salto. */
    private static String csv(String s) {
        if (s == null) return "";
        boolean needsQuoting = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                            || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!needsQuoting) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** Escapa una cadena JSON: \", \\ y caracteres de control comunes. */
    private static String json(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
