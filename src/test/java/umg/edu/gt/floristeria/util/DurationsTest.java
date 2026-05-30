package umg.edu.gt.floristeria.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de {@link Durations}.
 * <p>
 * Valida que cada una de las 4 ramas de unidad (ns / µs / ms / s) seleccione
 * la unidad y el sufijo correctos, además del manejo de valores negativos
 * (que se tratan como 0).
 */
class DurationsTest {

    /* ----------- human() — incluye rama de nanosegundos ----------- */

    @Test
    @DisplayName("human < 1_000 ns devuelve la rama 'ns'")
    void human_nanos() {
        assertEquals("347 ns",   Durations.human(347L));
        assertEquals("0 ns",     Durations.human(0L));
        assertEquals("999 ns",   Durations.human(999L));
    }

    @Test
    @DisplayName("human entre 1 µs y 1 ms devuelve la rama 'µs' con 2 decimales")
    void human_microsegundos() {
        String r = Durations.human(1_500L);            // 1.5 µs
        assertTrue(r.endsWith(" µs"), "sufijo µs esperado, obtenido: " + r);
        assertTrue(r.startsWith("1") || r.startsWith("1,") || r.startsWith("1."));
    }

    @Test
    @DisplayName("human entre 1 ms y 1 s devuelve la rama 'ms' con 3 decimales")
    void human_milisegundos() {
        String r = Durations.human(2_500_000L);        // 2.5 ms
        assertTrue(r.endsWith(" ms"), "sufijo ms esperado, obtenido: " + r);
    }

    @Test
    @DisplayName("human >= 1 s devuelve la rama 's' con 3 decimales")
    void human_segundos() {
        String r = Durations.human(3_750_000_000L);    // 3.75 s
        assertTrue(r.endsWith(" s") && !r.endsWith("ms") && !r.endsWith("µs") && !r.endsWith("ns"),
                "sufijo s esperado, obtenido: " + r);
    }

    @Test
    @DisplayName("human negativo se trata como 0 ns")
    void human_negativoSeTrataComoCero() {
        assertEquals("0 ns", Durations.human(-1L));
        assertEquals("0 ns", Durations.human(Long.MIN_VALUE));
    }

    /* ----------- humanCarga() — sin rama de nanosegundos ----------- */

    @Test
    @DisplayName("humanCarga < 1 ms usa µs (nunca ns)")
    void humanCarga_microsegundos() {
        // 500 ns en human() saldría como "500 ns"; en humanCarga() es 0.50 µs.
        String r = Durations.humanCarga(500L);
        assertTrue(r.endsWith(" µs"), "humanCarga nunca usa ns; obtenido: " + r);
    }

    @Test
    @DisplayName("humanCarga rama ms")
    void humanCarga_milisegundos() {
        String r = Durations.humanCarga(2_500_000L);
        assertTrue(r.endsWith(" ms"));
    }

    @Test
    @DisplayName("humanCarga rama s")
    void humanCarga_segundos() {
        String r = Durations.humanCarga(3_750_000_000L);
        assertTrue(r.endsWith(" s") && !r.endsWith("ms") && !r.endsWith("µs"));
    }

    @Test
    @DisplayName("humanCarga negativo → 0 µs (nunca ns)")
    void humanCarga_negativoSeTrataComoCero() {
        String r = Durations.humanCarga(-100L);
        assertTrue(r.endsWith(" µs"), "obtenido: " + r);
    }
}
