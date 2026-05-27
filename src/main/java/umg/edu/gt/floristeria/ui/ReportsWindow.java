package umg.edu.gt.floristeria.ui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;
import umg.edu.gt.floristeria.service.ReportExporter;
import umg.edu.gt.floristeria.service.ReportService;
import umg.edu.gt.floristeria.service.ReportService.ProductoMarcaRow;
import umg.edu.gt.floristeria.service.ReportService.ProductoRow;
import umg.edu.gt.floristeria.util.Durations;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Ventana modal con los dos reportes obligatorios + análisis gráfico:
 * <ul>
 *   <li><b>4.1 Productos registrados</b>: tabla con clave hash, slot y
 *       tiempo de búsqueda. Exportable a CSV y JSON.</li>
 *   <li><b>4.2 Producto y su marca</b>: tabla con el tiempo de búsqueda
 *       de la marca asociada. Exportable a CSV y JSON.</li>
 *   <li><b>Gráficas</b>: histogramas de la distribución de tiempos en
 *       ambas tablas hash, útiles para evidenciar el comportamiento
 *       O(1) amortizado durante la defensa.</li>
 * </ul>
 */
public class ReportsWindow extends Stage {

    /** Bins de tiempo en nanosegundos usados por los histogramas. */
    private static final long[] BINS_NS = {
            0, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000, Long.MAX_VALUE
    };
    private static final String[] BIN_LABELS = {
            "0-200 ns", "200-500 ns", "500 ns-1 µs", "1-2 µs", "2-5 µs",
            "5-10 µs", "10-20 µs", "20-50 µs", "50-100 µs", "100 µs+"
    };

    private final List<ProductoRow>      filasProductos;
    private final List<ProductoMarcaRow> filasPM;

    public ReportsWindow(CustomHashTable<Integer, ItemFloral> catalogo,
                         CustomHashTable<Integer, ProveedorOrigen> marcas) {

        setTitle("Reportes de la Tabla Hash - Floristería UMG");

        ReportService rs = new ReportService();
        this.filasProductos = rs.reporteProductos(catalogo);
        this.filasPM        = rs.reporteProductoMarca(catalogo, marcas);

        Tab tabProductos = new Tab("4.1 Productos registrados",
                buildPaneProductos());
        tabProductos.setClosable(false);

        Tab tabPM = new Tab("4.2 Producto y su marca",
                buildPanePM());
        tabPM.setClosable(false);

        Tab tabGraficas = new Tab("Gráficas de tiempo", buildPaneGraficas());
        tabGraficas.setClosable(false);

        TabPane tabs = new TabPane(tabProductos, tabPM, tabGraficas);
        setScene(new Scene(tabs, 1020, 680));
    }

    /* ====================================================================
     *  Pestaña 4.1 - Productos
     * ==================================================================== */

    private BorderPane buildPaneProductos() {
        TableView<ProductoRow> table = new TableView<>();
        table.getColumns().addAll(List.of(
                col("ID",            ProductoRow::idProducto),
                colString("Nombre",  ProductoRow::nombreProducto, 240),
                col("Precio (Q)",    ProductoRow::precio),
                col("Clave hash",    ProductoRow::claveHash),
                col("Slot",          ProductoRow::slot),
                col("Probes",        ProductoRow::probes),
                colNanos("Tiempo",   ProductoRow::durationNs)
        ));
        table.setItems(FXCollections.observableArrayList(filasProductos));
        table.getSortOrder().add(table.getColumns().get(0));

        long avg = avgNs(filasProductos, ProductoRow::durationNs);
        long max = maxNs(filasProductos, ProductoRow::durationNs);

        Button btnCsv = new Button("Exportar CSV…");
        btnCsv.setOnAction(e -> guardar("4.1_productos.csv", "CSV", "*.csv", file -> {
            new ReportExporter().exportProductosCsv(filasProductos, file.toPath());
        }));
        Button btnJson = new Button("Exportar JSON…");
        btnJson.setOnAction(e -> guardar("4.1_productos.json", "JSON", "*.json", file -> {
            new ReportExporter().exportProductosJson(filasProductos, file.toPath());
        }));

        BorderPane bp = new BorderPane(table);
        bp.setBottom(footer(
                List.of(btnCsv, btnJson),
                "Total productos: " + filasProductos.size(),
                "Tiempo promedio: " + Durations.human(avg),
                "Tiempo máximo: "   + Durations.human(max)
        ));
        return bp;
    }

    /* ====================================================================
     *  Pestaña 4.2 - Producto-Marca
     * ==================================================================== */

