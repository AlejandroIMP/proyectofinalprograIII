package umg.edu.gt.floristeria;

import umg.edu.gt.floristeria.api.GraphRestApi;
import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.hash.CustomHashTable.SearchResult;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;
import umg.edu.gt.floristeria.service.CatalogoSource;
import umg.edu.gt.floristeria.service.CatalogoSources;
import umg.edu.gt.floristeria.service.DatabaseCatalogoSource;
import umg.edu.gt.floristeria.service.ReportExporter;
import umg.edu.gt.floristeria.service.ReportService;
import umg.edu.gt.floristeria.service.ReportService.ProductoMarcaRow;
import umg.edu.gt.floristeria.service.ReportService.ProductoRow;
import umg.edu.gt.floristeria.service.SyntheticCatalogoSource;
import umg.edu.gt.floristeria.ui.HashTableApp;
import umg.edu.gt.floristeria.util.Durations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        CatalogoSource source = seleccionarFuente(args);
        CustomHashTable<Integer, ItemFloral>       catalogo;
        CustomHashTable<Integer, ProveedorOrigen>  marcas;
        try {
            catalogo = source.cargar();
            marcas   = source.cargarMarcas();
        } catch (Exception ex) {
            System.err.println("ERROR cargando catálogo desde " + source.descripcion()
                    + ": " + ex.getMessage());
            System.err.println("Sugerencia: use --source=synth para forzar la fuente sintética.");
            System.exit(2);
            return;
        }
        System.out.printf("Catálogo cargado: %s%n", source.descripcion());

        mostrarMetricasDeCarga(catalogo);
        ejecutarBusquedas(catalogo);
        verificarReemplazoSinColision(catalogo);

        ReportService rs = new ReportService();
        List<ProductoRow>      filasProd = rs.reporteProductos(catalogo);
        List<ProductoMarcaRow> filasPM   = rs.reporteProductoMarca(catalogo, marcas);
        imprimirReporteProductos(filasProd);
        imprimirReporteProductoMarca(filasPM);

        exportarReportes(filasProd, filasPM);

        banner("FIN DE LA DEMOSTRACION");
        System.out.println("Tip: ejecuta con --gui (o `mvn javafx:run`) para los reportes completos.");

        // Sección 3.4 — salida controlada: libera estructuras al recibir Ctrl+C o SIGTERM
        final CustomHashTable<Integer, ItemFloral>      _cat = catalogo;
        final CustomHashTable<Integer, ProveedorOrigen> _mar = marcas;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println();
            System.out.println("[SHUTDOWN] Liberando estructuras en memoria...");
            System.out.printf("[SHUTDOWN] Catalogo  : %d items liberados%n",    _cat.getSize());
            System.out.printf("[SHUTDOWN] Marcas    : %d entradas liberadas%n", _mar.getSize());
            System.out.println("[SHUTDOWN] Conexiones Oracle: cerradas por try-with-resources.");
            System.out.println("[SHUTDOWN] Fin controlado del programa. Hasta pronto.");
        }, "shutdown-hook"));

        // API REST — secciones 4 y 5. Escucha en :8085 hasta Ctrl+C.
        // Las tablas hash se pasan para exponerlas en /api/hash/catalogo y /api/hash/marcas.
        GraphRestApi.iniciarServidor(catalogo, marcas);
    }

    /* --------------------------------------------------------------------- */
    /*  Selección de fuente de datos                                          */
    /* --------------------------------------------------------------------- */

    /**
     * Resuelve la fuente de datos a usar según los argumentos de línea de
     * comandos y las variables de entorno.
     * <ul>
     *   <li>{@code --source=oracle} fuerza Oracle. Si faltan variables de
     *       entorno, sale con código 2.</li>
     *   <li>{@code --source=synth} fuerza la fuente sintética aunque Oracle
     *       esté configurado.</li>
     *   <li>Sin flag: usa {@link CatalogoSources#defaultSource(int)}
     *       (auto-detect por {@code ORACLE_URL}).</li>
     * </ul>
     */
    private static CatalogoSource seleccionarFuente(String[] args) {
        String override = null;
        for (String a : args) {
            if (a.startsWith("--source=")) {
                override = a.substring("--source=".length());
                break;
            }
        }

        if ("oracle".equalsIgnoreCase(override)) {
            try {
                return DatabaseCatalogoSource.fromEnv();
            } catch (IllegalStateException ise) {
                System.err.println("ERROR: --source=oracle requiere variables de entorno: "
                        + ise.getMessage());
                System.exit(2);
                throw ise; // inalcanzable; satisface al compilador
            }
        }
        if ("synth".equalsIgnoreCase(override)) {
            return new SyntheticCatalogoSource(TOTAL_ITEMS);
        }
        return CatalogoSources.defaultSource(TOTAL_ITEMS);
    }

    /* --------------------------------------------------------------------- */
    /*  Export a CSV y JSON                                                   */
    /* --------------------------------------------------------------------- */

    /**
     * Persiste ambos reportes en archivos {@code reports/4.x_*.csv|.json}
     * relativos al directorio de trabajo actual. Los archivos siempre se
     * sobrescriben para que cada corrida quede registrada con los tiempos
     * más recientes.
     */
    private static void exportarReportes(List<ProductoRow> productos,
                                          List<ProductoMarcaRow> productoMarca) {
        banner("EXPORT DE REPORTES A CSV Y JSON");
        Path dir = Paths.get("reports");
        try {
            Files.createDirectories(dir);
            ReportExporter ex = new ReportExporter();

            Path csvProd  = dir.resolve("4.1_productos.csv");
            Path jsonProd = dir.resolve("4.1_productos.json");
            Path csvPM    = dir.resolve("4.2_producto_marca.csv");
            Path jsonPM   = dir.resolve("4.2_producto_marca.json");

            ex.exportProductosCsv(productos, csvProd);
            ex.exportProductosJson(productos, jsonProd);
            ex.exportProductoMarcaCsv(productoMarca, csvPM);
            ex.exportProductoMarcaJson(productoMarca, jsonPM);

            System.out.println("   " + csvProd.toAbsolutePath());
            System.out.println("   " + jsonProd.toAbsolutePath());
            System.out.println("   " + csvPM.toAbsolutePath());
            System.out.println("   " + jsonPM.toAbsolutePath());
        } catch (IOException ioe) {
            System.err.println("Error escribiendo reportes: " + ioe.getMessage());
        }
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
            System.out.printf("HIT  id=%-5d -> slot=%-3d probes=%-2d duracion=%-12s | %s%n",
                    id, r.tablePosition(), r.probes(),
                    Durations.human(r.durationNanoseconds()),
                    r.value().nombreFlor());
        }
        for (int id : misses) {
            SearchResult<ItemFloral> r = t.get(id);
            System.out.printf("MISS id=%-5d -> slot=%-3d probes=%-2d duracion=%-12s | (no encontrado)%n",
                    id, r.tablePosition(), r.probes(),
                    Durations.human(r.durationNanoseconds()));
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

    private static void imprimirReporteProductos(List<ProductoRow> filas) {
        banner("REPORTE 4.1 - PRODUCTOS REGISTRADOS (clave hash, slot, tiempo)");
        System.out.printf("%-6s %-30s %10s %12s %6s %8s %14s%n",
                "ID", "NOMBRE", "PRECIO", "CLAVE_HASH", "SLOT", "PROBES", "TIEMPO");
        System.out.println("-".repeat(94));

        long totalNs = 0;
        long maxNs   = 0;
        int  shown   = 0;
        for (ProductoRow r : filas) {
            totalNs += r.durationNs();
            if (r.durationNs() > maxNs) maxNs = r.durationNs();
            if (shown < FILAS_REPORTE_CLI) {
                System.out.printf("%-6d %-30s %10.2f %12d %6d %8d %14s%n",
                        r.idProducto(),
                        truncar(r.nombreProducto(), 30),
                        r.precio(),
                        r.claveHash(),
                        r.slot(),
                        r.probes(),
                        Durations.human(r.durationNs()));
                shown++;
            }
        }
        if (filas.size() > FILAS_REPORTE_CLI) {
            System.out.printf("... (%d filas más en el reporte completo)%n",
                    filas.size() - FILAS_REPORTE_CLI);
        }
        long avgNs = filas.isEmpty() ? 0 : totalNs / filas.size();
        System.out.println("-".repeat(94));
        System.out.printf("Total filas: %d  |  Tiempo promedio: %s  |  Tiempo máximo: %s%n",
                filas.size(), Durations.human(avgNs), Durations.human(maxNs));
    }

    /* --------------------------------------------------------------------- */
    /*  Reporte 4.2 - Producto y su marca                                     */
    /* --------------------------------------------------------------------- */

    private static void imprimirReporteProductoMarca(List<ProductoMarcaRow> filas) {
        banner("REPORTE 4.2 - PRODUCTO Y SU MARCA (tiempo de búsqueda de marca)");
        System.out.printf("%-6s %-26s %-26s %-12s %6s %14s%n",
                "ID_P", "PRODUCTO", "MARCA", "PAIS", "SLOT_M", "TIEMPO_MARCA");
        System.out.println("-".repeat(98));

        long totalNs = 0;
        long maxNs   = 0;
        int  shown   = 0;
        for (ProductoMarcaRow r : filas) {
            totalNs += r.marcaDurationNs();
            if (r.marcaDurationNs() > maxNs) maxNs = r.marcaDurationNs();
            if (shown < FILAS_REPORTE_CLI) {
                System.out.printf("%-6d %-26s %-26s %-12s %6d %14s%n",
                        r.idProducto(),
                        truncar(r.nombreProducto(), 26),
                        truncar(r.nombreMarca(), 26),
                        truncar(r.paisMarca(), 12),
                        r.slotMarca(),
                        Durations.human(r.marcaDurationNs()));
                shown++;
            }
        }
        if (filas.size() > FILAS_REPORTE_CLI) {
            System.out.printf("... (%d filas más en el reporte completo)%n",
                    filas.size() - FILAS_REPORTE_CLI);
        }
        long avgNs = filas.isEmpty() ? 0 : totalNs / filas.size();
        System.out.println("-".repeat(98));
        System.out.printf("Total filas: %d  |  Tiempo promedio búsqueda marca: %s  |  Máximo: %s%n",
                filas.size(), Durations.human(avgNs), Durations.human(maxNs));
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
    }
}
