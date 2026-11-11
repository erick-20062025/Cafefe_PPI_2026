
package validation;

import entity.Producto;
import entity.ProductoTipoProducto;
import entity.TipoProducto;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProductoTipoProductoValidatorTest {

    private ProductoTipoProducto crearRelacionValida() {
        Producto producto = new Producto();
        producto.setIdProducto(UUID.randomUUID());

        TipoProducto tipo = new TipoProducto();
        tipo.setIdTipoProducto(UUID.randomUUID());
        tipo.setActivo(true);

        ProductoTipoProducto relacion =
                new ProductoTipoProducto();

        relacion.setIdProductoTipoProducto(UUID.randomUUID());
        relacion.setProducto(producto);
        relacion.setIdTipoProducto(tipo);

        return relacion;
    }

    @Test
    void relacionValidaDebeSerAceptada() {
        ProductoTipoProducto relacion = crearRelacionValida();

        assertDoesNotThrow(() ->
                ProductoTipoProductoValidator.validar(relacion));
    }

    @Test
    void relacionNulaDebeSerRechazada() {
        assertThrows(IllegalArgumentException.class,
                () -> ProductoTipoProductoValidator.validar(null));
    }

    @Test
    void productoNuloDebeSerRechazado() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.setProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoTipoProductoValidator.validar(relacion));
    }

    @Test
    void productoSinUuidDebeSerRechazado() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.getProducto().setIdProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoTipoProductoValidator.validar(relacion));
    }

    @Test
    void tipoProductoNuloDebeSerRechazado() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.setIdTipoProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoTipoProductoValidator.validar(relacion));
    }

    @Test
    void tipoProductoSinUuidDebeSerRechazado() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.getIdTipoProducto().setIdTipoProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoTipoProductoValidator.validar(relacion));
    }

    @Test
    void tipoProductoInactivoDebeSerRechazado() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.getIdTipoProducto().setActivo(false);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoTipoProductoValidator.validar(relacion));
    }

    @Test
    void tipoProductoConActivoNuloDebeSerRechazado() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.getIdTipoProducto().setActivo(null);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoTipoProductoValidator.validar(relacion));
    }

    @Test
    void fechaCreacionNulaDebeSerAceptada() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.setFechaCreacion(null);

        assertDoesNotThrow(() ->
                ProductoTipoProductoValidator.validar(relacion));
    }

    @Test
    void observacionesNulasDebenSerAceptadas() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.setObservaciones(null);

        assertDoesNotThrow(() ->
                ProductoTipoProductoValidator.validar(relacion));
    }
}
