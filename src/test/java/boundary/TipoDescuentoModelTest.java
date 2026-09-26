package boundary;

import control.TipoDescuentoDAO;
import entity.TipoDescuento;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TipoDescuentoModelTest {

    @Mock
    private TipoDescuentoDAO tipoDescuentoDAO;

    @InjectMocks
    private TipoDescuentoModel tipoDescuentoModel;

    @Test
    void debeCargarTiposDescuentoDesdeDAO() {

        TipoDescuento estudiante = new TipoDescuento();
        estudiante.setNombre("Estudiante");

        TipoDescuento empleado = new TipoDescuento();
        empleado.setNombre("Empleado");

        List<TipoDescuento> descuentos = List.of(estudiante, empleado);

        when(tipoDescuentoDAO.findAll()).thenReturn(descuentos);

        tipoDescuentoModel.cargarDatos();

        assertEquals(2, tipoDescuentoModel.getModelo().size());
        assertEquals("Estudiante",
                tipoDescuentoModel.getModelo().get(0).getNombre());
        assertEquals("Empleado",
                tipoDescuentoModel.getModelo().get(1).getNombre());

        verify(tipoDescuentoDAO).findAll();
    }
}