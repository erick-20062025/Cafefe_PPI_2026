
package validation;

import entity.Caracteristica;
import entity.TipoCaracteristica;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CaracteristicaValidatorTest {

    private Caracteristica crearCaracteristicaValida() {
        TipoCaracteristica tipo = new TipoCaracteristica();
        tipo.setNombre("Tamaño");
        tipo.setActivo(true);

        Caracteristica caracteristica = new Caracteristica();
        caracteristica.setNombre("Grande");
        caracteristica.setActivo(true);
        caracteristica.setIdTipoCaracteristica(tipo);

        return caracteristica;
    }

    @Test
    void caracteristicaValidaNoDebeLanzarExcepcion() {
        Caracteristica caracteristica = crearCaracteristicaValida();

        assertDoesNotThrow(() ->
                CaracteristicaValidator.validar(caracteristica));
    }

    @Test
    void caracteristicaNulaDebeSerRechazada() {
        IllegalArgumentException excepcion = assertThrows(
                IllegalArgumentException.class,
                () -> CaracteristicaValidator.validar(null));

        assertEquals("La característica no puede ser nula",
                excepcion.getMessage());
    }

    @Test
    void nombreNuloDebeSerRechazado() {
        Caracteristica caracteristica = crearCaracteristicaValida();
        caracteristica.setNombre(null);

        IllegalArgumentException excepcion = assertThrows(
                IllegalArgumentException.class,
                () -> CaracteristicaValidator.validar(caracteristica));

        assertEquals("El nombre es obligatorio",
                excepcion.getMessage());
    }

    @Test
    void nombreEnBlancoDebeSerRechazado() {
        Caracteristica caracteristica = crearCaracteristicaValida();
        caracteristica.setNombre("   ");

        assertThrows(IllegalArgumentException.class,
                () -> CaracteristicaValidator.validar(caracteristica));
    }

    @Test
    void nombreMayorA155CaracteresDebeSerRechazado() {
        Caracteristica caracteristica = crearCaracteristicaValida();
        caracteristica.setNombre("A".repeat(156));

        assertThrows(IllegalArgumentException.class,
                () -> CaracteristicaValidator.validar(caracteristica));
    }

    @Test
    void nombreDe155CaracteresDebeSerAceptado() {
        Caracteristica caracteristica = crearCaracteristicaValida();
        caracteristica.setNombre("A".repeat(155));

        assertDoesNotThrow(() ->
                CaracteristicaValidator.validar(caracteristica));
    }

    @Test
    void estadoNuloDebeSerRechazado() {
        Caracteristica caracteristica = crearCaracteristicaValida();
        caracteristica.setActivo(null);

        IllegalArgumentException excepcion = assertThrows(
                IllegalArgumentException.class,
                () -> CaracteristicaValidator.validar(caracteristica));

        assertEquals(
                "Debe indicar si la característica está activa o inactiva",
                excepcion.getMessage());
    }

    @Test
    void caracteristicaInactivaDebeSerAceptada() {
        Caracteristica caracteristica = crearCaracteristicaValida();
        caracteristica.setActivo(false);

        assertDoesNotThrow(() ->
                CaracteristicaValidator.validar(caracteristica));
    }

    @Test
    void tipoNuloDebeSerRechazado() {
        Caracteristica caracteristica = crearCaracteristicaValida();
        caracteristica.setIdTipoCaracteristica(null);

        IllegalArgumentException excepcion = assertThrows(
                IllegalArgumentException.class,
                () -> CaracteristicaValidator.validar(caracteristica));

        assertEquals("Debe seleccionar un tipo de característica",
                excepcion.getMessage());
    }

    @Test
    void tipoInactivoDebeSerRechazado() {
        Caracteristica caracteristica = crearCaracteristicaValida();
        caracteristica.getIdTipoCaracteristica().setActivo(false);

        IllegalArgumentException excepcion = assertThrows(
                IllegalArgumentException.class,
                () -> CaracteristicaValidator.validar(caracteristica));

        assertEquals(
                "No se puede asignar un tipo de característica inactivo",
                excepcion.getMessage());
    }

    @Test
    void tipoConEstadoNuloDebeSerRechazado() {
        Caracteristica caracteristica = crearCaracteristicaValida();
        caracteristica.getIdTipoCaracteristica().setActivo(null);

        assertThrows(IllegalArgumentException.class,
                () -> CaracteristicaValidator.validar(caracteristica));
    }
}
