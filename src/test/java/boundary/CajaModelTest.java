
package boundary;

import control.CajaDAO;
import entity.Caja;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

        when(cajaDAO.findAll())
                .thenReturn(List.of(caja1, caja2));

        cajaModel.cargarDatos();

        assertEquals(2, cajaModel.getModelo().size());
        verify(cajaDAO).findAll();
    }

    @Test
    void nuevaCajaDebeTenerUUIDYEstarActiva() {
        cajaModel.btnNuevoHandler(null);

        Caja caja = cajaModel.getRegistro();

        assertNotNull(caja);
        assertNotNull(caja.getIdCaja());
        assertTrue(caja.getActivo());
        assertTrue(cajaModel.isNuevo());
    }

    @Test
    void cancelarNuevaCajaDebeLimpiarFormulario() {
        cajaModel.btnNuevoHandler(null);

        cajaModel.btnCancelarHandler(null);

        assertNull(cajaModel.getRegistro());
        assertFalse(cajaModel.isNuevo());
    }
}
