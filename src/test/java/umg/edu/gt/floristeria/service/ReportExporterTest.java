package umg.edu.gt.floristeria.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import umg.edu.gt.floristeria.service.ReportService.ProductoMarcaRow;
import umg.edu.gt.floristeria.service.ReportService.ProductoRow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de {@link ReportExporter}.
 * <p>
 * Cada test escribe en un {@code @TempDir} aislado y lee de vuelta el archivo
 * para validar formato (BOM UTF-8, header CSV, número de filas, escape de
 * comillas y campos JSON de metadatos).
 */
class ReportExporterTest {

    /** Carácter BOM UTF-8 que el exportador escribe como primer byte. */
    private static final char BOM = '﻿';

    private final ReportExporter exp = new ReportExporter();

    /* ============================ CSV ============================ */

    @Test
    @DisplayName("CSV de productos: BOM + header + una línea por fila")
    void csvProductos_estructuraBasica(@TempDir Path dir) throws IOException {
        Path archivo = dir.resolve("productos.csv");

        List<ProductoRow> filas = List.of(
                new ProductoRow(1000, "Rosa", 12.50, 1000, 91, 1, 250L),
                new ProductoRow(1001, "Tulipán Naranja", 15.00, 1001, 92, 2, 480L)
        );
        exp.exportProductosCsv(filas, archivo);

        String contenido = Files.readString(archivo, StandardCharsets.UTF_8);
        assertEquals(BOM, contenido.charAt(0), "el archivo debe empezar con BOM UTF-8");

        // Línea 1 después del BOM debe ser el header.
        String[] lineas = contenido.substring(1).split("\\R");
        assertEquals("id,nombre,precio,claveHash,slot,probes,durationNs", lineas[0]);

        // Header + 2 filas → 3 líneas no vacías.
        long noVacias = java.util.Arrays.stream(lineas).filter(l -> !l.isBlank()).count();
        assertEquals(3, noVacias, "header + 2 filas");

        // Contiene los datos numéricos relevantes.
        assertTrue(contenido.contains("1000"));
        assertTrue(contenido.contains("Rosa"));
        assertTrue(contenido.contains("12.50") || contenido.contains("12,50"),
                "precio con 2 decimales: " + contenido);
    }

    @Test
    @DisplayName("CSV escapa comillas y comas según RFC 4180")
    void csvProductos_escapaComillasYComas(@TempDir Path dir) throws IOException {
        Path archivo = dir.resolve("productos.csv");
        List<ProductoRow> filas = List.of(
                new ProductoRow(1, "Rosa, \"premium\"", 10.0, 1, 1, 1, 100L)
        );
        exp.exportProductosCsv(filas, archivo);

        String c = Files.readString(archivo, StandardCharsets.UTF_8);
        // Las comillas internas se duplican y el campo va entre comillas.
        assertTrue(c.contains("\"Rosa, \"\"premium\"\"\""),
                "esperaba el campo escapado con comillas dobles: " + c);
    }

    @Test
    @DisplayName("CSV de producto-marca incluye su header esperado")
    void csvProductoMarca_headerCorrecto(@TempDir Path dir) throws IOException {
        Path archivo = dir.resolve("producto-marca.csv");
        List<ProductoMarcaRow> filas = List.of(
                new ProductoMarcaRow(1000, "Rosa", 5, "Finca Países Bajos", "Holanda", 5, 1, 200L)
        );
        exp.exportProductoMarcaCsv(filas, archivo);

        String c = Files.readString(archivo, StandardCharsets.UTF_8);
        assertEquals(BOM, c.charAt(0));
        assertTrue(c.contains(
                "idProducto,nombreProducto,idMarca,nombreMarca,paisMarca,slotMarca,probesMarca,marcaDurationNs"),
                "header esperado en producto-marca.csv: " + c);
        assertTrue(c.contains("Finca Países Bajos"));
        assertTrue(c.contains("Holanda"));
    }

    /* ============================ JSON ============================ */

    @Test
    @DisplayName("JSON de productos contiene los campos de metadatos y una entrada por fila")
    void jsonProductos_metadatosYFilas(@TempDir Path dir) throws IOException {
        Path archivo = dir.resolve("productos.json");

        List<ProductoRow> filas = List.of(
                new ProductoRow(1000, "Rosa",     12.50, 1000, 91, 1, 200L),
                new ProductoRow(1001, "Tulipán",  15.00, 1001, 92, 2, 400L),
                new ProductoRow(1002, "Lirio",    18.00, 1002, 93, 1, 600L)
        );
        exp.exportProductosJson(filas, archivo);

        String c = Files.readString(archivo, StandardCharsets.UTF_8);

        // Metadatos exigidos.
        assertTrue(c.contains("\"reporte\""),         c);
        assertTrue(c.contains("\"generadoEn\""),      c);
        assertTrue(c.contains("\"totalFilas\": 3"),   c);
        assertTrue(c.contains("\"tiempoPromedioNs\": 400"), "promedio = (200+400+600)/3 = 400: " + c);
        assertTrue(c.contains("\"tiempoMaximoNs\": 600"),   c);

        // Una fila por entrada (cuenta de "\"id\":").
        long apariciones = c.chars().mapToObj(ch -> (char) ch).toString()
                .codePoints().count();
        long nFilas = (c.split("\"id\":", -1).length) - 1;
        assertEquals(3, nFilas, "esperaba 3 entradas \"id\": en el JSON");
        assertTrue(apariciones > 0); // sanity sobre el stream

        // Escapado JSON correcto (sin comillas sin escapar dentro de los strings).
        assertTrue(c.contains("\"Rosa\""));
    }

    @Test
    @DisplayName("JSON producto-marca contiene los campos esperados y respeta el escape")
    void jsonProductoMarca_camposYEscape(@TempDir Path dir) throws IOException {
        Path archivo = dir.resolve("producto-marca.json");

        List<ProductoMarcaRow> filas = List.of(
                new ProductoMarcaRow(1000, "Rosa \"premium\"", 5,
                        "Finca \"Tulipán\"", "Holanda", 5, 1, 300L)
        );
        exp.exportProductoMarcaJson(filas, archivo);

        String c = Files.readString(archivo, StandardCharsets.UTF_8);

        assertTrue(c.contains("\"reporte\""));
        assertTrue(c.contains("\"totalFilas\": 1"));
        assertTrue(c.contains("\"tiempoMaximoNs\": 300"));

        // Comillas internas escapadas con \".
        assertTrue(c.contains("Rosa \\\"premium\\\""), "comillas en nombre escapadas: " + c);
        assertTrue(c.contains("Finca \\\"Tulipán\\\""), "comillas en finca escapadas: " + c);
    }

    @Test
    @DisplayName("JSON con lista vacía produce metadatos en 0 y no falla")
    void jsonProductos_listaVacia(@TempDir Path dir) throws IOException {
        Path archivo = dir.resolve("vacio.json");
        exp.exportProductosJson(List.of(), archivo);

        String c = Files.readString(archivo, StandardCharsets.UTF_8);
        assertTrue(c.contains("\"totalFilas\": 0"));
        assertTrue(c.contains("\"tiempoPromedioNs\": 0"));
        assertTrue(c.contains("\"tiempoMaximoNs\": 0"));
    }
}
