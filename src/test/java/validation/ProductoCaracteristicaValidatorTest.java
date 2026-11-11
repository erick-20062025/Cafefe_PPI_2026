
package validation;

import entity.Caracteristica;
import entity.Producto;
import entity.ProductoCaracteristica;
import entity.TipoCaracteristica;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProductoCaracteristicaValidatorTest {

    private ProductoCaracteristica crearRelacionValida() {
        Producto producto = new Producto();
        producto.setIdProducto(UUID.randomUUID());

        TipoCaracteristica tipo = new TipoCaracteristica();
        tipo.setActivo(true);

        Caracteristica caracteristica = new Caracteristica();
        caracteristica.setIdCaracteristica(UUID.randomUUID());
        caracteristica.setActivo(true);
        caracteristica.setIdTipoCaracteristica(tipo);

        ProductoCaracteristica relacion = new ProductoCaracteristica();
        relacion.setIdProducto(producto);
        relacion.setIdCaracteristica(caracteristica);
        relacion.setValor("600 ml");

        return relacion;
    }

    @Test
    void relacionValidaDebeSerAceptada() {
        assertDoesNotThrow(() ->
                ProductoCaracteristicaValidator.validar(
                        crearRelacionValida()));
    }

    @Test
    void relacionNulaDebeSerRechazada() {
        assertThrows(IllegalArgumentException.class,
                () -> ProductoCaracteristicaValidator.validar(null));
    }

    @Test
    void productoNuloDebeSerRechazado() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.setIdProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoCaracteristicaValidator.validar(relacion));
    }

    @Test
    void productoSinIdentificadorDebeSerRechazado() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.getIdProducto().setIdProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoCaracteristicaValidator.validar(relacion));
    }

    @Test
    void caracteristicaNulaDebeSerRechazada() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.setIdCaracteristica(null);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoCaracteristicaValidator.validar(relacion));
    }

    @Test
    void caracteristicaSinIdentificadorDebeSerRechazada() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.getIdCaracteristica().setIdCaracteristica(null);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoCaracteristicaValidator.validar(relacion));
    }

    @Test
    void caracteristicaInactivaDebeSerRechazada() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.getIdCaracteristica().setActivo(false);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoCaracteristicaValidator.validar(relacion));
    }

    @Test
    void caracteristicaSinEstadoDebeSerRechazada() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.getIdCaracteristica().setActivo(null);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoCaracteristicaValidator.validar(relacion));
    }

    @Test
    void valorNuloDebeSerRechazado() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.setValor(null);

        assertThrows(IllegalArgumentException.class,
                () -> ProductoCaracteristicaValidator.validar(relacion));
    }

    @Test
    void valorEnBlancoDebeSerRechazado() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.setValor("   ");

        assertThrows(IllegalArgumentException.class,
                () -> ProductoCaracteristicaValidator.validar(relacion));
    }

    @Test
    void valorConFormatoValidoDebeSerAceptado() {
        ProductoCaracteristica relacion = crearRelacionValida();

        relacion.getIdCaracteristica()
                .getIdTipoCaracteristica()
                .setExpresionRegular("^[0-9]+ ml$");

        assertDoesNotThrow(() ->
                ProductoCaracteristicaValidator.validar(relacion));
    }

    @Test
    void valorConFormatoInvalidoDebeSerRechazado() {
        ProductoCaracteristica relacion = crearRelacionValida();

        relacion.getIdCaracteristica()
                .getIdTipoCaracteristica()
                .setExpresionRegular("^[0-9]+$");

        assertThrows(IllegalArgumentException.class,
                () -> ProductoCaracteristicaValidator.validar(relacion));
    }

    @Test
    void expresionRegularInvalidaDebeSerRechazada() {
        ProductoCaracteristica relacion = crearRelacionValida();

        relacion.getIdCaracteristica()
                .getIdTipoCaracteristica()
                .setExpresionRegular("[");

        assertThrows(IllegalArgumentException.class,
                () -> ProductoCaracteristicaValidator.validar(relacion));
    }

    @Test
    void sinExpresionRegularDebeAceptarValor() {
        ProductoCaracteristica relacion = crearRelacionValida();

        relacion.getIdCaracteristica()
                .getIdTipoCaracteristica()
                .setExpresionRegular(null);

        assertDoesNotThrow(() ->
                ProductoCaracteristicaValidator.validar(relacion));
    }

    @Test
    void sinTipoCaracteristicaDebeAceptarValor() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.getIdCaracteristica().setIdTipoCaracteristica(null);

        assertDoesNotThrow(() ->
                ProductoCaracteristicaValidator.validar(relacion));
    }
}
