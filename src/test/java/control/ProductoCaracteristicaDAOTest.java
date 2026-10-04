package control;

import entity.ProductoCaracteristica;
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
public class ProductoCaracteristicaDAOTest {
    @Mock private EntityManager em;
    @InjectMocks private ProductoCaracteristicaDAO dao;

    @Test
    void debeBuscarRelacionesYDevolverListaVacia() {
        UUID id = UUID.randomUUID();
        TypedQuery<ProductoCaracteristica> query = mock(TypedQuery.class);
        String jpql = "SELECT pc FROM ProductoCaracteristica pc WHERE pc.idProducto.idProducto = :idProducto";
        when(em.createQuery(jpql, ProductoCaracteristica.class)).thenReturn(query);
        when(query.setParameter("idProducto", id)).thenReturn(query);
        List<ProductoCaracteristica> registros = List.of(new ProductoCaracteristica(), new ProductoCaracteristica());
        when(query.getResultList()).thenReturn(registros, List.of());
        assertSame(registros, dao.findByIdProducto(id));
        assertTrue(dao.findByIdProducto(id).isEmpty());
        verify(em, times(2)).createQuery(jpql, ProductoCaracteristica.class);
        verify(query, times(2)).setParameter("idProducto", id);
        verify(query, times(2)).getResultList();
    }

    @Test
    void debeDistinguirRelacionExistenteDeAusente() {
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery("SELECT COUNT(pc) FROM ProductoCaracteristica pc WHERE pc.idProducto.idProducto = :idProducto AND pc.idCaracteristica.idCaracteristica = :idCaracteristica", Long.class)).thenReturn(query);
        when(query.setParameter("idProducto", primero)).thenReturn(query);
        when(query.setParameter("idCaracteristica", segundo)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L, 1L, 3L);
        assertFalse(dao.existeRelacion(primero, segundo));
        assertTrue(dao.existeRelacion(primero, segundo));
        assertTrue(dao.existeRelacion(primero, segundo));
        verify(query, times(3)).setParameter("idProducto", primero);
        verify(query, times(3)).setParameter("idCaracteristica", segundo);
        verify(query, times(3)).getSingleResult();
    }

    @Test
    void debeBuscarConLaEntidadYEntityManagerDelDAO() {
        UUID id = UUID.randomUUID();
        ProductoCaracteristica registro = new ProductoCaracteristica();
        when(em.find(ProductoCaracteristica.class, id)).thenReturn(registro);
        assertSame(registro, dao.findById(id));
        verify(em).find(ProductoCaracteristica.class, id);
    }
}
