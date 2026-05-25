package umg.edu.gt.floristeria.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.hash.CustomHashTable.SearchResult;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;
import umg.edu.gt.floristeria.service.CatalogoSource;
import umg.edu.gt.floristeria.service.SyntheticCatalogoSource;

import java.util.List;

/**
 * Interfaz gráfica JavaFX para visualizar la {@link CustomHashTable} del
 * Proyecto 2 - Floristería UMG.
 * <p>
 * La aplicación consume un {@link CatalogoSource} inyectado: hoy una fuente
 * sintética, en la sección 3 podrá ser una fuente Oracle sin cambiar nada
 * más que la línea de instanciación.
 * <p>
 * Capacidades visibles:
 * <ul>
 *   <li>Heatmap de slots coloreado por longitud de cadena.</li>
 *   <li>Panel de métricas en vivo (size, capacity, colisiones, load factor).</li>
 *   <li>Búsqueda animada: el slot consultado parpadea verde (hit) o rojo (miss).</li>
 *   <li>Detalle del slot al hacer click: muestra todas las claves encadenadas.</li>
 *   <li>Log de operaciones con SearchResult completo (probes, ns).</li>
 * </ul>
 */
public class HashTableApp extends Application {

    /* ---- Configuración visual --------------------------------------- */
    private static final int    CELL_SIZE          = 18;
    private static final int    GRID_COLUMNS       = 25;
    private static final int    DEFAULT_LOAD_COUNT = 200;

    private static final Color  COLOR_EMPTY        = Color.web("#e8e8e8");
    private static final Color  COLOR_ONE          = Color.web("#5cb85c");
    private static final Color  COLOR_TWO          = Color.web("#f0ad4e");
    private static final Color  COLOR_THREE_PLUS   = Color.web("#d9534f");
    private static final Color  COLOR_HIT_FLASH    = Color.web("#2b7ce9");
    private static final Color  COLOR_MISS_FLASH   = Color.web("#c00000");

    /* ---- Estado ------------------------------------------------------ */
    private CatalogoSource                            source  = new SyntheticCatalogoSource(DEFAULT_LOAD_COUNT);
    private CustomHashTable<Integer, ItemFloral>      tabla;
    private CustomHashTable<Integer, ProveedorOrigen> marcas;
    private Rectangle[]                               slots;     // refs a los rectángulos del heatmap

    /* ---- Widgets clave ----------------------------------------------- */
    private final GridPane     heatmap        = new GridPane();
    private final Label        lblSize        = metricLabel("0");
    private final Label        lblCapacity    = metricLabel("0");
    private final Label        lblCollisions  = metricLabel("0");
    private final Label        lblLoadFactor  = metricLabel("0.000");
    private final Label        lblSource      = new Label();
    private final Label        lblSlotDetail  = new Label("(haga click en un slot)");
    private final TextField    tfBuscar       = new TextField();
    private final TextArea     log            = new TextArea();

