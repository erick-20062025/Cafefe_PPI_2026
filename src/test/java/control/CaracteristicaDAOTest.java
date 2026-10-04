package control;

import entity.Caracteristica;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CaracteristicaDAOTest {
    @Mock private EntityManager em;
    @InjectMocks private CaracteristicaDAO dao;

    @Test
    void debeBuscarConLaEntidadYEntityManagerDelDAO() {
        UUID id = UUID.randomUUID();
        Caracteristica registro = new Caracteristica();
        when(em.find(Caracteristica.class, id)).thenReturn(registro);
        assertSame(registro, dao.findById(id));
        verify(em).find(Caracteristica.class, id);
    }
}
