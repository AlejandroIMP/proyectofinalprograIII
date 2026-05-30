package umg.edu.gt.floristeria.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import umg.edu.gt.floristeria.service.ReportService.ProductoMarcaRow;
import umg.edu.gt.floristeria.service.ReportService.ProductoRow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de {@link WordReportExporter}.
 * <p>
 * El exportador devuelve los reportes como {@code byte[]} con un .docx válido.
 * Cada test re-abre el byte array con {@link XWPFDocument} y comprueba el
 * título del reporte y la cantidad mínima de filas en la primera tabla.
 */
class WordReportExporterTest {

    private final WordReportExporter exp = new WordReportExporter();

    private static String docToText(byte[] doc) throws IOException {
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(doc))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : d.getParagraphs()) sb.append(p.getText()).append('\n');
            for (XWPFTable t : d.getTables()) {
                t.getRows().forEach(r ->
                        r.getTableCells().forEach(c -> sb.append(c.getText()).append('|')));
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    /* ============================ 4.1 ============================ */

    @Test
    @DisplayName("productos(): byte[] no vacío con título 4.1 y N+1 filas en la tabla")
    void productos_generaDocxValido() throws IOException {
        List<ProductoRow> filas = List.of(
                new ProductoRow(1000, "Rosa",     12.50, 1000, 91, 1, 250L),
                new ProductoRow(1001, "Tulipán",  15.00, 1001, 92, 2, 480L),
                new ProductoRow(1002, "Lirio",    18.00, 1002, 93, 1, 610L)
        );
        byte[] docBytes = exp.productos(filas);
        assertTrue(docBytes.length > 0, "el documento no debe estar vacío");

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docBytes))) {
            String texto = docToText(docBytes);
            assertTrue(texto.contains("Reporte 4.1"), "título 4.1 ausente: " + texto);
            assertTrue(texto.contains("Total de productos: 3"));

            assertFalse(doc.getTables().isEmpty(), "debe contener al menos una tabla");
            XWPFTable t = doc.getTables().get(0);
            assertEquals(filas.size() + 1, t.getRows().size(),
                    "header + " + filas.size() + " filas de datos");

            // Header esperado en la primera celda.
            assertEquals("ID", t.getRow(0).getCell(0).getText());
        }
    }

    /* ============================ 4.2 ============================ */

    @Test
    @DisplayName("productoMarca(): título 4.2 y filas con datos de marca")
    void productoMarca_generaDocxValido() throws IOException {
        List<ProductoMarcaRow> filas = List.of(
                new ProductoMarcaRow(1000, "Rosa", 5, "Finca Países Bajos", "Holanda", 5, 1, 200L),
                new ProductoMarcaRow(1001, "Tulipán", 6, "Floricola Quiteña", "Ecuador", 6, 1, 320L)
        );
        byte[] docBytes = exp.productoMarca(filas);
        assertTrue(docBytes.length > 0);

        String texto = docToText(docBytes);
        assertTrue(texto.contains("Reporte 4.2"), texto);
        assertTrue(texto.contains("Holanda"));
        assertTrue(texto.contains("Ecuador"));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docBytes))) {
            XWPFTable t = doc.getTables().get(0);
            assertEquals(filas.size() + 1, t.getRows().size());
        }
    }

    /* ============================ 4.3 ============================ */

    @Test
    @DisplayName("grafoCliente(): título 4.3 con nombre del cliente y bloque por año")
    void grafoCliente_generaDocxValido() throws IOException {
        ReporteGrafoCliente.Linea l1 = new ReporteGrafoCliente.Linea(
                50, "Rosa Roja", 3, 37.50, "Finca Países Bajos", "Holanda");
        ReporteGrafoCliente.Factura f1 = new ReporteGrafoCliente.Factura(
                1, LocalDate.of(2024, 3, 14), List.of(l1), 37.50);
        ReporteGrafoCliente.Anio a1 = new ReporteGrafoCliente.Anio(2024, List.of(f1));
        ReporteGrafoCliente data = new ReporteGrafoCliente(
                101, "Eventos Sky", List.of(a1), 12_345_678L);

        byte[] docBytes = exp.grafoCliente(data);
        assertTrue(docBytes.length > 0);

        String texto = docToText(docBytes);
        assertTrue(texto.contains("Reporte 4.3"), texto);
        assertTrue(texto.contains("Eventos Sky"), texto);
        assertTrue(texto.contains("Año 2024"),    texto);
        assertTrue(texto.contains("Factura #1"),  texto);
        assertTrue(texto.contains("Rosa Roja"),   texto);
    }

    @Test
    @DisplayName("grafoCliente() con cliente sin facturas escribe el mensaje informativo")
    void grafoCliente_sinFacturas_avisa() throws IOException {
        ReporteGrafoCliente vacio = new ReporteGrafoCliente(
                999, "Cliente Vacío", List.of(), 0L);
        byte[] docBytes = exp.grafoCliente(vacio);

        String texto = docToText(docBytes);
        assertTrue(texto.contains("no tiene facturas registradas"),
                "esperaba el mensaje de aviso: " + texto);
    }
}
