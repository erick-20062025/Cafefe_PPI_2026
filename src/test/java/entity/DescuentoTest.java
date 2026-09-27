package entity;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DescuentoTest {

    @Test
    void debeAsignarYObtenerNombre() {

        Descuento descuento = new Descuento();

        descuento.setNombre("Descuento estudiantes");

        assertEquals(
                "Descuento estudiantes",
                descuento.getNombre()
        );
    }

    @Test
    void debeAsignarTipoDescuento() {

        UUID idTipoDescuento = UUID.randomUUID();

        Descuento descuento = new Descuento();

        descuento.setIdTipoDescuento(idTipoDescuento);

        assertEquals(
                idTipoDescuento,
                descuento.getIdTipoDescuento()
        );
    }
}