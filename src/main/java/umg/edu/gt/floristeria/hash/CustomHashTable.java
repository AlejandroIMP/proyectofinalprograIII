package umg.edu.gt.floristeria.hash;

/**
 * Tabla hash genérica implementada desde cero (sin {@code java.util.HashMap}).
 * <p>
 * Estrategia de resolución de colisiones: <b>encadenamiento separado</b>
 * mediante listas enlazadas simples en cada slot.
 * <p>
 * Características:
 * <ul>
 *   <li>Capacidad inicial: número primo ({@value #INITIAL_CAPACITY}) para
 *       distribuir mejor índices ante {@code hashCode()} con patrones regulares.</li>
 *   <li>Rehash automático al superar el factor de carga
 *       ({@value #LOAD_FACTOR_THRESHOLD}), duplicando la capacidad al siguiente
 *       primo disponible.</li>
 *   <li>Conteo estricto de colisiones reales (excluye actualizaciones de
 *       claves ya existentes).</li>
 *   <li>{@link #get(Object)} retorna un {@link SearchResult} con métricas para
 *       fines didácticos: posición en la tabla, número de probes recorridos y
 *       duración en nanosegundos.</li>
 * </ul>
 *
 * @param <K> tipo de la clave (debe implementar {@code equals} y {@code hashCode})
 * @param <V> tipo del valor asociado
 */
public class    CustomHashTable<K, V> {

    /** Capacidad inicial. Número primo para reducir agrupaciones de índices. */
    private static final int INITIAL_CAPACITY = 101;

    /** Umbral de carga que dispara el rehash. */
    private static final double LOAD_FACTOR_THRESHOLD = 0.75;

    private Node<K, V>[] table;
    private int size;
    private int collisionCount;

    @SuppressWarnings("unchecked")
    public CustomHashTable() {
        this.table = (Node<K, V>[]) new Node[INITIAL_CAPACITY];
        this.size = 0;
        this.collisionCount = 0;
    }

    /** Nodo de la lista enlazada usada para encadenamiento en cada slot. */
    private static final class Node<K, V> {
        final K key;
        V value;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    /**
     * Calcula el índice en la tabla para una clave dada.
     * <p>
     * Se usa {@code (hashCode & 0x7FFFFFFF)} para forzar a positivo el bit de
     * signo. Esto evita el bug clásico de {@code Math.abs(Integer.MIN_VALUE)},
     * que devuelve {@code Integer.MIN_VALUE} (sigue siendo negativo) y
     * produciría un índice negativo tras la operación módulo.
     */
    private int indexFor(K key, int capacity) {
        return (key.hashCode() & 0x7FFFFFFF) % capacity;
    }

    /**
     * Inserta o actualiza el valor asociado a la clave dada.
     * <p>
     * Si el factor de carga supera {@value #LOAD_FACTOR_THRESHOLD} antes de
     * insertar, se ejecuta {@link #rehash()}. Si la clave ya existe en la
     * cadena del slot, se reemplaza el valor (no cuenta como colisión).
     * Si la clave es nueva y el slot ya estaba ocupado por otra clave,
     * se incrementa {@code collisionCount}.
     */
    public void put(K key, V value) {
        if ((double) size / table.length >= LOAD_FACTOR_THRESHOLD) {
            rehash();
        }

        int index = indexFor(key, table.length);
        Node<K, V> head = table[index];

        if (head == null) {
            table[index] = new Node<>(key, value);
            size++;
            return;
        }

        Node<K, V> current = head;
        while (true) {
            if (current.key.equals(key)) {
                current.value = value;          // actualización: NO es colisión
                return;
            }
            if (current.next == null) {
                current.next = new Node<>(key, value);
                size++;
                collisionCount++;               // clave nueva en slot ocupado
                return;
            }
            current = current.next;
        }
    }

    /**
     * Busca el valor asociado a la clave dada.
     *
     * @return un {@link SearchResult} con el valor (o {@code null} si no existe),
     *         la posición de la tabla consultada, la cantidad de nodos recorridos
     *         en la cadena (probes) y la duración total en nanosegundos.
     */
    public SearchResult<V> get(K key) {
        long start = System.nanoTime();
        int index = indexFor(key, table.length);
        Node<K, V> current = table[index];
        int probes = 0;

        while (current != null) {
            probes++;
            if (current.key.equals(key)) {
                return new SearchResult<>(current.value, index, probes,
                        System.nanoTime() - start);
            }
            current = current.next;
        }
        return new SearchResult<>(null, index, probes, System.nanoTime() - start);
    }

