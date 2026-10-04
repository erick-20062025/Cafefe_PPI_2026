package control;

import entity.FacturaOrdenProducto;
import entity.OrdenProducto;
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
public class FacturaOrdenProductoDAOTest {
    @Mock private EntityManager em;
    @InjectMocks private FacturaOrdenProductoDAO dao;

    @Test
    void debeBuscarDetallesDeFactura() {
        UUID id = UUID.randomUUID();
        TypedQuery<FacturaOrdenProducto> query = mock(TypedQuery.class);
        String jpql = "SELECT fop FROM FacturaOrdenProducto fop JOIN FETCH fop.idOrdenProducto op JOIN FETCH op.idProducto WHERE fop.idFactura.idFactura = :idFactura";
        when(em.createQuery(jpql, FacturaOrdenProducto.class)).thenReturn(query);
        when(query.setParameter("idFactura", id)).thenReturn(query);
        List<FacturaOrdenProducto> registros = List.of(new FacturaOrdenProducto(), new FacturaOrdenProducto());
        when(query.getResultList()).thenReturn(registros, List.of());
        assertSame(registros, dao.findByIdFactura(id));
        assertTrue(dao.findByIdFactura(id).isEmpty());
        verify(em, times(2)).createQuery(jpql, FacturaOrdenProducto.class);
        verify(query, times(2)).setParameter("idFactura", id);
        verify(query, times(2)).getResultList();
    }

    @Test
    void debeBuscarProductosDeOrden() {
        UUID id = UUID.randomUUID();
        TypedQuery<OrdenProducto> query = mock(TypedQuery.class);
        String jpql = "SELECT op FROM OrdenProducto op JOIN FETCH op.idProducto WHERE op.idOrden.idOrden = :idOrden";
        when(em.createQuery(jpql, OrdenProducto.class)).thenReturn(query);
        when(query.setParameter("idOrden", id)).thenReturn(query);
        List<OrdenProducto> registros = List.of(new OrdenProducto(), new OrdenProducto());
        when(query.getResultList()).thenReturn(registros, List.of());
        assertSame(registros, dao.findProductosByOrden(id));
        assertTrue(dao.findProductosByOrden(id).isEmpty());
        verify(em, times(2)).createQuery(jpql, OrdenProducto.class);
        verify(query, times(2)).setParameter("idOrden", id);
        verify(query, times(2)).getResultList();
    }

    @Test
    void debeDistinguirRelacionExistenteDeAusente() {
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery("SELECT COUNT(fop) FROM FacturaOrdenProducto fop WHERE fop.idFactura.idFactura = :idFactura AND fop.idOrdenProducto.idOrdenProducto = :idOrdenProducto", Long.class)).thenReturn(query);
        when(query.setParameter("idFactura", primero)).thenReturn(query);
        when(query.setParameter("idOrdenProducto", segundo)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L, 1L, 3L);
        assertFalse(dao.existeRelacion(primero, segundo));
        assertTrue(dao.existeRelacion(primero, segundo));
        assertTrue(dao.existeRelacion(primero, segundo));
        verify(query, times(3)).setParameter("idFactura", primero);
        verify(query, times(3)).setParameter("idOrdenProducto", segundo);
        verify(query, times(3)).getSingleResult();
    }

    @Test
    void debePersistirDetalle() {
        FacturaOrdenProducto detalle = new FacturaOrdenProducto();
        dao.agregarProductoAFactura(detalle);
        verify(em).persist(same(detalle));
    }

    @Test
    void debeBuscarConLaEntidadYEntityManagerDelDAO() {
        UUID id = UUID.randomUUID();
        FacturaOrdenProducto registro = new FacturaOrdenProducto();
        when(em.find(FacturaOrdenProducto.class, id)).thenReturn(registro);
        assertSame(registro, dao.findById(id));
        verify(em).find(FacturaOrdenProducto.class, id);
    }
}
