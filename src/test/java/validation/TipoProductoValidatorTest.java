
package validation;

import entity.TipoProducto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TipoProductoValidatorTest {

    private TipoProducto crearTipoProductoValido() {
        TipoProducto tipo = new TipoProducto();
        tipo.setNombre("Bebidas");
        tipo.setActivo(true);
        return tipo;
    }

    @Test
    void tipoProductoValidoDebeSerAceptado() {
        assertDoesNotThrow(() ->
                TipoProductoValidator.validar(
                        crearTipoProductoValido()));
    }

    @Test
    void tipoProductoNuloDebeSerRechazado() {
        assertThrows(IllegalArgumentException.class,
                () -> TipoProductoValidator.validar(null));
    }

    @Test
    void nombreNuloDebeSerRechazado() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setNombre(null);

        assertThrows(IllegalArgumentException.class,
                () -> TipoProductoValidator.validar(tipo));
    }

    @Test
    void nombreVacioDebeSerRechazado() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setNombre("");

        assertThrows(IllegalArgumentException.class,
                () -> TipoProductoValidator.validar(tipo));
    }

    @Test
    void nombreConEspaciosDebeSerRechazado() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setNombre("   ");

        assertThrows(IllegalArgumentException.class,
                () -> TipoProductoValidator.validar(tipo));
    }

    @Test
    void nombreMayorA155CaracteresDebeSerRechazado() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setNombre("A".repeat(156));

        assertThrows(IllegalArgumentException.class,
                () -> TipoProductoValidator.validar(tipo));
    }

    @Test
    void nombreDe155CaracteresDebeSerAceptado() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setNombre("A".repeat(155));

        assertDoesNotThrow(() ->
                TipoProductoValidator.validar(tipo));
    }

    @Test
    void activoNuloDebeSerRechazado() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setActivo(null);

        assertThrows(IllegalArgumentException.class,
                () -> TipoProductoValidator.validar(tipo));
    }

    @Test
    void tipoProductoInactivoDebeSerAceptado() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setActivo(false);

        assertDoesNotThrow(() ->
                TipoProductoValidator.validar(tipo));
    }

    @Test
    void observacionesNulasDebenSerAceptadas() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setObservaciones(null);

        assertDoesNotThrow(() ->
                TipoProductoValidator.validar(tipo));
    }
}
