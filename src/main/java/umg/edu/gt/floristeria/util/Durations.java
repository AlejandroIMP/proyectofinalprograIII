package umg.edu.gt.floristeria.util;

/**
 * Formateo de duraciones en la unidad de tiempo más legible para humanos.
 * <p>
 * Las búsquedas en la tabla hash se miden con {@code System.nanoTime()}, lo
 * que produce valores muy dispares: una búsqueda directa puede tardar 200 ns
 * mientras que una carga completa con I/O puede llevar varios segundos.
 * Esta utilidad selecciona la unidad apropiada para cada caso y la imprime
 * <b>siempre con su sufijo explícito</b> (ns, µs, ms o s), de manera que
 * los reportes nunca dejen ambigüedad sobre la escala.
 *
 * <pre>
 *   Durations.human(    347L) → "347 ns"
 *   Durations.human(   1500L) → "1.50 µs"
 *   Durations.human(2_500_000L) → "2.500 ms"
 *   Durations.human(3_750_000_000L) → "3.750 s"
 * </pre>
 */
public final class Durations {

    private static final long NS_PER_US = 1_000L;
    private static final long NS_PER_MS = 1_000_000L;
    private static final long NS_PER_S  = 1_000_000_000L;

    private Durations() { /* clase utilitaria */ }

    /**
     * Convierte un intervalo en nanosegundos a una representación legible
     * con la unidad apropiada y su sufijo.
     *
     * @param nanos cantidad de nanosegundos (>= 0); valores negativos se
     *              tratan como 0.
     * @return cadena con el valor y la unidad ("ns", "µs", "ms" o "s")
     */
    public static String human(long nanos) {
        if (nanos < 0) nanos = 0;
        if (nanos < NS_PER_US) return nanos + " ns";
        if (nanos < NS_PER_MS) return String.format("%.2f µs", nanos / (double) NS_PER_US);
        if (nanos < NS_PER_S)  return String.format("%.3f ms", nanos / (double) NS_PER_MS);
        return String.format("%.3f s", nanos / (double) NS_PER_S);
    }

    /**
     * Misma idea que {@link #human(long)} pero pensado para tiempos de
     * <em>carga</em>: nunca usa nanosegundos porque a esa escala la unidad
     * no aporta legibilidad para el operador.
     */
    public static String humanCarga(long nanos) {
        if (nanos < 0) nanos = 0;
        if (nanos < NS_PER_MS) return String.format("%.2f µs", nanos / (double) NS_PER_US);
        if (nanos < NS_PER_S)  return String.format("%.3f ms", nanos / (double) NS_PER_MS);
        return String.format("%.3f s", nanos / (double) NS_PER_S);
    }
}
