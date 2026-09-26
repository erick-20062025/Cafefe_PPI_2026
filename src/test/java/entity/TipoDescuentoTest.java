package entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TipoDescuentoTest {

    @Test
    void debeAsignarYObtenerNombre() {
        TipoDescuento tipoDescuento = new TipoDescuento();

        tipoDescuento.setNombre("Descuento estudiante");

        assertEquals("Descuento estudiante", tipoDescuento.getNombre());
    }
}