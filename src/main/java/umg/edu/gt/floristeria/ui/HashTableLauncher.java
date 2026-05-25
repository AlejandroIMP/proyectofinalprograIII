package umg.edu.gt.floristeria.ui;

/**
 * Launcher dedicado para {@link HashTableApp}.
 * <p>
 * <b>¿Por qué existe esta clase?</b> Desde Java 11, JavaFX dejó de venir con
 * el JDK y se distribuye como módulos separados. Cuando la clase que se
 * ejecuta extiende {@code javafx.application.Application} <em>directamente</em>,
 * la JVM exige que los módulos JavaFX estén en el <b>module-path</b>; si
 * vienen del classpath (como sucede con dependencias Maven sin configuración
 * extra) se produce el error:
 * <pre>
 * Error: JavaFX runtime components are missing, and are required to run
 * </pre>
 * <p>
 * El patrón recomendado por OpenJFX es interponer un launcher que NO extienda
 * {@code Application}. La JVM solo aplica la verificación de módulos cuando
 * la clase principal hereda de {@code Application}; al usar este launcher como
 * <em>main class</em> en IntelliJ (o desde {@code java}), JavaFX se carga
 * tranquilamente desde el classpath y todo funciona.
 * <p>
 * <b>Cómo usar en IntelliJ:</b> Run > Edit Configurations… > Main class:
 * {@code umg.edu.gt.floristeria.ui.HashTableLauncher}.
 */
public final class HashTableLauncher {

    private HashTableLauncher() { /* clase utilitaria */ }

    public static void main(String[] args) {
        HashTableApp.main(args);
    }
}
