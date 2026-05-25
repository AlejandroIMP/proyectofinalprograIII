package umg.edu.gt.floristeria.service;

import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.hash.CustomHashTable.Entry;
import umg.edu.gt.floristeria.hash.CustomHashTable.SearchResult;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;

import java.util.ArrayList;
import java.util.List;

/**
 * Generador de los reportes exigidos por la rúbrica de la sección 2.
 * <p>
 * Las filas se devuelven como {@code record}s puros, sin asunciones sobre el
 * medio de salida (CLI, GUI o exportación). Cada presentación posterior
 * (texto tabular, {@code TableView}, CSV) puede consumir las mismas listas.
 * <h2>Reportes implementados</h2>
 * <ul>
 *   <li><b>4.1 - Productos registrados</b>: para cada ítem del catálogo se
 *       reporta su clave hash calculada ({@code hashCode}), el slot físico
 *       donde quedó almacenado y el tiempo en nanosegundos que tardó su
 *       recuperación mediante {@link CustomHashTable#get(Object)}.</li>
 *   <li><b>4.2 - Producto y su marca</b>: para cada ítem se busca su
 *       proveedor en una <em>tabla hash separada</em> (la de marcas) y se
 *       reporta el tiempo de esa búsqueda específica, además de los datos
 *       de la marca asociada.</li>
 * </ul>
 */
public final class ReportService {

    /** Fila del reporte 4.1. */
    public record ProductoRow(int idProducto,
                              String nombreProducto,
                              double precio,
                              int claveHash,
                              int slot,
                              int probes,
                              long durationNs) {}

    /** Fila del reporte 4.2. */
    public record ProductoMarcaRow(int idProducto,
                                   String nombreProducto,
                                   int idMarca,
                                   String nombreMarca,
                                   String paisMarca,
                                   int slotMarca,
                                   int probesMarca,
                                   long marcaDurationNs) {}

    /**
     * Genera el reporte 4.1.
     * <p>
     * Para cada ítem en {@code catalogo} se ejecuta un {@code get(id)} para
     * obtener métricas reales (slot, probes, tiempo en ns). La clave hash
     * reportada es {@code Integer.hashCode(id)} — el mismo valor que la
     * tabla usa internamente antes del enmascarado y el módulo.
     */
    public List<ProductoRow> reporteProductos(
            CustomHashTable<Integer, ItemFloral> catalogo) {

        List<ProductoRow> filas = new ArrayList<>(catalogo.getSize());
        for (Entry<Integer, ItemFloral> e : catalogo.entries()) {
            SearchResult<ItemFloral> r = catalogo.get(e.key());
            ItemFloral item = e.value();
            filas.add(new ProductoRow(
                    item.id(),
                    item.nombreFlor(),
                    item.precio(),
                    Integer.hashCode(item.id()),
                    r.tablePosition(),
                    r.probes(),
                    r.durationNanoseconds()
            ));
        }
        return filas;
    }

    /**
     * Genera el reporte 4.2.
     * <p>
     * Para cada ítem se busca su proveedor en {@code marcas} (tabla hash
     * independiente) y se registra el tiempo de esa búsqueda. Si un ítem
     * apunta a un {@code idProveedor} inexistente en {@code marcas}, la
     * fila se incluye con nombre/país "(no encontrado)" pero conservando
     * la métrica del intento de búsqueda.
     */
    public List<ProductoMarcaRow> reporteProductoMarca(
            CustomHashTable<Integer, ItemFloral> catalogo,
            CustomHashTable<Integer, ProveedorOrigen> marcas) {

        List<ProductoMarcaRow> filas = new ArrayList<>(catalogo.getSize());
        for (Entry<Integer, ItemFloral> e : catalogo.entries()) {
            ItemFloral item = e.value();
            SearchResult<ProveedorOrigen> r = marcas.get(item.idProveedor());
            ProveedorOrigen marca = r.value();
            filas.add(new ProductoMarcaRow(
                    item.id(),
                    item.nombreFlor(),
                    item.idProveedor(),
                    marca != null ? marca.nombreFinca() : "(no encontrado)",
                    marca != null ? marca.pais()        : "(no encontrado)",
                    r.tablePosition(),
                    r.probes(),
                    r.durationNanoseconds()
            ));
        }
        return filas;
    }
}
