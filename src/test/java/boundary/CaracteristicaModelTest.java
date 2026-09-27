package boundary;

import control.CaracteristicaDAO;
import control.TipoCaracteristicaDAO;
import entity.Caracteristica;
import entity.TipoCaracteristica;
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
public class CaracteristicaModelTest {

    @Mock
    private CaracteristicaDAO caracteristicaDAO;

    @Mock
    private TipoCaracteristicaDAO tipoCaracteristicaDAO;

    @InjectMocks
    private CaracteristicaModel caracteristicaModel;

    @Test
    void debeCargarCaracteristicasDesdeDAO() {

        Caracteristica kilogramos = new Caracteristica();
        kilogramos.setNombre("Kilogramos");

        Caracteristica libras = new Caracteristica();
        libras.setNombre("Libras");

        List<Caracteristica> caracteristicas =
                List.of(kilogramos, libras);

        when(caracteristicaDAO.findAll())
                .thenReturn(caracteristicas);

        caracteristicaModel.cargarDatos();

        assertEquals(2, caracteristicaModel.getModelo().size());
        assertEquals("Kilogramos",
                caracteristicaModel.getModelo().get(0).getNombre());
        assertEquals("Libras",
                caracteristicaModel.getModelo().get(1).getNombre());

        verify(caracteristicaDAO).findAll();
    }
}   