    /** @return {@code true} si la clave está presente en la tabla. */
    public boolean containsKey(K key) {
        int index = indexFor(key, table.length);
        for (Node<K, V> n = table[index]; n != null; n = n.next) {
            if (n.key.equals(key)) return true;
        }
        return false;
    }

    /**
     * Redimensiona la tabla al siguiente número primo {@code >= capacity * 2}.
     * <p>
     * Reinicia {@code size} y {@code collisionCount} y vuelve a insertar todos
     * los nodos mediante {@link #put(Object, Object)} para que el nuevo conteo
     * de colisiones refleje la distribución real en la nueva capacidad.
     */
    @SuppressWarnings("unchecked")
    private void rehash() {
        Node<K, V>[] oldTable = table;
        int newCapacity = nextPrime(oldTable.length * 2);
        this.table = (Node<K, V>[]) new Node[newCapacity];
        this.size = 0;
        this.collisionCount = 0;

        for (Node<K, V> head : oldTable) {
            for (Node<K, V> n = head; n != null; n = n.next) {
                put(n.key, n.value);
            }
        }
    }

    private static int nextPrime(int n) {
        if (n < 2) return 2;
        while (!isPrime(n)) n++;
        return n;
    }

    private static boolean isPrime(int n) {
        if (n < 2) return false;
        if (n == 2) return true;
        if (n % 2 == 0) return false;
        for (int i = 3; (long) i * i <= n; i += 2) {
            if (n % i == 0) return false;
        }
        return true;
    }

    public int getSize()           { return size; }
    public int getCapacity()       { return table.length; }
    public int getCollisionCount() { return collisionCount; }

    /**
     * Longitud de la cadena en el slot indicado (0 si está vacío). Útil para
     * visualizaciones tipo heatmap de la distribución de la tabla.
     *
     * @throws IndexOutOfBoundsException si {@code slot} está fuera de rango
     */
    public int chainLengthAt(int slot) {
        if (slot < 0 || slot >= table.length) {
            throw new IndexOutOfBoundsException("slot " + slot + " fuera de rango");
        }
        int n = 0;
        for (Node<K, V> cur = table[slot]; cur != null; cur = cur.next) n++;
        return n;
    }

    /**
     * Snapshot inmutable de las claves presentes en el slot indicado, en el
     * mismo orden en que están encadenadas. Útil para paneles de detalle en
     * la GUI.
     *
     * @throws IndexOutOfBoundsException si {@code slot} está fuera de rango
     */
    public java.util.List<K> keysAt(int slot) {
        if (slot < 0 || slot >= table.length) {
            throw new IndexOutOfBoundsException("slot " + slot + " fuera de rango");
        }
        java.util.ArrayList<K> out = new java.util.ArrayList<>();
        for (Node<K, V> cur = table[slot]; cur != null; cur = cur.next) {
            out.add(cur.key);
        }
        return java.util.Collections.unmodifiableList(out);
    }

    /**
     * Iteración completa de la tabla en orden de slot ascendente. Cada
     * entrada incluye el slot donde está físicamente almacenada. Útil para
     * reportes que requieren mostrar la distribución real de los datos.
     *
     * @return lista inmutable de {@link Entry} con todas las parejas clave-valor
     */
    public java.util.List<Entry<K, V>> entries() {
        java.util.ArrayList<Entry<K, V>> out = new java.util.ArrayList<>(size);
        for (int s = 0; s < table.length; s++) {
            for (Node<K, V> cur = table[s]; cur != null; cur = cur.next) {
                out.add(new Entry<>(cur.key, cur.value, s));
            }
        }
        return java.util.Collections.unmodifiableList(out);
    }

    /**
     * Entrada de la tabla expuesta a través de {@link #entries()}.
     *
     * @param key   la clave
     * @param value el valor asociado
     * @param slot  el índice físico donde está almacenada (resultado del hashing)
     */
    public record Entry<K, V>(K key, V value, int slot) {}

    /**
     * Resultado de una búsqueda con métricas de rendimiento.
     *
     * @param value               el valor encontrado, o {@code null} si la clave no existe
     * @param tablePosition       el slot consultado en la tabla
     * @param probes              cantidad de nodos recorridos en la cadena
     * @param durationNanoseconds duración total de la búsqueda en ns
     * @param <T>                 tipo del valor
     */
    public record SearchResult<T>(T value,
                                  int tablePosition,
                                  int probes,
                                  long durationNanoseconds) {}
}
