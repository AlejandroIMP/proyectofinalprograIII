package umg.edu.gt.floristeria;

import umg.edu.gt.floristeria.api.GraphRestApi;
import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.hash.CustomHashTable.SearchResult;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;
import umg.edu.gt.floristeria.service.CatalogoSource;
import umg.edu.gt.floristeria.service.ReportService;
import umg.edu.gt.floristeria.service.ReportService.ProductoMarcaRow;
import umg.edu.gt.floristeria.service.ReportService.ProductoRow;
import umg.edu.gt.floristeria.service.SyntheticCatalogoSource;
import umg.edu.gt.floristeria.ui.HashTableApp;

import java.util.List;

/**
 * Demostración interactiva de la {@link CustomHashTable} sobre el modelo
 * {@link ItemFloral} del Proyecto 2 - Floristería UMG.
 * <p>
 * Modos de ejecución:
 * <ul>
 *   <li>Sin argumentos → demo completa en consola (carga, búsquedas,
 *       reemplazo y los dos reportes de la rúbrica).</li>
 *   <li>{@code --gui} → lanza la interfaz JavaFX ({@link HashTableApp}).</li>
 * </ul>
 */
public class Main {

    /** Cantidad de ítems sintéticos a cargar (suficiente para disparar rehash). */
    private static final int TOTAL_ITEMS = 200;

    /** Cantidad de filas a imprimir por reporte (los reportes completos van a la GUI). */
    private static final int FILAS_REPORTE_CLI = 15;

    public static void main(String[] args) {
        if (args.length > 0 && "--gui".equals(args[0])) {
            HashTableApp.main(args);
            return;
        }

        banner("PROYECTO 2 - FLORISTERIA UMG | DEMO TABLA HASH PERSONALIZADA");

        CatalogoSource source = new SyntheticCatalogoSource(TOTAL_ITEMS);
        CustomHashTable<Integer, ItemFloral>       catalogo;
        CustomHashTable<Integer, ProveedorOrigen>  marcas;
        try {
            catalogo = source.cargar();
            marcas   = source.cargarMarcas();
        } catch (Exception ex) {
            System.err.println("Error cargando catálogo: " + ex.getMessage());
            return;
        }
        System.out.printf("Catálogo cargado: %s%n", source.descripcion());

        mostrarMetricasDeCarga(catalogo);
        ejecutarBusquedas(catalogo);
        verificarReemplazoSinColision(catalogo);

        ReportService rs = new ReportService();
        imprimirReporteProductos(rs.reporteProductos(catalogo), catalogo.getSize());
        imprimirReporteProductoMarca(rs.reporteProductoMarca(catalogo, marcas),
                                     catalogo.getSize());

        banner("FIN DE LA DEMOSTRACION");
        System.out.println("Tip: ejecuta con --gui (o `mvn javafx:run`) para los reportes completos.");
    }

    /* --------------------------------------------------------------------- */
    /*  Metricas y busquedas (existente)                                      */
    /* --------------------------------------------------------------------- */

    private static void mostrarMetricasDeCarga(CustomHashTable<Integer, ItemFloral> t) {
        System.out.printf("%nMETRICAS DE LA TABLA HASH:%n");
        System.out.printf("   size            = %d%n", t.getSize());
        System.out.printf("   capacity        = %d  (creci%s respecto al inicial 101)%n",
                t.getCapacity(), t.getCapacity() > 101 ? "o" : "o no");
        System.out.printf("   collisionCount  = %d%n", t.getCollisionCount());
        System.out.printf("   load factor     = %.3f%n",
                (double) t.getSize() / t.getCapacity());
    }

    private static void ejecutarBusquedas(CustomHashTable<Integer, ItemFloral> t) {
        banner("BUSQUEDAS - 3 HITS + 2 MISSES");
        int[] hits   = { 1000, 1099, 1199 };
        int[] misses = { 9999, 7  };
        for (int id : hits) {
            SearchResult<ItemFloral> r = t.get(id);
            System.out.printf("HIT  id=%-5d -> slot=%-3d probes=%-2d duracion=%6d ns  | %s%n",
                    id, r.tablePosition(), r.probes(), r.durationNanoseconds(),
                    r.value().nombreFlor());
        }
        for (int id : misses) {
            SearchResult<ItemFloral> r = t.get(id);
            System.out.printf("MISS id=%-5d -> slot=%-3d probes=%-2d duracion=%6d ns  | (no encontrado)%n",
                    id, r.tablePosition(), r.probes(), r.durationNanoseconds());
        }
    }

