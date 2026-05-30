package umg.edu.gt.floristeria.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de {@link CatalogoSources}.
 * <p>
 * Estos tests se centran en la rama "sin Oracle" (la propiedad JVM se controla
 * en cada test) y se desactivan automáticamente cuando la variable de entorno
 * {@code ORACLE_URL} está definida en el SO — ese caso ya queda cubierto por
 * los IT.
 */
@DisabledIfEnvironmentVariable(named = "ORACLE_URL", matches = ".+",
        disabledReason = "Cuando ORACLE_URL viene como env var no podemos limpiarla desde Java")
class CatalogoSourcesTest {

    private String urlPrevia;

    @BeforeEach
    void setUp() {
        // Guardar y limpiar la propiedad JVM para empezar cada test desde un estado conocido.
        urlPrevia = System.getProperty("ORACLE_URL");
        System.clearProperty("ORACLE_URL");
    }

    @AfterEach
    void tearDown() {
        // Restaurar el estado original.
        if (urlPrevia == null) {
            System.clearProperty("ORACLE_URL");
        } else {
            System.setProperty("ORACLE_URL", urlPrevia);
        }
    }

    @Test
    @DisplayName("oracleConfigurado() = false cuando ni env var ni propiedad JVM están definidas")
    void oracleConfigurado_falseSinPropiedad() {
        assertFalse(CatalogoSources.oracleConfigurado());
    }

    @Test
    @DisplayName("oracleConfigurado() = true cuando se define ORACLE_URL como propiedad JVM")
    void oracleConfigurado_trueConPropiedad() {
        System.setProperty("ORACLE_URL", "jdbc:oracle:thin:@//host:1521/svc");
        assertTrue(CatalogoSources.oracleConfigurado());
    }

    @Test
    @DisplayName("oracleConfigurado() ignora valores en blanco")
    void oracleConfigurado_ignoraBlanco() {
        System.setProperty("ORACLE_URL", "   ");
        assertFalse(CatalogoSources.oracleConfigurado());
    }

    @Test
    @DisplayName("defaultSource(N) devuelve SyntheticCatalogoSource cuando Oracle no está configurado")
    void defaultSource_sinOracle_devuelveSintetica() {
        CatalogoSource src = CatalogoSources.defaultSource(20);

        assertInstanceOf(SyntheticCatalogoSource.class, src);
        assertTrue(src.descripcion().toLowerCase().contains("sintétic")
                || src.descripcion().toLowerCase().contains("sintetic"),
                "la descripción debe identificarla como sintética: " + src.descripcion());
    }
}
