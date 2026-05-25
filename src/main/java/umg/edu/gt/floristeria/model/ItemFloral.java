package umg.edu.gt.floristeria.model;

/**
 * Ítem floral del catálogo (equivalente de PRODUCTO en la rúbrica).
 *
 * @param id            PK numérica
 * @param nombreFlor    descripción comercial del ítem
 * @param precio        precio unitario de lista (> 0)
 * @param idProveedor   FK hacia {@link ProveedorOrigen}
 */
public record ItemFloral(int id,
                         String nombreFlor,
                         double precio,
                         int idProveedor) {}
