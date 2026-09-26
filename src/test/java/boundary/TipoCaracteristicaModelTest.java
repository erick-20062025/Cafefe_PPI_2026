package boundary;

import control.TipoCaracteristicaDAO;
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
public class TipoCaracteristicaModelTest {

    @Mock
    private TipoCaracteristicaDAO tipoCaracteristicaDAO;

    @InjectMocks
    private TipoCaracteristicaModel tipoCaracteristicaModel;

    @Test
    void debeCargarTiposCaracteristicaDesdeDAO() {
        TipoCaracteristica tamano = new TipoCaracteristica();
        tamano.setNombre("Tamaño");

        TipoCaracteristica color = new TipoCaracteristica();
        color.setNombre("Color");

        List<TipoCaracteristica> tiposCaracteristica = List.of(tamano, color);

        when(tipoCaracteristicaDAO.findAll()).thenReturn(tiposCaracteristica);

        tipoCaracteristicaModel.cargarDatos();

        assertEquals(2, tipoCaracteristicaModel.getModelo().size());
        assertEquals("Tamaño", tipoCaracteristicaModel.getModelo().get(0).getNombre());
        assertEquals("Color", tipoCaracteristicaModel.getModelo().get(1).getNombre());

        verify(tipoCaracteristicaDAO).findAll();
    }
}