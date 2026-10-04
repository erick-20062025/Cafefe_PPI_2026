package control;

import entity.TipoCaracteristica;
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
public class TipoCaracteristicaDAOTest {
    @Mock private EntityManager em;
    @InjectMocks private TipoCaracteristicaDAO dao;

    @Test
    void debeBuscarConLaEntidadYEntityManagerDelDAO() {
        UUID id = UUID.randomUUID();
        TipoCaracteristica registro = new TipoCaracteristica();
        when(em.find(TipoCaracteristica.class, id)).thenReturn(registro);
        assertSame(registro, dao.findById(id));
        verify(em).find(TipoCaracteristica.class, id);
    }
}
