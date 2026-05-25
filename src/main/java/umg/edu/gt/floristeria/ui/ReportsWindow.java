package umg.edu.gt.floristeria.ui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;
import umg.edu.gt.floristeria.service.ReportService;
import umg.edu.gt.floristeria.service.ReportService.ProductoMarcaRow;
import umg.edu.gt.floristeria.service.ReportService.ProductoRow;

import java.util.List;
import java.util.function.Function;

/**
 * Ventana modal con los dos reportes obligatorios de la sección 2:
 * <ul>
 *   <li><b>4.1 Productos registrados</b>: clave hash, slot y tiempo de
 *       búsqueda de cada producto.</li>
 *   <li><b>4.2 Producto y su marca</b>: tiempo de búsqueda de la marca
 *       asociada (consultada en su propia tabla hash).</li>
 * </ul>
 * Ambas tablas son ordenables (click en cabecera). El pie de cada pestaña
 * muestra el tiempo promedio y máximo para evidenciar el comportamiento
 * O(1) amortizado de la {@link CustomHashTable}.
 */
public class ReportsWindow extends Stage {

    public ReportsWindow(CustomHashTable<Integer, ItemFloral> catalogo,
                         CustomHashTable<Integer, ProveedorOrigen> marcas) {

        setTitle("Reportes de la Tabla Hash - Floristería UMG");

        ReportService rs = new ReportService();
        List<ProductoRow>       filasProductos = rs.reporteProductos(catalogo);
        List<ProductoMarcaRow>  filasPM        = rs.reporteProductoMarca(catalogo, marcas);

        Tab tabProductos = new Tab("4.1 Productos registrados",
                buildPaneProductos(filasProductos));
        tabProductos.setClosable(false);

        Tab tabPM = new Tab("4.2 Producto y su marca",
                buildPanePM(filasPM));
        tabPM.setClosable(false);

        TabPane tabs = new TabPane(tabProductos, tabPM);
        setScene(new Scene(tabs, 980, 620));
    }

    /* ====================================================================
     *  Pestaña 4.1
     * ==================================================================== */

    private BorderPane buildPaneProductos(List<ProductoRow> filas) {
        TableView<ProductoRow> table = new TableView<>();
        table.getColumns().addAll(List.of(
                col("ID",          ProductoRow::idProducto),
                colString("Nombre",ProductoRow::nombreProducto, 240),
                col("Precio",      ProductoRow::precio),
                col("Clave hash",  ProductoRow::claveHash),
                col("Slot",        ProductoRow::slot),
                col("Probes",      ProductoRow::probes),
                col("Tiempo (ns)", ProductoRow::durationNs)
        ));
        table.setItems(FXCollections.observableArrayList(filas));
        table.getSortOrder().add(table.getColumns().get(0)); // por ID

        BorderPane bp = new BorderPane(table);
        bp.setBottom(footer(
                "Total productos: " + filas.size(),
                "Tiempo promedio: " + promedioNs(filas, ProductoRow::durationNs) + " ns",
                "Tiempo máximo: "   + maxNs(filas, ProductoRow::durationNs) + " ns"
        ));
        return bp;
    }

    /* ====================================================================
     *  Pestaña 4.2
     * ==================================================================== */

    private BorderPane buildPanePM(List<ProductoMarcaRow> filas) {
        TableView<ProductoMarcaRow> table = new TableView<>();
        table.getColumns().addAll(List.of(
                col("ID Prod.",     ProductoMarcaRow::idProducto),
                colString("Producto", ProductoMarcaRow::nombreProducto, 220),
                col("ID Marca",     ProductoMarcaRow::idMarca),
                colString("Marca",  ProductoMarcaRow::nombreMarca, 200),
                colString("País",   ProductoMarcaRow::paisMarca, 100),
                col("Slot marca",   ProductoMarcaRow::slotMarca),
                col("Probes",       ProductoMarcaRow::probesMarca),
                col("Tiempo marca (ns)", ProductoMarcaRow::marcaDurationNs)
        ));
        table.setItems(FXCollections.observableArrayList(filas));
        table.getSortOrder().add(table.getColumns().get(0));

        BorderPane bp = new BorderPane(table);
        bp.setBottom(footer(
                "Total relaciones: " + filas.size(),
                "Tiempo promedio búsqueda marca: " + promedioNs(filas, ProductoMarcaRow::marcaDurationNs) + " ns",
                "Máximo: " + maxNs(filas, ProductoMarcaRow::marcaDurationNs) + " ns"
        ));
        return bp;
    }

    /* ====================================================================
     *  Helpers
     * ==================================================================== */

    /**
     * Construye una columna que extrae un valor genérico de un record. Usar
     * {@link ReadOnlyObjectWrapper} permite trabajar con records sin
     * necesidad de propiedades JavaFX, manteniendo el ordenamiento nativo.
     */
    private static <T, R> TableColumn<T, R> col(String header, Function<T, R> getter) {
        TableColumn<T, R> c = new TableColumn<>(header);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(getter.apply(d.getValue())));
        c.setMinWidth(90);
        return c;
    }

    /** Igual que {@link #col(String, Function)} pero con un ancho mínimo más amplio. */
    private static <T> TableColumn<T, String> colString(String header,
                                                        Function<T, String> getter,
                                                        double minWidth) {
        TableColumn<T, String> c = new TableColumn<>(header);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(getter.apply(d.getValue())));
        c.setMinWidth(minWidth);
        return c;
    }

    private static <T> long promedioNs(List<T> filas, Function<T, Long> getter) {
        if (filas.isEmpty()) return 0;
        long sum = 0;
        for (T row : filas) sum += getter.apply(row);
        return sum / filas.size();
    }

    private static <T> long maxNs(List<T> filas, Function<T, Long> getter) {
        long m = 0;
        for (T row : filas) {
            long v = getter.apply(row);
            if (v > m) m = v;
        }
        return m;
    }

    private static HBox footer(String... textos) {
        HBox box = new HBox(20);
        box.setPadding(new Insets(8));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");
        for (String t : textos) {
            Label l = new Label(t);
            l.setStyle("-fx-font-family: 'Consolas', monospace;");
            box.getChildren().add(l);
        }
        return box;
    }
}
