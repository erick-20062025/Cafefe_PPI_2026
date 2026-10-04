package control;

import entity.Caja;
import entity.EmpleadoRol;
import entity.Factura;
import entity.FacturaOrdenProducto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.Query;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FacturaDAOTest {
    @Mock private EntityManager em;
    @InjectMocks private FacturaDAO dao;

    @Test
    void debeBuscarConLaEntidadYEntityManagerDelDAO() {
        UUID id = UUID.randomUUID();
        Factura registro = new Factura();
        when(em.find(Factura.class, id)).thenReturn(registro);
        assertSame(registro, dao.findById(id));
        verify(em).find(Factura.class, id);
    }

    @Test
    void debeConsultarCajasActivas() {
        TypedQuery<Caja> query = mock(TypedQuery.class);
        String jpql = "SELECT c FROM Caja c WHERE c.activo = true";
        when(em.createQuery(jpql, Caja.class)).thenReturn(query);
        List<Caja> registros = List.of(new Caja(), new Caja());
        when(query.getResultList()).thenReturn(registros, List.of());
        assertSame(registros, dao.findCajasActivas());
        assertTrue(dao.findCajasActivas().isEmpty());
        verify(em, times(2)).createQuery(jpql, Caja.class);
        verify(query, times(2)).getResultList();
    }

    @Test
    void debeConsultarEmpleadosRolActivos() {
        TypedQuery<EmpleadoRol> query = mock(TypedQuery.class);
        String jpql = "SELECT er FROM EmpleadoRol er WHERE er.activo = true";
        when(em.createQuery(jpql, EmpleadoRol.class)).thenReturn(query);
        List<EmpleadoRol> registros = List.of(new EmpleadoRol(), new EmpleadoRol());
        when(query.getResultList()).thenReturn(registros, List.of());
        assertSame(registros, dao.findEmpleadosRolActivos());
        assertTrue(dao.findEmpleadosRolActivos().isEmpty());
        verify(em, times(2)).createQuery(jpql, EmpleadoRol.class);
        verify(query, times(2)).getResultList();
    }

    @Test
    void debeBuscarCajaPorIdODevolverNull() {
        UUID id = UUID.randomUUID();
        Caja registro = new Caja();
        when(em.find(Caja.class, id)).thenReturn(registro).thenReturn(null);
        assertSame(registro, dao.findCajaById(id));
        assertNull(dao.findCajaById(id));
        verify(em, times(2)).find(Caja.class, id);
    }

    @Test
    void debeBuscarEmpleadoRolPorIdODevolverNull() {
        UUID id = UUID.randomUUID();
        EmpleadoRol registro = new EmpleadoRol();
        when(em.find(EmpleadoRol.class, id)).thenReturn(registro).thenReturn(null);
        assertSame(registro, dao.findEmpleadoRolById(id));
        assertNull(dao.findEmpleadoRolById(id));
        verify(em, times(2)).find(EmpleadoRol.class, id);
    }

    @Test
    void debeCrearFacturaYAsociarTodosLosDetallesAntesDePersistir() {
        Factura cabecera = new Factura();
        FacturaOrdenProducto primero = new FacturaOrdenProducto();
        FacturaOrdenProducto segundo = new FacturaOrdenProducto();
        doAnswer(invocacion -> {
            Object registro = invocacion.getArgument(0);
            if (registro instanceof FacturaOrdenProducto detalle) {
                assertSame(cabecera, detalle.getIdFactura());
            }
            return null;
        }).when(em).persist(any());
        dao.crearFacturaCompleta(cabecera, List.of(primero, segundo));
        InOrder orden = inOrder(em);
        orden.verify(em).persist(same(cabecera));
        orden.verify(em).persist(same(primero));
        orden.verify(em).persist(same(segundo));
        verifyNoMoreInteractions(em);
    }

    @Test
    void debeCrearFacturaSinDetalles() {
        Factura cabecera = new Factura();
        dao.crearFacturaCompleta(cabecera, List.of());
        verify(em).persist(same(cabecera));
        verifyNoMoreInteractions(em);
    }

    @Test
    void debeEliminarDetallesAntesDeEliminarFacturaAdministrada() {
        UUID id = UUID.randomUUID();
        Factura factura = new Factura();
        factura.setIdFactura(id);
        Query query = mock(Query.class);
        String jpql = "DELETE FROM FacturaOrdenProducto fop WHERE fop.idFactura.idFactura = :idFactura";
        when(em.createQuery(jpql)).thenReturn(query);
        when(query.setParameter("idFactura", id)).thenReturn(query);
        Factura administrada = new Factura();
        when(em.find(Factura.class, id)).thenReturn(administrada);
        dao.eliminarFacturaCompleta(factura);
        InOrder orden = inOrder(em, query);
        orden.verify(em).createQuery(jpql);
        orden.verify(query).setParameter("idFactura", id);
        orden.verify(query).executeUpdate();
        orden.verify(em).find(Factura.class, id);
        orden.verify(em).remove(same(administrada));
        verifyNoMoreInteractions(em, query);
    }

    @Test
    void debeEliminarDetallesAunqueFacturaNoExista() {
        UUID id = UUID.randomUUID();
        Factura factura = new Factura();
        factura.setIdFactura(id);
        Query query = mock(Query.class);
        String jpql = "DELETE FROM FacturaOrdenProducto fop WHERE fop.idFactura.idFactura = :idFactura";
        when(em.createQuery(jpql)).thenReturn(query);
        when(query.setParameter("idFactura", id)).thenReturn(query);
        when(em.find(Factura.class, id)).thenReturn(null);
        dao.eliminarFacturaCompleta(factura);
        InOrder orden = inOrder(em, query);
        orden.verify(em).createQuery(jpql);
        orden.verify(query).setParameter("idFactura", id);
        orden.verify(query).executeUpdate();
        orden.verify(em).find(Factura.class, id);
        verifyNoMoreInteractions(em, query);
    }
}
