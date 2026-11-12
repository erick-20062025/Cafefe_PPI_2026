
package validation;

import entity.TipoDescuento;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TipoDescuentoValidatorTest {

    private TipoDescuento crearTipoValido() {
        TipoDescuento tipo = new TipoDescuento();
        tipo.setIdTipoDescuento(UUID.randomUUID());
        tipo.setNombre("Descuento promocional");
        tipo.setActivo(true);
        tipo.setDescuentoMaximo(25);
        return tipo;
    }

    @Test
    void tipoValidoNoDebeLanzarExcepcion() {
        TipoDescuento tipo = crearTipoValido();

        assertDoesNotThrow(
                () -> TipoDescuentoValidator.validar(tipo));
    }

    @Test
    void tipoNuloDebeSerRechazado() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TipoDescuentoValidator.validar(null));
    }

    @Test
    void idNuloDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setIdTipoDescuento(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> TipoDescuentoValidator.validar(tipo));
    }

    @Test
    void nombreNuloDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setNombre(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> TipoDescuentoValidator.validar(tipo));
    }

    @Test
    void nombreVacioDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setNombre("");

        assertThrows(
                IllegalArgumentException.class,
                () -> TipoDescuentoValidator.validar(tipo));
    }

    @Test
    void nombreConEspaciosDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setNombre("   ");

        assertThrows(
                IllegalArgumentException.class,
                () -> TipoDescuentoValidator.validar(tipo));
    }

    @Test
    void estadoNuloDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setActivo(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> TipoDescuentoValidator.validar(tipo));
    }

    @Test
    void tipoInactivoDebeSerAceptado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setActivo(false);

        assertDoesNotThrow(
                () -> TipoDescuentoValidator.validar(tipo));
    }

    @Test
    void descuentoMaximoNuloDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setDescuentoMaximo(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> TipoDescuentoValidator.validar(tipo));
    }

    @Test
    void descuentoMaximoNegativoDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setDescuentoMaximo(-1);

        assertThrows(
                IllegalArgumentException.class,
                () -> TipoDescuentoValidator.validar(tipo));
    }

    @Test
    void descuentoMaximoMayorA100DebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setDescuentoMaximo(101);

        assertThrows(
                IllegalArgumentException.class,
                () -> TipoDescuentoValidator.validar(tipo));
    }

    @Test
    void descuentoMaximoCeroDebeSerAceptado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setDescuentoMaximo(0);

        assertDoesNotThrow(
                () -> TipoDescuentoValidator.validar(tipo));
    }

    @Test
    void descuentoMaximo100DebeSerAceptado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setDescuentoMaximo(100);

        assertDoesNotThrow(
                () -> TipoDescuentoValidator.validar(tipo));
    }

    @Test
    void observacionesNulasDebenSerAceptadas() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setObservaciones(null);

        assertDoesNotThrow(
                () -> TipoDescuentoValidator.validar(tipo));
    }
}
