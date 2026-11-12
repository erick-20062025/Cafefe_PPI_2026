package validation;

import entity.Descuento;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DescuentoValidatorTest {

    private Date crearFecha(int anio, int mes, int dia) {
        Calendar calendario = Calendar.getInstance();
        calendario.clear();
        calendario.set(anio, mes - 1, dia);
        return calendario.getTime();
    }

    private Descuento crearDescuentoValido() {
        Descuento descuento = new Descuento();
        descuento.setIdDescuento(UUID.randomUUID());
        descuento.setIdTipoDescuento(UUID.randomUUID());
        descuento.setNombre("Promoción especial");
        descuento.setFechaDesde(crearFecha(2026, 11, 11));
        descuento.setFechaHasta(crearFecha(2026, 11, 30));
        return descuento;
    }

    @Test
    void descuentoValidoDebeSerAceptado() {
        Descuento descuento = crearDescuentoValido();

        assertDoesNotThrow(
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoNuloDebeSerRechazado() {
        assertThrows(IllegalArgumentException.class,
                () -> DescuentoValidator.validar(null));
    }

    @Test
    void descuentoSinIdDebeSerRechazado() {
        Descuento descuento = crearDescuentoValido();
        descuento.setIdDescuento(null);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoSinTipoDebeSerRechazado() {
        Descuento descuento = crearDescuentoValido();
        descuento.setIdTipoDescuento(null);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoSinNombreDebeSerRechazado() {
        Descuento descuento = crearDescuentoValido();
        descuento.setNombre(null);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoConNombreVacioDebeSerRechazado() {
        Descuento descuento = crearDescuentoValido();
        descuento.setNombre("");

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoConMismoDiaYHorasDiferentesDebeSerAceptado() {
        Descuento descuento = crearDescuentoValido();

        Calendar calendario = Calendar.getInstance();

        calendario.set(2026, Calendar.NOVEMBER, 11, 18, 0, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        descuento.setFechaDesde(calendario.getTime());

        calendario.set(2026, Calendar.NOVEMBER, 11, 8, 0, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        descuento.setFechaHasta(calendario.getTime());

        assertDoesNotThrow(
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoConNombreEnBlancoDebeSerRechazado() {
        Descuento descuento = crearDescuentoValido();
        descuento.setNombre("   ");

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoConNombreMayorA155DebeSerRechazado() {
        Descuento descuento = crearDescuentoValido();
        descuento.setNombre("A".repeat(156));

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoConNombreDe155DebeSerAceptado() {
        Descuento descuento = crearDescuentoValido();
        descuento.setNombre("A".repeat(155));

        assertDoesNotThrow(
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoSinFechaDesdeDebeSerRechazado() {
        Descuento descuento = crearDescuentoValido();
        descuento.setFechaDesde(null);

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoSinFechaHastaDebeSerAceptado() {
        Descuento descuento = crearDescuentoValido();
        descuento.setFechaHasta(null);

        assertDoesNotThrow(
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoConFechaHastaAnteriorDebeSerRechazado() {
        Descuento descuento = crearDescuentoValido();
        descuento.setFechaHasta(crearFecha(2026, 11, 10));

        assertThrows(IllegalArgumentException.class,
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoConFechasIgualesDebeSerAceptado() {
        Descuento descuento = crearDescuentoValido();
        descuento.setFechaHasta(descuento.getFechaDesde());

        assertDoesNotThrow(
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoConObservacionesNulasDebeSerAceptado() {
        Descuento descuento = crearDescuentoValido();
        descuento.setObservaciones(null);

        assertDoesNotThrow(
                () -> DescuentoValidator.validar(descuento));
    }

    @Test
    void descuentoHistoricoDebeSerAceptadoPorValidador() {
        Descuento descuento = crearDescuentoValido();
        descuento.setFechaDesde(crearFecha(2020, 1, 1));
        descuento.setFechaHasta(crearFecha(2020, 12, 31));

        assertDoesNotThrow(
                () -> DescuentoValidator.validar(descuento));
    }
}
