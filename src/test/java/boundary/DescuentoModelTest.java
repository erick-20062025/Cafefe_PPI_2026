package boundary;

import control.DescuentoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
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
public class DescuentoModelTest {

    @Mock
    private DescuentoDAO descuentoDAO;

    @Mock
    private TipoDescuentoDAO tipoDescuentoDAO;

    @InjectMocks
    private DescuentoModel descuentoModel;

    @Test
    void debeCargarDescuentosDesdeDAO() {

        Descuento estudiantes = new Descuento();
        estudiantes.setNombre("Descuento estudiantes");

        Descuento empleados = new Descuento();
        empleados.setNombre("Descuento empleados");

        List<Descuento> descuentos =
                List.of(estudiantes, empleados);

        when(descuentoDAO.findAll()).thenReturn(descuentos);

        descuentoModel.cargarDatos();

        assertEquals(2, descuentoModel.getModelo().size());

        assertEquals(
                "Descuento estudiantes",
                descuentoModel.getModelo().get(0).getNombre()
        );

        assertEquals(
                "Descuento empleados",
                descuentoModel.getModelo().get(1).getNombre()
        );

        verify(descuentoDAO).findAll();
    }
}