    private BorderPane buildPanePM() {
        TableView<ProductoMarcaRow> table = new TableView<>();
        table.getColumns().addAll(List.of(
                col("ID Prod.",       ProductoMarcaRow::idProducto),
                colString("Producto", ProductoMarcaRow::nombreProducto, 220),
                col("ID Marca",       ProductoMarcaRow::idMarca),
                colString("Marca",    ProductoMarcaRow::nombreMarca, 200),
                colString("País",     ProductoMarcaRow::paisMarca, 100),
                col("Slot marca",     ProductoMarcaRow::slotMarca),
                col("Probes",         ProductoMarcaRow::probesMarca),
                colNanos("Tiempo marca", ProductoMarcaRow::marcaDurationNs)
        ));
        table.setItems(FXCollections.observableArrayList(filasPM));
        table.getSortOrder().add(table.getColumns().get(0));

        long avg = avgNs(filasPM, ProductoMarcaRow::marcaDurationNs);
        long max = maxNs(filasPM, ProductoMarcaRow::marcaDurationNs);

        Button btnCsv = new Button("Exportar CSV…");
        btnCsv.setOnAction(e -> guardar("4.2_producto_marca.csv", "CSV", "*.csv", file -> {
            new ReportExporter().exportProductoMarcaCsv(filasPM, file.toPath());
        }));
        Button btnJson = new Button("Exportar JSON…");
        btnJson.setOnAction(e -> guardar("4.2_producto_marca.json", "JSON", "*.json", file -> {
            new ReportExporter().exportProductoMarcaJson(filasPM, file.toPath());
        }));

        BorderPane bp = new BorderPane(table);
        bp.setBottom(footer(
                List.of(btnCsv, btnJson),
                "Total relaciones: " + filasPM.size(),
                "Promedio búsq. marca: " + Durations.human(avg),
                "Máximo: " + Durations.human(max)
        ));
        return bp;
    }

    /* ====================================================================
     *  Pestaña Gráficas
     * ==================================================================== */

    private VBox buildPaneGraficas() {
        BarChart<String, Number> chartProd = histograma(
                "Distribución de tiempos – Reporte 4.1 (productos)",
                filasProductos, ProductoRow::durationNs);
        BarChart<String, Number> chartMarca = histograma(
                "Distribución de tiempos – Reporte 4.2 (marcas)",
                filasPM, ProductoMarcaRow::marcaDurationNs);

        VBox box = new VBox(10, chartProd, chartMarca);
        box.setPadding(new Insets(10));
        return box;
    }

    /**
     * Construye un histograma {@link BarChart} con los bins definidos en
     * {@link #BINS_NS}. Pedagógicamente útil: una distribución concentrada
     * a la izquierda evidencia el comportamiento O(1) de la tabla hash;
     * outliers a la derecha permiten discutir el warmup del JIT.
     */
    private <T> BarChart<String, Number> histograma(String titulo,
                                                    List<T> filas,
                                                    Function<T, Long> getNs) {
        int[] counts = new int[BIN_LABELS.length];
        for (T r : filas) {
            long ns = getNs.apply(r);
            for (int i = 0; i < BIN_LABELS.length; i++) {
                if (ns < BINS_NS[i + 1]) { counts[i]++; break; }
            }
        }

        CategoryAxis x = new CategoryAxis();
        x.setLabel("Rango de tiempo");
        NumberAxis y = new NumberAxis();
        y.setLabel("Cantidad de búsquedas");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle(titulo);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(280);

        XYChart.Series<String, Number> s = new XYChart.Series<>();
        for (int i = 0; i < BIN_LABELS.length; i++) {
            s.getData().add(new XYChart.Data<>(BIN_LABELS[i], counts[i]));
        }
        chart.getData().add(s);
        return chart;
    }

    /* ====================================================================
     *  Helpers
     * ==================================================================== */

    /** Acción de exportar con FileChooser. */
    @FunctionalInterface
    private interface ExportAction { void run(File f) throws IOException; }

    private void guardar(String nombreDefault, String descripcion, String mask, ExportAction action) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Guardar reporte como " + descripcion);
        fc.setInitialFileName(nombreDefault);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(descripcion + " (" + mask + ")", mask));
        File destino = fc.showSaveDialog(this);
        if (destino == null) return;
        try {
            action.run(destino);
            new Alert(Alert.AlertType.INFORMATION,
                    "Reporte guardado en:\n" + destino.getAbsolutePath()).showAndWait();
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR,
                    "Error al guardar: " + ex.getMessage()).showAndWait();
        }
    }

    private static <T, R> TableColumn<T, R> col(String header, Function<T, R> getter) {
        TableColumn<T, R> c = new TableColumn<>(header);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(getter.apply(d.getValue())));
        c.setMinWidth(90);
        return c;
    }

    private static <T> TableColumn<T, String> colString(String header,
                                                        Function<T, String> getter,
                                                        double minWidth) {
        TableColumn<T, String> c = new TableColumn<>(header);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(getter.apply(d.getValue())));
        c.setMinWidth(minWidth);
        return c;
    }

    /**
     * Columna que muestra nanosegundos como cadena humana ("250 ns",
     * "1.20 µs") pero ordena numéricamente porque la celda guarda el valor
     * en ns como propiedad y solo se formatea para mostrarse.
     */
    private static <T> TableColumn<T, Long> colNanos(String header, Function<T, Long> getter) {
        TableColumn<T, Long> c = new TableColumn<>(header);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(getter.apply(d.getValue())));
        c.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : Durations.human(item));
            }
        });
        c.setMinWidth(110);
        return c;
    }

    private static <T> long avgNs(List<T> filas, Function<T, Long> getter) {
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

    private static HBox footer(List<Button> botones, String... textos) {
        HBox box = new HBox(15);
        box.setPadding(new Insets(8));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");
        List<javafx.scene.Node> nodos = new ArrayList<>(botones);
        if (!botones.isEmpty()) {
            nodos.add(new Separator(javafx.geometry.Orientation.VERTICAL));
        }
        for (String t : textos) {
            Label l = new Label(t);
            l.setStyle("-fx-font-family: 'Consolas', monospace;");
            nodos.add(l);
        }
        box.getChildren().addAll(nodos);
        return box;
    }
}
