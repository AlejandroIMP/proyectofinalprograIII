package umg.edu.gt.floristeria.service;

/**
 * Factory utility para seleccionar la implementación de {@link CatalogoSource}
 * apropiada según el entorno de ejecución.
 * <p>
 * <b>Política de auto-detect:</b> si la variable de entorno
 * {@code ORACLE_URL} está definida, se asume que el operador quiere usar la
 * base de datos real y se devuelve un {@link DatabaseCatalogoSource}. En
 * cualquier otro caso se devuelve un {@link SyntheticCatalogoSource} con el
 * tamaño indicado.
 * <p>
 * Este es el único punto del proyecto que decide qué fuente usar. La CLI y
 * la GUI lo invocan al arrancar; la GUI además permite sobreescribir la
 * decisión en runtime desde un {@code ComboBox}.
 */
public final class CatalogoSources {

    private CatalogoSources() { /* clase utilitaria */ }

    /**
     * Devuelve la fuente predeterminada de acuerdo con el entorno.
     *
     * @param syntheticCount cantidad de ítems sintéticos a generar si el
     *                       fallback termina seleccionando
     *                       {@link SyntheticCatalogoSource}.
     */
    public static CatalogoSource defaultSource(int syntheticCount) {
        if (oracleConfigurado()) {
            return DatabaseCatalogoSource.fromEnv();
        }
        return new SyntheticCatalogoSource(syntheticCount);
    }

    /**
     * @return {@code true} si {@code ORACLE_URL} está definida, ya sea como
     *         variable de entorno del SO o como propiedad JVM ({@code -DORACLE_URL=...}).
     */
    public static boolean oracleConfigurado() {
        String url = System.getenv("ORACLE_URL");
        if (url == null || url.isBlank()) {
            url = System.getProperty("ORACLE_URL");
        }
        return url != null && !url.isBlank();
    }
}
