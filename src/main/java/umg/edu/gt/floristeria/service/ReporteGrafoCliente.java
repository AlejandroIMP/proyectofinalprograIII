package umg.edu.gt.floristeria.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Estructura jerárquica del reporte 4.3 (recorrido del grafo a partir de un
 * cliente). Modela: Cliente → Año → Factura → Línea de detalle.
 * <p>
 * Es un DTO de solo lectura producido por
 * {@link ComercioDao#recorridoCliente(int)} y consumido por
 * {@link WordReportExporter#grafoCliente(ReporteGrafoCliente)}.
 *
 * @param idCliente     identificador del cliente consultado
 * @param nombreCliente nombre completo del cliente
 * @param anios         bloques por año (2024, 2025, 2026) con sus facturas
 * @param durationNs    tiempo de respuesta del recorrido en nanosegundos
 *                      (medición exigida por la rúbrica)
 */
public record ReporteGrafoCliente(int idCliente,
                                  String nombreCliente,
                                  List<Anio> anios,
                                  long durationNs) {

    /** Bloque de un año con todas las facturas emitidas en él. */
    public record Anio(int anio, List<Factura> facturas) {}

    /** Factura con sus líneas y el total acumulado. */
    public record Factura(int idFactura, LocalDate fecha, List<Linea> lineas, double total) {}

    /** Línea de detalle: producto adquirido, cantidad, subtotal y su marca. */
    public record Linea(int idItem, String producto, int cantidad,
                        double subtotal, String marca, String pais) {}
}
