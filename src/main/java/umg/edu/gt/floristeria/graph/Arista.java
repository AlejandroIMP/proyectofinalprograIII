package umg.edu.gt.floristeria.graph;

public class Arista {
    private final String origenId;
    private final String destinoId;
    private final String relacion; // Descripción de la unión (ej: "COMPRO", "CONTIENE", "PROVIENE_DE")

    public Arista(String origenId, String destinoId, String relacion) {
        this.origenId = origenId;
        this.destinoId = destinoId;
        this.relacion = relacion;
    }

    public String getOrigenId() { return origenId; }
    public String getDestinoId() { return destinoId; }
    public String getRelacion() { return relacion; }
}