    private static void verificarReemplazoSinColision(CustomHashTable<Integer, ItemFloral> t) {
        banner("PRUEBA DE REEMPLAZO (debe mantener size y collisionCount)");
        int sizeAntes       = t.getSize();
        int colisionesAntes = t.getCollisionCount();
        ItemFloral original    = t.get(1050).value();
        ItemFloral actualizado = new ItemFloral(1050, "Rosa Imperial PROMOCION",
                                                5.00, original.idProveedor());
        t.put(1050, actualizado);
        System.out.printf("   Antes  -> size=%d  colisiones=%d  nombre=%s%n",
                sizeAntes, colisionesAntes, original.nombreFlor());
        System.out.printf("   Despues-> size=%d  colisiones=%d  nombre=%s%n",
                t.getSize(), t.getCollisionCount(), t.get(1050).value().nombreFlor());
        System.out.printf("   Resultado: size %s | colisiones %s | valor %s%n",
                t.getSize() == sizeAntes               ? "OK" : "FAIL",
                t.getCollisionCount() == colisionesAntes ? "OK" : "FAIL",
                "Rosa Imperial PROMOCION".equals(t.get(1050).value().nombreFlor()) ? "OK" : "FAIL");
    }

    /* --------------------------------------------------------------------- */
    /*  Reporte 4.1 - Productos registrados                                   */
    /* --------------------------------------------------------------------- */

    private static void imprimirReporteProductos(List<ProductoRow> filas, int totalCatalogo) {
        banner("REPORTE 4.1 - PRODUCTOS REGISTRADOS (clave hash, slot, tiempo)");
        System.out.printf("%-6s %-30s %10s %12s %6s %8s %12s%n",
                "ID", "NOMBRE", "PRECIO", "CLAVE_HASH", "SLOT", "PROBES", "TIEMPO_NS");
        System.out.println("-".repeat(90));

        long totalNs = 0;
        long maxNs   = 0;
        int  shown   = 0;
        for (ProductoRow r : filas) {
            totalNs += r.durationNs();
            if (r.durationNs() > maxNs) maxNs = r.durationNs();
            if (shown < FILAS_REPORTE_CLI) {
                System.out.printf("%-6d %-30s %10.2f %12d %6d %8d %12d%n",
                        r.idProducto(),
                        truncar(r.nombreProducto(), 30),
                        r.precio(),
                        r.claveHash(),
                        r.slot(),
                        r.probes(),
                        r.durationNs());
                shown++;
            }
        }
        if (filas.size() > FILAS_REPORTE_CLI) {
            System.out.printf("... (%d filas más en el reporte completo)%n",
                    filas.size() - FILAS_REPORTE_CLI);
        }
        long avgNs = filas.isEmpty() ? 0 : totalNs / filas.size();
        System.out.println("-".repeat(90));
        System.out.printf("Total filas: %d  |  Tiempo promedio: %d ns  |  Tiempo máximo: %d ns%n",
                filas.size(), avgNs, maxNs);
    }

    /* --------------------------------------------------------------------- */
    /*  Reporte 4.2 - Producto y su marca                                     */
    /* --------------------------------------------------------------------- */

    private static void imprimirReporteProductoMarca(List<ProductoMarcaRow> filas, int totalCatalogo) {
        banner("REPORTE 4.2 - PRODUCTO Y SU MARCA (tiempo de búsqueda de marca)");
        System.out.printf("%-6s %-26s %-26s %-12s %6s %12s%n",
                "ID_P", "PRODUCTO", "MARCA", "PAIS", "SLOT_M", "TIEMPO_NS_M");
        System.out.println("-".repeat(96));

        long totalNs = 0;
        long maxNs   = 0;
        int  shown   = 0;
        for (ProductoMarcaRow r : filas) {
            totalNs += r.marcaDurationNs();
            if (r.marcaDurationNs() > maxNs) maxNs = r.marcaDurationNs();
            if (shown < FILAS_REPORTE_CLI) {
                System.out.printf("%-6d %-26s %-26s %-12s %6d %12d%n",
                        r.idProducto(),
                        truncar(r.nombreProducto(), 26),
                        truncar(r.nombreMarca(), 26),
                        truncar(r.paisMarca(), 12),
                        r.slotMarca(),
                        r.marcaDurationNs());
                shown++;
            }
        }
        if (filas.size() > FILAS_REPORTE_CLI) {
            System.out.printf("... (%d filas más en el reporte completo)%n",
                    filas.size() - FILAS_REPORTE_CLI);
        }
        long avgNs = filas.isEmpty() ? 0 : totalNs / filas.size();
        System.out.println("-".repeat(96));
        System.out.printf("Total filas: %d  |  Tiempo promedio de búsqueda de marca: %d ns  |  Máximo: %d ns%n",
                filas.size(), avgNs, maxNs);
    }

    /* --------------------------------------------------------------------- */
    /*  Util                                                                  */
    /* --------------------------------------------------------------------- */

    private static String truncar(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void banner(String title) {
        String line = "=".repeat(Math.max(title.length() + 4, 40));
        System.out.printf("%n%s%n  %s%n%s%n", line, title, line);

        // Al final del método main de tu clase Main.java:
        GraphRestApi.iniciarServidor();
    }


}
