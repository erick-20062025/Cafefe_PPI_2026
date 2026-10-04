package control;

import entity.TipoProducto;
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
public class TipoProductoDAOTest {
    @Mock private EntityManager em;
    @InjectMocks private TipoProductoDAO dao;

    @Test
    void debeBuscarConLaEntidadYEntityManagerDelDAO() {
        UUID id = UUID.randomUUID();
        TipoProducto registro = new TipoProducto();
        when(em.find(TipoProducto.class, id)).thenReturn(registro);
        assertSame(registro, dao.findById(id));
        verify(em).find(TipoProducto.class, id);
    }
}
