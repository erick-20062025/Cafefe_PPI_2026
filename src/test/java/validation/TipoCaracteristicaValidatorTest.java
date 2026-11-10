
package validation;

import entity.TipoCaracteristica;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TipoCaracteristicaValidatorTest {

    private TipoCaracteristica crearTipoValido() {
        TipoCaracteristica tipo = new TipoCaracteristica();
        tipo.setIdTipoCaracteristica(UUID.randomUUID());
        tipo.setNombre("Temperatura");
        tipo.setActivo(true);
        tipo.setExpresionRegular("^(Frío|Caliente)$");
        return tipo;
    }

    @Test
    void debeAceptarTipoValido() {
        TipoCaracteristica tipo = crearTipoValido();

        assertDoesNotThrow(
            () -> TipoCaracteristicaValidator.validar(tipo)
        );
    }

    @Test
    void debeRechazarObjetoNulo() {
        assertThrows(
            IllegalArgumentException.class,
            () -> TipoCaracteristicaValidator.validar(null)
        );
    }

    @Test
    void debeRechazarNombreNulo() {
        TipoCaracteristica tipo = crearTipoValido();
        tipo.setNombre(null);

        assertThrows(
            IllegalArgumentException.class,
            () -> TipoCaracteristicaValidator.validar(tipo)
        );
    }

    @Test
    void debeRechazarNombreVacio() {
        TipoCaracteristica tipo = crearTipoValido();
        tipo.setNombre("   ");

        assertThrows(
            IllegalArgumentException.class,
            () -> TipoCaracteristicaValidator.validar(tipo)
        );
    }

    @Test
    void debeRechazarNombreMayorA155Caracteres() {
        TipoCaracteristica tipo = crearTipoValido();
        tipo.setNombre("A".repeat(156));

        assertThrows(
            IllegalArgumentException.class,
            () -> TipoCaracteristicaValidator.validar(tipo)
        );
    }

    @Test
    void debeAceptarNombreDe155Caracteres() {
        TipoCaracteristica tipo = crearTipoValido();
        tipo.setNombre("A".repeat(155));

        assertDoesNotThrow(
            () -> TipoCaracteristicaValidator.validar(tipo)
        );
    }

    @Test
    void debeRechazarActivoNulo() {
        TipoCaracteristica tipo = crearTipoValido();
        tipo.setActivo(null);

        assertThrows(
            IllegalArgumentException.class,
            () -> TipoCaracteristicaValidator.validar(tipo)
        );
    }

    @Test
    void debeRechazarExpresionRegularInvalida() {
        TipoCaracteristica tipo = crearTipoValido();
        tipo.setExpresionRegular("[abc");

        assertThrows(
            IllegalArgumentException.class,
            () -> TipoCaracteristicaValidator.validar(tipo)
        );
    }

    @Test
    void debeAceptarExpresionRegularNula() {
        TipoCaracteristica tipo = crearTipoValido();
        tipo.setExpresionRegular(null);

        assertDoesNotThrow(
            () -> TipoCaracteristicaValidator.validar(tipo)
        );
    }

    @Test
    void debeAceptarExpresionRegularVacia() {
        TipoCaracteristica tipo = crearTipoValido();
        tipo.setExpresionRegular("");

        assertDoesNotThrow(
            () -> TipoCaracteristicaValidator.validar(tipo)
        );
    }
}
