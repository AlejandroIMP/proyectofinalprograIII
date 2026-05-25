package umg.edu.gt.floristeria.model;

/**
 * Línea de detalle de una factura. Cada registro asocia un ítem floral
 * a una factura con su cantidad y precio de venta efectivo.
 *
 * @param id            PK numérica
 * @param idFactura     FK hacia {@link Factura}
 * @param idItem        FK hacia {@link ItemFloral}
 * @param cantidad      unidades vendidas (> 0)
 * @param precioVenta   subtotal de la línea (>= 0)
 */
public record DetalleFactura(int id,
                             int idFactura,
                             int idItem,
                             int cantidad,
                             double precioVenta) {}
