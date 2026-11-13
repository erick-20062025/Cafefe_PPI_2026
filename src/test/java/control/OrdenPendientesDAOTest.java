package control;

import entity.Orden;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Verifica el contrato de la NamedQuery para órdenes pendientes. */
@ExtendWith(MockitoExtension.class)
class OrdenPendientesDAOTest {
    @Mock EntityManager em;
    @Mock TypedQuery<Orden> consulta;
    @InjectMocks OrdenDAO ordenDAO;

    @Test void pendientesUsaNamedQuerySinJPQLEnDao() {
        when(em.createNamedQuery("Orden.findPendientesPago", Orden.class)).thenReturn(consulta);
        when(consulta.getResultList()).thenReturn(List.of());
        assertEquals(List.of(), ordenDAO.findPendientesPago());
        verify(em).createNamedQuery("Orden.findPendientesPago", Orden.class);
        verifyNoMoreInteractions(em);
    }
}
