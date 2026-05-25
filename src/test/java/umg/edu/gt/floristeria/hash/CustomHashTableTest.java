package umg.edu.gt.floristeria.hash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de {@link CustomHashTable}.
 * <p>
 * Notas de diseño:
 * <ul>
 *   <li>La capacidad inicial es 101 (primo). Las claves enteras
 *       {@code 1, 102, 203, 304, ...} caen todas en el slot 1, lo que
 *       permite forzar colisiones de forma determinista.</li>
 *   <li>El umbral de rehash es 0.75 → con capacidad 101 se dispara al
 *       insertar la entrada número 76.</li>
 * </ul>
 */
class CustomHashTableTest {

    @Test
    @DisplayName("put + get devuelve el valor insertado")
    void putAndGet_retornaValorInsertado() {
        CustomHashTable<Integer, String> table = new CustomHashTable<>();
        table.put(42, "rosa");

        CustomHashTable.SearchResult<String> result = table.get(42);

        assertEquals("rosa", result.value());
        assertEquals(1, table.getSize());
        assertEquals(0, table.getCollisionCount());
    }

    @Test
    @DisplayName("put sobre clave existente reemplaza el valor sin contar colisión")
    void put_claveDuplicada_reemplazaValorSinIncrementarColisiones() {
        CustomHashTable<Integer, String> table = new CustomHashTable<>();
        table.put(7, "rosa");
        table.put(7, "tulipán");                  // misma clave: reemplazo

        assertEquals("tulipán", table.get(7).value());
        assertEquals(1, table.getSize(), "el reemplazo no debe incrementar size");
        assertEquals(0, table.getCollisionCount(),
                "actualizar una clave existente NO es colisión");
    }

    @Test
    @DisplayName("get sobre clave inexistente devuelve SearchResult con valor null")
    void get_claveInexistente_retornaSearchResultConValorNull() {
        CustomHashTable<Integer, String> table = new CustomHashTable<>();
        table.put(1, "lirio");

        CustomHashTable.SearchResult<String> result = table.get(999);

        assertNull(result.value());
        assertTrue(result.tablePosition() >= 0,
                "tablePosition debe ser un slot válido aunque la clave no exista");
        assertTrue(result.durationNanoseconds() >= 0);
    }

    @Test
    @DisplayName("Colisiones forzadas en el mismo slot se cuentan correctamente")
    void colisionesForzadas_seCuentanCorrectamente() {
        // Capacidad inicial = 101. Las claves 1, 102, 203, 304 colisionan
        // todas en el slot 1 (k % 101 == 1).
        CustomHashTable<Integer, String> table = new CustomHashTable<>();
        table.put(1,   "A");          // slot vacío → 0 colisiones
        table.put(102, "B");          // colisión #1
        table.put(203, "C");          // colisión #2
        table.put(304, "D");          // colisión #3

        assertEquals(4, table.getSize());
        assertEquals(3, table.getCollisionCount(),
                "deben contarse 3 colisiones reales (slot 1 ya ocupado)");

        // Las 4 claves siguen siendo recuperables; las que llegaron después
        // requieren más probes que la primera.
        assertEquals("A", table.get(1).value());
        assertEquals("D", table.get(304).value());
        assertEquals(1, table.get(1).probes(),
                "la cabeza de la cadena se encuentra en 1 probe");
        assertEquals(4, table.get(304).probes(),
                "el último insertado requiere recorrer toda la cadena");
    }

    @Test
    @DisplayName("Al superar el 75 % de carga, la tabla rehashea y conserva los datos")
    void rehash_alSuperar75PctMantieneTodosLosDatos() {
        CustomHashTable<Integer, Integer> table = new CustomHashTable<>();
        int capacidadInicial = table.getCapacity();    // 101

        // 76 elementos > 75 % de 101 → debe disparar al menos un rehash.
        for (int i = 0; i < 120; i++) {
            table.put(i, i * 10);
        }

        assertTrue(table.getCapacity() > capacidadInicial,
                "la capacidad debió crecer tras el rehash");
        assertEquals(120, table.getSize());

        // Todos los valores siguen accesibles tras el rehash.
        for (int i = 0; i < 120; i++) {
            assertEquals(Integer.valueOf(i * 10), table.get(i).value(),
                    "valor perdido tras rehash para la clave " + i);
        }
    }

    @Test
    @DisplayName("SearchResult reporta probes >= 1 y duración no negativa en un hit")
    void searchResult_reportaProbesYNanos() {
        CustomHashTable<String, Integer> table = new CustomHashTable<>();
        table.put("girasol", 500);

        CustomHashTable.SearchResult<Integer> r = table.get("girasol");

        assertEquals(500, r.value());
        assertTrue(r.probes() >= 1,
                "un hit debe haber visitado al menos un nodo");
        assertTrue(r.durationNanoseconds() >= 0,
                "la duración medida no puede ser negativa");
    }

    @Test
    @DisplayName("Claves con hashCode = Integer.MIN_VALUE no producen índice negativo")
    void hashCode_negativoNoLanzaArithmeticException() {
        CustomHashTable<EvilKey, String> table = new CustomHashTable<>();

        // Si la tabla usara Math.abs(...) este put habría calculado un índice
        // negativo y lanzado ArrayIndexOutOfBoundsException.
        assertDoesNotThrow(() -> table.put(new EvilKey("k1"), "valor"));

        CustomHashTable.SearchResult<String> r = table.get(new EvilKey("k1"));
        assertEquals("valor", r.value());
        assertTrue(r.tablePosition() >= 0,
                "el índice calculado debe ser no negativo incluso con hashCode = MIN_VALUE");
    }

    /**
     * Clave maliciosa cuyo {@code hashCode()} devuelve {@link Integer#MIN_VALUE}.
     * Sirve para verificar que el cálculo de índice usa enmascarado de bits
     * y no {@code Math.abs(...)}, que rompería ante este caso límite.
     */
    private record EvilKey(String id) {
        @Override
        public int hashCode() { return Integer.MIN_VALUE; }
    }
}
