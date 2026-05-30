package umg.edu.gt.floristeria.service;

import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;
import umg.edu.gt.floristeria.model.TipoCliente;

/**
 * Abstracción de una fuente de catálogo de ítems florales.
 * <p>
 * El propósito de esta interfaz es desacoplar la <b>presentación</b>
 * (CLI/GUI/API REST) del <b>origen real de los datos</b>. Hoy existe una sola
 * implementación con datos sintéticos en memoria
 * ({@link SyntheticCatalogoSource}); en la sección 3 de la guía se agregará
 * {@code OracleCatalogoSource} que leerá de la base de datos vía JDBC sin
 * que la GUI ni la demo CLI tengan que cambiar.
 * <p>
 * Contrato: cada llamada a {@link #cargar()} debe devolver una
 * {@link CustomHashTable} <em>nueva</em>, lista para usarse. La implementación
 * decide internamente cuántos ítems hay y de dónde vienen.
 */
public interface CatalogoSource {

    /**
     * Construye y retorna un catálogo de ítems florales indexado por su ID.
     *
     * @return tabla hash poblada con los ítems disponibles en la fuente
     * @throws Exception cuando la fuente subyacente falla (red, BD, IO…). Se
     *         deja como {@code Exception} a propósito para que cada
     *         implementación encapsule su error específico (p. ej. JDBC
     *         lanzará {@link java.sql.SQLException}).
     */
    CustomHashTable<Integer, ItemFloral> cargar() throws Exception;

    /**
     * Construye y retorna el catálogo de proveedores/marcas indexado por su
     * ID. Se carga en una tabla hash <em>separada</em> de los productos para
     * que las búsquedas por marca puedan medirse de forma independiente
     * (requisito del reporte 4.2 de la rúbrica).
     *
     * @return tabla hash con las marcas disponibles
     * @throws Exception cuando la fuente subyacente falla
     */
    CustomHashTable<Integer, ProveedorOrigen> cargarMarcas() throws Exception;

    /**
     * Construye y retorna el catálogo de tipos de cliente indexado por su ID.
     * Se carga en una tabla hash <em>separada</em> (la tercera estructura del
     * sistema, junto a productos y marcas) para que las búsquedas por tipo de
     * cliente puedan medirse de forma independiente.
     *
     * @return tabla hash con los tipos de cliente disponibles
     * @throws Exception cuando la fuente subyacente falla
     */
    CustomHashTable<Integer, TipoCliente> cargarTiposCliente() throws Exception;

    /**
     * @return nombre legible de la fuente para mostrarlo en la GUI o en logs
     *         (p. ej. "Datos sintéticos", "Oracle MV UMG").
     */
    String descripcion();
}
