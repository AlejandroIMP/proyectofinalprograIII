package umg.edu.gt.floristeria.model;

import java.time.LocalDate;

/**
 * Cabecera de factura comercial. Se persiste en la tabla {@code FACTURA},
 * particionada por rango de año de {@code fechaEmision} (2024-2026).
 *
 * @param id                PK numérica
 * @param fechaEmision      fecha en la que se emitió la factura
 * @param idCliente         FK hacia {@link Cliente}
 * @param serie             serie del documento fiscal (p. ej. "A")
 * @param numeroDocumento   correlativo dentro de la serie
 */
public record Factura(int id,
                      LocalDate fechaEmision,
                      int idCliente,
                      String serie,
                      int numeroDocumento) {}
