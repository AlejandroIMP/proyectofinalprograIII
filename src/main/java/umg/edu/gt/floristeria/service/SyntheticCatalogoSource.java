package umg.edu.gt.floristeria.service;

import umg.edu.gt.floristeria.hash.CustomHashTable;
import umg.edu.gt.floristeria.model.ItemFloral;
import umg.edu.gt.floristeria.model.ProveedorOrigen;
import umg.edu.gt.floristeria.model.TipoCliente;

/**
 * Implementación de {@link CatalogoSource} que genera ítems florales y
 * proveedores sintéticos en memoria. Útil para la demo CLI y para la GUI
 * antes de conectar la fuente real (Oracle, sección 3).
 * <p>
 * Los IDs de ítem son consecutivos desde {@value #ID_INICIAL}; cada ítem
 * tiene asignado uno de los 3 proveedores ({@link #PROVEEDORES_BASE}) en
 * rotación. Las marcas se cargan en una tabla hash separada para satisfacer
 * el reporte 4.2 (medición independiente del tiempo de búsqueda de marca).
 */
public class SyntheticCatalogoSource implements CatalogoSource {

    public static final int ID_INICIAL = 1000;

    private static final String[] FLORES = {
            "Rosas", "Tulipanes", "Lirios", "Girasoles", "Orquideas",
            "Claveles", "Margaritas", "Crisantemos", "Astromelias", "Gerberas"
    };

    /** Las 3 fincas coinciden con los IDs usados en {@link #cargar()}. */
    private static final ProveedorOrigen[] PROVEEDORES_BASE = {
            new ProveedorOrigen(5, "Finca Países Bajos S.A.", "Holanda"),
            new ProveedorOrigen(6, "Floricola Quiteña",       "Ecuador"),
            new ProveedorOrigen(7, "Cooperativa Antigua",     "Guatemala")
    };

    /** Los 4 tipos coinciden con los datos semilla de {@code install.sql}. */
    private static final TipoCliente[] TIPOS_BASE = {
            new TipoCliente(1, "Minorista Regular",      0.00),
            new TipoCliente(2, "Mayorista Alianzas",    10.50),
            new TipoCliente(3, "Eventos Premium",        5.00),
            new TipoCliente(4, "Floristería Afiliada",  15.00)
    };

    private final int totalItems;

    /** @param totalItems cantidad de ítems sintéticos a generar (>= 0). */
    public SyntheticCatalogoSource(int totalItems) {
        if (totalItems < 0) {
            throw new IllegalArgumentException("totalItems no puede ser negativo");
        }
        this.totalItems = totalItems;
    }

    @Override
    public CustomHashTable<Integer, ItemFloral> cargar() {
        CustomHashTable<Integer, ItemFloral> catalogo = new CustomHashTable<>();
        for (int i = 0; i < totalItems; i++) {
            int id = ID_INICIAL + i;
            String nombre = FLORES[i % FLORES.length] + " variedad #" + i;
            double precio = 10.00 + (i % 50);
            int idProveedor = PROVEEDORES_BASE[i % PROVEEDORES_BASE.length].id();
            catalogo.put(id, new ItemFloral(id, nombre, precio, idProveedor));
        }
        return catalogo;
    }

    @Override
    public CustomHashTable<Integer, ProveedorOrigen> cargarMarcas() {
        CustomHashTable<Integer, ProveedorOrigen> marcas = new CustomHashTable<>();
        for (ProveedorOrigen p : PROVEEDORES_BASE) {
            marcas.put(p.id(), p);
        }
        return marcas;
    }

    @Override
    public CustomHashTable<Integer, TipoCliente> cargarTiposCliente() {
        CustomHashTable<Integer, TipoCliente> tipos = new CustomHashTable<>();
        for (TipoCliente tc : TIPOS_BASE) {
            tipos.put(tc.id(), tc);
        }
        return tipos;
    }

    @Override
    public String descripcion() {
        return "Datos sintéticos (" + totalItems + " ítems, "
                + PROVEEDORES_BASE.length + " marcas, "
                + TIPOS_BASE.length + " tipos de cliente)";
    }
}
