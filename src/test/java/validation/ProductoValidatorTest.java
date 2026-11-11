
package validation;

import entity.Producto;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductoValidatorTest {

    private Producto crearProductoValido() {
        Producto producto = new Producto();
        producto.setIdProducto(UUID.randomUUID());
        producto.setNombre("Café americano");
        producto.setActivo(true);
        producto.setPrecioSugerido(new BigDecimal("2.50"));
        return producto;
    }

    @Test
    void productoValidoNoDebeLanzarExcepcion() {
        Producto producto = crearProductoValido();

        assertDoesNotThrow(
                () -> ProductoValidator.validar(producto));
    }

    @Test
    void productoNuloDebeSerRechazado() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductoValidator.validar(null));
    }

    @Test
    void nombreNuloDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setNombre(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> ProductoValidator.validar(producto));
    }

    @Test
    void nombreVacioDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setNombre("");

        assertThrows(
                IllegalArgumentException.class,
                () -> ProductoValidator.validar(producto));
    }

    @Test
    void nombreConSoloEspaciosDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setNombre("   ");

        assertThrows(
                IllegalArgumentException.class,
                () -> ProductoValidator.validar(producto));
    }

    @Test
    void nombreMayorA155CaracteresDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setNombre("A".repeat(156));

        assertThrows(
                IllegalArgumentException.class,
                () -> ProductoValidator.validar(producto));
    }

    @Test
    void nombreDe155CaracteresDebeSerAceptado() {
        Producto producto = crearProductoValido();
        producto.setNombre("A".repeat(155));

        assertDoesNotThrow(
                () -> ProductoValidator.validar(producto));
    }

    @Test
    void estadoNuloDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setActivo(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> ProductoValidator.validar(producto));
    }

    @Test
    void productoInactivoDebeSerAceptado() {
        Producto producto = crearProductoValido();
        producto.setActivo(false);

        assertDoesNotThrow(
                () -> ProductoValidator.validar(producto));
    }

    @Test
    void precioNuloDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setPrecioSugerido(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> ProductoValidator.validar(producto));
    }

    @Test
    void precioNegativoDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setPrecioSugerido(new BigDecimal("-1.00"));

        assertThrows(
                IllegalArgumentException.class,
                () -> ProductoValidator.validar(producto));
    }

    @Test
    void precioCeroDebeSerAceptado() {
        Producto producto = crearProductoValido();
        producto.setPrecioSugerido(BigDecimal.ZERO);

        assertDoesNotThrow(
                () -> ProductoValidator.validar(producto));
    }

    @Test
    void comentariosNulosDebenSerAceptados() {
        Producto producto = crearProductoValido();
        producto.setComentarios(null);

        assertDoesNotThrow(
                () -> ProductoValidator.validar(producto));
    }
    @Test
void productoSinIdDebeSerRechazado() {
    Producto producto = crearProductoValido();
    producto.setIdProducto(null);

    assertThrows(
            IllegalArgumentException.class,
            () -> ProductoValidator.validar(producto));
}
}
