package umg.edu.gt.floristeria.model;

/**
 * Cliente comercial de la floristería (equivalente de la entidad
 * {@code CLIENTE} en Oracle).
 *
 * @param id             PK numérica
 * @param nit            NIT fiscal único
 * @param nombre         razón social o nombre completo
 * @param direccion      dirección postal (puede ser {@code null})
 * @param idTipoCliente  FK hacia {@link TipoCliente}
 */
public record Cliente(int id,
                      String nit,
                      String nombre,
                      String direccion,
                      int idTipoCliente) {}