    @Override
    public void start(Stage stage) {
        recargar();   // carga inicial

        BorderPane root = new BorderPane();
        root.setTop(buildToolbar());
        root.setCenter(buildHeatmapPane());
        root.setRight(buildSidePanel());
        root.setBottom(buildLogPane());
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 1100, 720);
        stage.setTitle("Floristería UMG - Visor de Tabla Hash Personalizada");
        stage.setScene(scene);
        stage.show();
    }

    /* ====================================================================
     *  Construcción de UI
     * ==================================================================== */

    private HBox buildToolbar() {
        Button btnRecargar = new Button("Recargar catálogo");
        btnRecargar.setOnAction(e -> recargar());

        Button btnInsertar = new Button("Insertar siguiente ID");
        btnInsertar.setOnAction(e -> insertarSiguiente());

        Button btnReportes = new Button("Reportes 4.1 / 4.2");
        btnReportes.setOnAction(e -> abrirVentanaReportes());

        tfBuscar.setPromptText("ID a buscar (ej. 1050)");
        tfBuscar.setPrefWidth(160);
        Button btnBuscar = new Button("Buscar");
        btnBuscar.setOnAction(e -> ejecutarBusqueda());
        tfBuscar.setOnAction(e -> ejecutarBusqueda());

        HBox bar = new HBox(8, btnRecargar, btnInsertar, btnReportes,
                            new Separator(), tfBuscar, btnBuscar);
        bar.setPadding(new Insets(0, 0, 10, 0));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void abrirVentanaReportes() {
        if (tabla == null || marcas == null) {
            logLine("ERROR: no hay datos cargados aún.");
            return;
        }
        ReportsWindow rw = new ReportsWindow(tabla, marcas);
        rw.show();
        logLine("Ventana de reportes abierta (4.1 productos + 4.2 producto-marca)");
    }

    private ScrollPane buildHeatmapPane() {
        heatmap.setHgap(2);
        heatmap.setVgap(2);
        heatmap.setPadding(new Insets(10));
        heatmap.setStyle("-fx-background-color: white; -fx-border-color: #ddd;");
        ScrollPane sp = new ScrollPane(heatmap);
        sp.setFitToWidth(true);
        return sp;
    }

    private VBox buildSidePanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(0, 0, 0, 15));
        panel.setPrefWidth(280);

        panel.getChildren().addAll(
                titulo("Métricas"),
                metricRow("Size:",          lblSize),
                metricRow("Capacity:",      lblCapacity),
                metricRow("Colisiones:",    lblCollisions),
                metricRow("Load factor:",   lblLoadFactor),
                new Separator(),
                titulo("Fuente de datos"),
                lblSource,
                new Separator(),
                titulo("Leyenda"),
                legendRow("Vacío",      COLOR_EMPTY),
                legendRow("1 nodo",     COLOR_ONE),
                legendRow("2 nodos",    COLOR_TWO),
                legendRow("3+ nodos",   COLOR_THREE_PLUS),
                new Separator(),
                titulo("Detalle del slot"),
                lblSlotDetail
        );
        lblSlotDetail.setWrapText(true);
        lblSource.setWrapText(true);
        return panel;
    }

    private VBox buildLogPane() {
        log.setEditable(false);
        log.setPrefRowCount(6);
        log.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
        VBox box = new VBox(5, new Label("Log de operaciones:"), log);
        box.setPadding(new Insets(10, 0, 0, 0));
        return box;
    }

    /* ====================================================================
     *  Acciones
     * ==================================================================== */

    private void recargar() {
        try {
            long t0 = System.nanoTime();
            tabla  = source.cargar();
            marcas = source.cargarMarcas();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            logLine("Catálogo recargado en " + ms + " ms desde: " + source.descripcion()
                    + "  | marcas=" + marcas.getSize());
        } catch (Exception ex) {
            logLine("ERROR al recargar: " + ex.getMessage());
            return;
        }
        renderHeatmap();
        refreshMetrics();
        lblSource.setText(source.descripcion());
        lblSlotDetail.setText("(haga click en un slot)");
    }

    private void insertarSiguiente() {
        int nuevoId = SyntheticCatalogoSource.ID_INICIAL + tabla.getSize();
        ItemFloral nuevo = new ItemFloral(nuevoId, "Nuevo ítem #" + nuevoId, 12.34, 5);

        int capacityAntes = tabla.getCapacity();
        tabla.put(nuevoId, nuevo);
        int capacityDespues = tabla.getCapacity();

        if (capacityDespues != capacityAntes) {
            logLine("REHASH disparado: capacidad " + capacityAntes + " → " + capacityDespues);
            renderHeatmap();
        } else {
            updateSlot(slotDe(nuevoId));
        }
        refreshMetrics();
        flash(slotDe(nuevoId), COLOR_HIT_FLASH);
        logLine("PUT id=" + nuevoId + " (slot " + slotDe(nuevoId) + ")");
    }

    private void ejecutarBusqueda() {
        String raw = tfBuscar.getText();
        if (raw == null || raw.isBlank()) return;
        int id;
        try {
            id = Integer.parseInt(raw.trim());
        } catch (NumberFormatException nfe) {
            logLine("ID inválido: " + raw);
            return;
        }

        SearchResult<ItemFloral> r = tabla.get(id);
        boolean hit = r.value() != null;
        flash(r.tablePosition(), hit ? COLOR_HIT_FLASH : COLOR_MISS_FLASH);

        String desc = hit ? r.value().nombreFlor() : "(no encontrado)";
        logLine(String.format("%s id=%d  slot=%d  probes=%d  duración=%d ns  | %s",
                hit ? "HIT " : "MISS", id, r.tablePosition(), r.probes(),
                r.durationNanoseconds(), desc));

        // Selecciona ese slot en el panel de detalle.
        mostrarDetalleSlot(r.tablePosition());
    }

    /* ====================================================================
     *  Render del heatmap
     * ==================================================================== */

    private void renderHeatmap() {
        heatmap.getChildren().clear();
        int cap = tabla.getCapacity();
        slots = new Rectangle[cap];

        for (int s = 0; s < cap; s++) {
            Rectangle rect = new Rectangle(CELL_SIZE, CELL_SIZE);
            rect.setArcWidth(3);
            rect.setArcHeight(3);
            rect.setStroke(Color.web("#cccccc"));
            rect.setStrokeWidth(0.5);
            final int slotIdx = s;
            rect.setOnMouseClicked(e -> mostrarDetalleSlot(slotIdx));
            Tooltip.install(rect, new Tooltip("Slot " + s));

            slots[s] = rect;
            heatmap.add(rect, s % GRID_COLUMNS, s / GRID_COLUMNS);
            paintSlot(s);
        }
    }

    private void updateSlot(int slot) {
        if (slots != null && slot >= 0 && slot < slots.length) {
            paintSlot(slot);
        }
    }

    private void paintSlot(int slot) {
        int len = tabla.chainLengthAt(slot);
        Color c = switch (len) {
            case 0  -> COLOR_EMPTY;
            case 1  -> COLOR_ONE;
            case 2  -> COLOR_TWO;
            default -> COLOR_THREE_PLUS;
        };
        slots[slot].setFill(c);
    }

    /** Pinta el slot del color de flash y vuelve al color natural con animación. */
    private void flash(int slot, Color flashColor) {
        if (slot < 0 || slot >= slots.length) return;
        Rectangle r = slots[slot];
        Color natural = (Color) r.getFill();
        r.setFill(flashColor);

        ScaleTransition pulse = new ScaleTransition(Duration.millis(150), r);
        pulse.setFromX(1.0); pulse.setFromY(1.0);
        pulse.setToX(1.6);   pulse.setToY(1.6);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        pulse.play();

        FadeTransition fade = new FadeTransition(Duration.millis(600), r);
        fade.setFromValue(1.0);
        fade.setToValue(1.0);
        fade.setOnFinished(e -> r.setFill(natural));
        fade.play();
    }

    /* ====================================================================
     *  Detalle del slot
     * ==================================================================== */

    private void mostrarDetalleSlot(int slot) {
        if (slot < 0 || slot >= tabla.getCapacity()) {
            lblSlotDetail.setText("(slot fuera de rango)");
            return;
        }
        List<Integer> keys = tabla.keysAt(slot);
        if (keys.isEmpty()) {
            lblSlotDetail.setText("Slot " + slot + ": vacío");
            return;
        }
        StringBuilder sb = new StringBuilder("Slot ").append(slot)
                .append(" (cadena de ").append(keys.size()).append("):\n");
        for (Integer k : keys) {
            ItemFloral v = tabla.get(k).value();
            sb.append("  • id=").append(k).append("  ").append(v.nombreFlor())
              .append("  Q").append(String.format("%.2f", v.precio())).append('\n');
        }
        lblSlotDetail.setText(sb.toString());
    }

    /* ====================================================================
     *  Utilidades
     * ==================================================================== */

    private void refreshMetrics() {
        lblSize.setText(String.valueOf(tabla.getSize()));
        lblCapacity.setText(String.valueOf(tabla.getCapacity()));
        lblCollisions.setText(String.valueOf(tabla.getCollisionCount()));
        lblLoadFactor.setText(String.format("%.3f", (double) tabla.getSize() / tabla.getCapacity()));
    }

    private int slotDe(int key) {
        return (Integer.hashCode(key) & 0x7FFFFFFF) % tabla.getCapacity();
    }

    private void logLine(String msg) {
        log.appendText(msg + System.lineSeparator());
    }

    private static Label titulo(String texto) {
        Label l = new Label(texto);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        return l;
    }

    private static Label metricLabel(String inicial) {
        Label l = new Label(inicial);
        l.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 13px;");
        return l;
    }

    private static HBox metricRow(String label, Label valor) {
        Label l = new Label(label);
        l.setMinWidth(110);
        HBox box = new HBox(5, l, valor);
        return box;
    }

    private static HBox legendRow(String texto, Color color) {
        Rectangle r = new Rectangle(14, 14);
        r.setFill(color);
        r.setStroke(Color.web("#999"));
        return new HBox(6, r, new Label(texto));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
