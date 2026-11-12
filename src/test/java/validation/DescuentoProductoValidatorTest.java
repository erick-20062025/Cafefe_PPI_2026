
package validation;

import entity.Descuento;
import entity.DescuentoProducto;
import entity.Producto;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DescuentoProductoValidatorTest {

    private DescuentoProducto relacionValida() {
        Producto producto = new Producto(UUID.randomUUID());
        Descuento descuento = new Descuento(UUID.randomUUID());

        DescuentoProducto relacion = new DescuentoProducto();
        relacion.setIdDescuentoProducto(UUID.randomUUID());
        relacion.setIdProducto(producto);
        relacion.setIdDescuento(descuento);
        relacion.setValor(15);
        relacion.setFechaDesde(new Date());

        return relacion;
    }

    @Test
    void relacionValidaDebeSerAceptada() {
        assertDoesNotThrow(() ->
                DescuentoProductoValidator.validar(relacionValida()));
    }

    @Test
    void relacionNulaDebeSerRechazada() {
        assertThrows(IllegalArgumentException.class,
                () -> DescuentoProductoValidator.validar(null));
    }

    @Test
    void relacionSinIdDebeSerRechazada() {
        DescuentoProducto relacion = relacionValida();
        relacion.setIdDescuentoProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void relacionSinProductoDebeSerRechazada() {
        DescuentoProducto relacion = relacionValida();
        relacion.setIdProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void productoSinIdDebeSerRechazado() {
        DescuentoProducto relacion = relacionValida();
        relacion.getIdProducto().setIdProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void relacionSinDescuentoDebeSerRechazada() {
        DescuentoProducto relacion = relacionValida();
        relacion.setIdDescuento(null);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void descuentoSinIdDebeSerRechazado() {
        DescuentoProducto relacion = relacionValida();
        relacion.getIdDescuento().setIdDescuento(null);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void porcentajeNuloDebeSerRechazado() {
        DescuentoProducto relacion = relacionValida();
        relacion.setValor(null);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void porcentajeCeroDebeSerRechazado() {
        DescuentoProducto relacion = relacionValida();
        relacion.setValor(0);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void porcentajeNegativoDebeSerRechazado() {
        DescuentoProducto relacion = relacionValida();
        relacion.setValor(-10);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void porcentajeSuperiorACienDebeSerRechazado() {
        DescuentoProducto relacion = relacionValida();
        relacion.setValor(101);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void porcentajeCienDebeSerAceptado() {
        DescuentoProducto relacion = relacionValida();
        relacion.setValor(100);

        assertDoesNotThrow(() ->
                DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void porcentajeUnoDebeSerAceptado() {
        DescuentoProducto relacion = relacionValida();
        relacion.setValor(1);

        assertDoesNotThrow(() ->
                DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void fechaDesdeNulaDebeSerRechazada() {
        DescuentoProducto relacion = relacionValida();
        relacion.setFechaDesde(null);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void fechaHastaNulaDebeSerAceptada() {
        DescuentoProducto relacion = relacionValida();
        relacion.setFechaHasta(null);

        assertDoesNotThrow(() ->
                DescuentoProductoValidator.validar(relacion));
    }

    @Test
    void observacionesNulasDebenSerAceptadas() {
        DescuentoProducto relacion = relacionValida();
        relacion.setObservaciones(null);

        assertDoesNotThrow(() ->
                DescuentoProductoValidator.validar(relacion));
    }
}
