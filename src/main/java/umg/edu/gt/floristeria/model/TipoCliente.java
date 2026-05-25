package umg.edu.gt.floristeria.model;

/**
 * Catálogo de tipos de cliente (equivalente directo de la entidad
 * {@code TIPO_CLIENTE} en Oracle).
 *
 * @param id         identificador único
 * @param nombre     etiqueta legible (p. ej. "Mayorista Alianzas")
 * @param descuento  descuento base aplicable, expresado en porcentaje (0-100)
 */
public record TipoCliente(int id, String nombre, double descuento) {}
