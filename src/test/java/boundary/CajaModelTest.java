/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary;

import control.CajaDAO;
import entity.Caja;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 *
 * @author erick
 */
@ExtendWith(MockitoExtension.class)
public class CajaModelTest {

    @Mock
    private CajaDAO cajaDAO;

    @InjectMocks
    private CajaModel cajaModel;

    @Test
    void debeCargarCajasDesdeDAO() {

        Caja caja1 = new Caja();
        caja1.setNombre("Caja Principal");

        Caja caja2 = new Caja();
        caja2.setNombre("Caja Secundaria");

        List<Caja> cajas = List.of(caja1, caja2);

        when(cajaDAO.findAll()).thenReturn(cajas);

        cajaModel.cargarDatos();

        assertEquals(2, cajaModel.getModelo().size());
        assertEquals("Caja Principal",
                cajaModel.getModelo().get(0).getNombre());
        assertEquals("Caja Secundaria",
                cajaModel.getModelo().get(1).getNombre());

        verify(cajaDAO).findAll();
    }

    @Test
    void debeCrearCajaNuevaConUuidYActiva() {

        Caja caja = cajaModel.crearNuevoRegistro();

        assertNotNull(caja);
        assertNotNull(caja.getIdCaja());
        assertInstanceOf(UUID.class, caja.getIdCaja());
        assertTrue(caja.getActivo());
    }
}
