package entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CaracteristicaTest {

    @Test
    void debeAsignarYObtenerNombre() {
        Caracteristica caracteristica = new Caracteristica();
        caracteristica.setNombre("Kilogramos");

        assertEquals("Kilogramos", caracteristica.getNombre());
    }

    @Test
    void debeAsignarTipoCaracteristica() {
        TipoCaracteristica tipoCaracteristica = new TipoCaracteristica();
        tipoCaracteristica.setNombre("Peso");

        Caracteristica caracteristica = new Caracteristica();
        caracteristica.setIdTipoCaracteristica(tipoCaracteristica);

        assertEquals(tipoCaracteristica,
                     caracteristica.getIdTipoCaracteristica());

        assertEquals("Peso",
                     caracteristica.getIdTipoCaracteristica().getNombre());
    }
}