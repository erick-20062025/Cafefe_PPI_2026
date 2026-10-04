package control;

import entity.EmpleadoRol;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmpleadoRolDAOTest {
    @Mock private EntityManager em;
    @InjectMocks private EmpleadoRolDAO dao;

    @Test
    void debeBuscarRelacionesYDevolverListaVacia() {
        UUID id = UUID.randomUUID();
        TypedQuery<EmpleadoRol> query = mock(TypedQuery.class);
        String jpql = "SELECT e FROM EmpleadoRol e WHERE e.idEmpleado.idEmpleado = :idEmpleado";
        when(em.createQuery(jpql, EmpleadoRol.class)).thenReturn(query);
        when(query.setParameter("idEmpleado", id)).thenReturn(query);
        List<EmpleadoRol> registros = List.of(new EmpleadoRol(), new EmpleadoRol());
        when(query.getResultList()).thenReturn(registros, List.of());
        assertSame(registros, dao.findByIdEmpleado(id));
        assertTrue(dao.findByIdEmpleado(id).isEmpty());
        verify(em, times(2)).createQuery(jpql, EmpleadoRol.class);
        verify(query, times(2)).setParameter("idEmpleado", id);
        verify(query, times(2)).getResultList();
    }

    @Test
    void debeDistinguirRelacionExistenteDeAusente() {
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery("SELECT COUNT(e) FROM EmpleadoRol e WHERE e.idEmpleado.idEmpleado = :idEmpleado AND e.idRol.idRol = :idRol", Long.class)).thenReturn(query);
        when(query.setParameter("idEmpleado", primero)).thenReturn(query);
        when(query.setParameter("idRol", segundo)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L, 1L, 3L);
        assertFalse(dao.existeRelacion(primero, segundo));
        assertTrue(dao.existeRelacion(primero, segundo));
        assertTrue(dao.existeRelacion(primero, segundo));
        verify(query, times(3)).setParameter("idEmpleado", primero);
        verify(query, times(3)).setParameter("idRol", segundo);
        verify(query, times(3)).getSingleResult();
    }

    @Test
    void debeDevolverConteoCeroYPositivo() {
        UUID id = UUID.randomUUID();
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery("SELECT COUNT(e) FROM EmpleadoRol e WHERE e.idEmpleado.idEmpleado = :idEmpleado", Long.class)).thenReturn(query);
        when(query.setParameter("idEmpleado", id)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L, 4L);
        assertEquals(0L, dao.countByIdEmpleado(id));
        assertEquals(4L, dao.countByIdEmpleado(id));
        verify(query, times(2)).setParameter("idEmpleado", id);
        verify(query, times(2)).getSingleResult();
    }

    @Test
    void debeBuscarConLaEntidadYEntityManagerDelDAO() {
        UUID id = UUID.randomUUID();
        EmpleadoRol registro = new EmpleadoRol();
        when(em.find(EmpleadoRol.class, id)).thenReturn(registro);
        assertSame(registro, dao.findById(id));
        verify(em).find(EmpleadoRol.class, id);
    }
}
