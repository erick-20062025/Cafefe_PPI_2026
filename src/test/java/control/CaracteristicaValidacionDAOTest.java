
package control;

import entity.Caracteristica;
import entity.TipoCaracteristica;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CaracteristicaValidacionDAOTest {

    @Mock
    private EntityManager em;

    private CaracteristicaDAO dao;

    @BeforeEach
    void preparar() throws Exception {
        dao = new CaracteristicaDAO();

        Field campo = CaracteristicaDAO.class.getDeclaredField("em");
        campo.setAccessible(true);
        campo.set(dao, em);
    }

    private Caracteristica crearCaracteristicaValida() {
        TipoCaracteristica tipo = new TipoCaracteristica();
        tipo.setNombre("Tamaño");
        tipo.setActivo(true);

        Caracteristica caracteristica = new Caracteristica();
        caracteristica.setNombre("Grande");
        caracteristica.setActivo(true);
        caracteristica.setIdTipoCaracteristica(tipo);

        return caracteristica;
    }

    @Test
    void nombreInvalidoNoDebePersistir() {
        Caracteristica caracteristica = crearCaracteristicaValida();
        caracteristica.setNombre("   ");

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(caracteristica));

        verifyNoInteractions(em);
    }

    @Test
    void tipoInactivoNoDebePersistir() {
        Caracteristica caracteristica = crearCaracteristicaValida();
        caracteristica.getIdTipoCaracteristica().setActivo(false);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(caracteristica));

        verifyNoInteractions(em);
    }

    @Test
    void tipoNuloNoDebeModificar() {
        Caracteristica caracteristica = crearCaracteristicaValida();
        caracteristica.setIdTipoCaracteristica(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.modificar(caracteristica));

        verifyNoInteractions(em);
    }

    @Test
    void caracteristicaValidaDebePersistir() {
        Caracteristica caracteristica = crearCaracteristicaValida();

        dao.crear(caracteristica);

        verify(em).persist(caracteristica);
        verifyNoMoreInteractions(em);
    }

    @Test
    void caracteristicaValidaDebeModificar() {
        Caracteristica caracteristica = crearCaracteristicaValida();

        dao.modificar(caracteristica);

        verify(em).merge(caracteristica);
        verifyNoMoreInteractions(em);
    }
}


