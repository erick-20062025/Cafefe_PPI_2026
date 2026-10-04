package control;

import entity.Descuento;
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
public class DescuentoDAOTest {
    @Mock private EntityManager em;
    @InjectMocks private DescuentoDAO dao;

    @Test
    void debeBuscarConLaEntidadYEntityManagerDelDAO() {
        UUID id = UUID.randomUUID();
        Descuento registro = new Descuento();
        when(em.find(Descuento.class, id)).thenReturn(registro);
        assertSame(registro, dao.findById(id));
        verify(em).find(Descuento.class, id);
    }
}
