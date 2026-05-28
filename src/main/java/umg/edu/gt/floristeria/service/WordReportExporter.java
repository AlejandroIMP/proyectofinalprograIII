package umg.edu.gt.floristeria.service;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import umg.edu.gt.floristeria.service.ReportService.ProductoMarcaRow;
import umg.edu.gt.floristeria.service.ReportService.ProductoRow;
import umg.edu.gt.floristeria.util.Durations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Genera los reportes de la sección 4 como documentos Microsoft Word (.docx)
 * usando Apache POI (XWPF). Cada método devuelve el documento como
 * {@code byte[]}, listo para escribirse en una respuesta HTTP de descarga.
 * <p>
 * Todos los reportes incluyen la <b>medición del tiempo de respuesta</b> de
 * cada operación (por registro en 4.1/4.2, y del recorrido completo en 4.3),
 * formateada con {@link Durations#human(long)}.
 */
public final class WordReportExporter {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String VERDE = "1B4332";

    /* ====================================================================
     *  4.1 — Productos registrados
     * ==================================================================== */
    public byte[] productos(List<ProductoRow> filas) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            titulo(doc, "Reporte 4.1 — Productos registrados en el sistema");
            subtitulo(doc, "Floristería UMG · Programación III");

            long suma = 0, max = 0;
            for (ProductoRow r : filas) { suma += r.durationNs(); if (r.durationNs() > max) max = r.durationNs(); }
            long prom = filas.isEmpty() ? 0 : suma / filas.size();

            meta(doc, List.of(
                    "Generado: " + LocalDateTime.now().format(ISO),
                    "Total de productos: " + filas.size(),
                    "Tiempo de búsqueda promedio: " + Durations.human(prom),
                    "Tiempo de búsqueda máximo: " + Durations.human(max)));

            String[] headers = {"ID", "Nombre", "Precio (Q)", "Clave hash", "Slot", "Probes", "Tiempo de búsqueda"};
            XWPFTable t = nuevaTabla(doc, headers);
            for (ProductoRow r : filas) {
                fila(t, String.valueOf(r.idProducto()), r.nombreProducto(),
                        String.format("%.2f", r.precio()), String.valueOf(r.claveHash()),
                        String.valueOf(r.slot()), String.valueOf(r.probes()),
                        Durations.human(r.durationNs()));
            }
            return bytes(doc);
        }
    }

    /* ====================================================================
     *  4.2 — Producto y su marca
     * ==================================================================== */
    public byte[] productoMarca(List<ProductoMarcaRow> filas) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            titulo(doc, "Reporte 4.2 — Productos y su marca");
            subtitulo(doc, "Tiempo de búsqueda de la marca en su tabla hash");

            long suma = 0, max = 0;
            for (ProductoMarcaRow r : filas) { suma += r.marcaDurationNs(); if (r.marcaDurationNs() > max) max = r.marcaDurationNs(); }
            long prom = filas.isEmpty() ? 0 : suma / filas.size();

            meta(doc, List.of(
                    "Generado: " + LocalDateTime.now().format(ISO),
                    "Total de relaciones producto-marca: " + filas.size(),
                    "Tiempo de búsqueda de marca promedio: " + Durations.human(prom),
                    "Tiempo de búsqueda de marca máximo: " + Durations.human(max)));

            String[] headers = {"ID Prod.", "Producto", "ID Marca", "Marca", "País", "Slot marca", "Probes", "Tiempo búsqueda marca"};
            XWPFTable t = nuevaTabla(doc, headers);
            for (ProductoMarcaRow r : filas) {
                fila(t, String.valueOf(r.idProducto()), r.nombreProducto(),
                        String.valueOf(r.idMarca()), r.nombreMarca(), r.paisMarca(),
                        String.valueOf(r.slotMarca()), String.valueOf(r.probesMarca()),
                        Durations.human(r.marcaDurationNs()));
            }
            return bytes(doc);
        }
    }

    /* ====================================================================
     *  4.3 — Grafo Cliente → Facturas (por año) → Productos
     * ==================================================================== */
    public byte[] grafoCliente(ReporteGrafoCliente data) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            titulo(doc, "Reporte 4.3 — Cliente, Facturas y Productos");
            subtitulo(doc, "Cliente #" + data.idCliente() + " — " + data.nombreCliente());

            meta(doc, List.of(
                    "Generado: " + LocalDateTime.now().format(ISO),
                    "Tiempo de respuesta del recorrido: " + Durations.human(data.durationNs()),
                    "Años analizados: 2024, 2025, 2026"));

            if (data.anios().isEmpty()) {
                parrafo(doc, "El cliente no tiene facturas registradas en el rango 2024-2026.", false);
            }

            for (ReporteGrafoCliente.Anio anio : data.anios()) {
                encabezadoAnio(doc, "Año " + anio.anio() + "  (" + anio.facturas().size() + " factura(s))");

                for (ReporteGrafoCliente.Factura f : anio.facturas()) {
                    parrafoFactura(doc, "Factura #" + f.idFactura() + "   ·   Fecha: " + f.fecha()
                            + "   ·   Total: Q " + String.format("%.2f", f.total()));

                    String[] headers = {"ID Ítem", "Producto", "Cantidad", "Subtotal (Q)", "Marca", "País"};
                    XWPFTable t = nuevaTabla(doc, headers);
                    for (ReporteGrafoCliente.Linea l : f.lineas()) {
                        fila(t, String.valueOf(l.idItem()), l.producto(),
                                String.valueOf(l.cantidad()), String.format("%.2f", l.subtotal()),
                                l.marca(), l.pais());
                    }
                    espacio(doc);
                }
            }
            return bytes(doc);
        }
    }

    /* ====================================================================
     *  Helpers de construcción
     * ==================================================================== */

    private static void titulo(XWPFDocument doc, String texto) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun r = p.createRun();
        r.setText(texto);
        r.setBold(true);
        r.setFontSize(16);
        r.setColor(VERDE);
        r.setFontFamily("Arial");
    }

    private static void subtitulo(XWPFDocument doc, String texto) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(texto);
        r.setItalic(true);
        r.setFontSize(11);
        r.setColor("555555");
        r.setFontFamily("Arial");
    }

    private static void meta(XWPFDocument doc, List<String> lineas) {
        for (String s : lineas) {
            XWPFParagraph p = doc.createParagraph();
            p.setSpacingAfter(0);
            XWPFRun r = p.createRun();
            r.setText(s);
            r.setFontSize(10);
            r.setFontFamily("Arial");
        }
        espacio(doc);
    }

    private static void parrafo(XWPFDocument doc, String texto, boolean negrita) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(texto);
        r.setBold(negrita);
        r.setFontSize(11);
        r.setFontFamily("Arial");
    }

    private static void encabezadoAnio(XWPFDocument doc, String texto) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(200);
        XWPFRun r = p.createRun();
        r.setText(texto);
        r.setBold(true);
        r.setFontSize(13);
        r.setColor("2D6A4F");
        r.setFontFamily("Arial");
    }

    private static void parrafoFactura(XWPFDocument doc, String texto) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(120);
        XWPFRun r = p.createRun();
        r.setText(texto);
        r.setBold(true);
        r.setFontSize(11);
        r.setColor("333333");
        r.setFontFamily("Arial");
    }

    private static void espacio(XWPFDocument doc) {
        doc.createParagraph().createRun().setText("");
    }

    /** Crea una tabla con su fila de encabezado sombreada en verde. */
    private static XWPFTable nuevaTabla(XWPFDocument doc, String[] headers) {
        XWPFTable table = doc.createTable(1, headers.length);
        table.setWidth("100%");
        XWPFTableRow head = table.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            XWPFTableCell c = head.getCell(i);
            c.setColor(VERDE);
            celdaTexto(c, headers[i], true, "FFFFFF");
        }
        return table;
    }

    /** Agrega una fila de datos a la tabla. */
    private static void fila(XWPFTable table, String... valores) {
        XWPFTableRow row = table.createRow();
        for (int i = 0; i < valores.length; i++) {
            // createRow ya generó celdas según la fila anterior; aseguramos texto
            XWPFTableCell c = row.getCell(i);
            if (c == null) c = row.createCell();
            celdaTexto(c, valores[i] == null ? "" : valores[i], false, "000000");
        }
    }

    private static void celdaTexto(XWPFTableCell c, String texto, boolean negrita, String color) {
        // Reutiliza el párrafo vacío que toda celda nueva trae por defecto.
        XWPFParagraph p = c.getParagraphs().isEmpty() ? c.addParagraph() : c.getParagraphs().get(0);
        p.setSpacingAfter(0);
        XWPFRun r = p.createRun();
        r.setText(texto);
        r.setBold(negrita);
        r.setColor(color);
        r.setFontSize(9);
        r.setFontFamily("Arial");
    }

    private static byte[] bytes(XWPFDocument doc) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.write(out);
            return out.toByteArray();
        }
    }
}
