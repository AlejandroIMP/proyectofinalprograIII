package umg.edu.gt.floristeria.graph;

public class Nodo {
    private final String id;       // Identificador único (ej: "CLI_1", "FAC_500")
    private final String label;    // Nombre visible (ej: "Alejandro Sian", "Factura #1023")
    private final String tipo;     // Categoría (ej: "CLIENTE", "FACTURA", "ITEM", "PROVEEDOR")

    public Nodo(String id, String label, String tipo) {
        this.id = id;
        this.label = label;
        this.tipo = tipo;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getTipo() { return tipo; }
}
