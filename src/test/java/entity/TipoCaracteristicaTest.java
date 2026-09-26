package entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TipoCaracteristicaTest {

    @Test
    void debeAsignarYObtenerNombre() {
        TipoCaracteristica tipoCaracteristica = new TipoCaracteristica();
        tipoCaracteristica.setNombre("Tamaño");

        assertEquals("Tamaño", tipoCaracteristica.getNombre());
    }
}