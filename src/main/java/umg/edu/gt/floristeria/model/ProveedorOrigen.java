package umg.edu.gt.floristeria.model;

/**
 * Proveedor de origen del ítem floral (equivalente del catálogo MARCA en la
 * rúbrica). Representa una finca productora identificada por país.
 *
 * @param id            PK numérica
 * @param nombreFinca   nombre comercial de la finca
 * @param pais          país de origen (Holanda, Ecuador, Guatemala, Colombia…)
 */
public record ProveedorOrigen(int id, String nombreFinca, String pais) {}
