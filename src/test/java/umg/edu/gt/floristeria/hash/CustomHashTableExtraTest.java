package umg.edu.gt.floristeria.hash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas complementarias de {@link CustomHashTable} que cubren los métodos
 * de introspección de slot (entries, keysAt, chainLengthAt, containsKey) y
 * los records {@link CustomHashTable.Entry} y {@link CustomHashTable.SearchResult}.
 * <p>
 * No duplican lo que ya cubre {@link CustomHashTableTest} — éste se centra en
 * put/get/colisiones/rehash. Aquí se valida la API que la GUI/REST usan para
 * dibujar la distribución de la tabla.
 */
class CustomHashTableExtraTest {

    @Test
    @DisplayName("containsKey devuelve true para clave presente y false para ausente")
    void containsKey_trueParaPresenteFalseParaAusente() {
        CustomHashTable<Integer, String> t = new CustomHashTable<>();
        t.put(10, "rosa");

        assertTrue(t.containsKey(10),  "10 fue insertada");
        assertFalse(t.containsKey(99), "99 nunca se insertó");
    }

    @Test
    @DisplayName("entries() devuelve todas las entradas en orden ascendente de slot")
    void entries_devuelveTodasOrdenadasPorSlot() {
        // Capacidad inicial 101: 1 → slot 1, 102 → slot 1 (cadena), 2 → slot 2
        CustomHashTable<Integer, String> t = new CustomHashTable<>();
        t.put(1,   "A");
        t.put(102, "B");   // misma cadena que 1
        t.put(2,   "C");

        List<CustomHashTable.Entry<Integer, String>> entries = t.entries();

        assertEquals(3, entries.size());
        // Slot 1 viene antes que slot 2.
        assertEquals(1, entries.get(0).slot());
        assertEquals(1, entries.get(1).slot());
        assertEquals(2, entries.get(2).slot());

        // Y la lista es inmutable.
        assertThrows(UnsupportedOperationException.class, () -> entries.add(null));
    }

    @Test
    @DisplayName("keysAt enumera las claves del slot en el orden de la cadena")
    void keysAt_listaClavesDelSlot() {
        CustomHashTable<Integer, String> t = new CustomHashTable<>();
        t.put(1,   "A");
        t.put(102, "B");          // misma cadena
        t.put(203, "C");          // misma cadena

        List<Integer> claves = t.keysAt(1);
        assertEquals(List.of(1, 102, 203), claves,
                "claves en orden de inserción en la cadena");

        // Slot vacío → lista vacía.
        assertTrue(t.keysAt(50).isEmpty());

        // Inmutable.
        assertThrows(UnsupportedOperationException.class, () -> claves.add(999));
    }

    @Test
    @DisplayName("chainLengthAt cuenta los nodos de la cadena en un slot")
    void chainLengthAt_cuentaNodosDeLaCadena() {
        CustomHashTable<Integer, String> t = new CustomHashTable<>();
        t.put(1,   "A");
        t.put(102, "B");
        t.put(203, "C");

        assertEquals(3, t.chainLengthAt(1));
        assertEquals(0, t.chainLengthAt(50), "slot vacío → 0");
    }

    @Test
    @DisplayName("keysAt y chainLengthAt lanzan IndexOutOfBoundsException si slot < 0 o >= capacity")
    void slotFueraDeRango_lanzaExcepcion() {
        CustomHashTable<Integer, String> t = new CustomHashTable<>();

        assertThrows(IndexOutOfBoundsException.class, () -> t.keysAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> t.keysAt(t.getCapacity()));
        assertThrows(IndexOutOfBoundsException.class, () -> t.chainLengthAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> t.chainLengthAt(t.getCapacity()));
    }

    @Test
    @DisplayName("Entry expone key, value y slot tal como se almacenaron")
    void entry_accessors() {
        CustomHashTable<Integer, String> t = new CustomHashTable<>();
        t.put(7, "lirio");

        CustomHashTable.Entry<Integer, String> e = t.entries().get(0);

        assertEquals(7, e.key());
        assertEquals("lirio", e.value());
        assertEquals(t.get(7).tablePosition(), e.slot(),
                "Entry.slot debe coincidir con el slot devuelto por get()");
    }

    @Test
    @DisplayName("SearchResult expone los 4 componentes (value, slot, probes, duración)")
    void searchResult_accessors() {
        CustomHashTable<Integer, String> t = new CustomHashTable<>();
        t.put(11, "tulipán");

        CustomHashTable.SearchResult<String> r = t.get(11);

        assertEquals("tulipán", r.value());
        assertEquals(11 % t.getCapacity(), r.tablePosition());
        assertEquals(1, r.probes(), "1 solo probe en cadena de longitud 1");
        assertTrue(r.durationNanoseconds() >= 0);
    }
}
