package control;

import entity.DescuentoProducto;
import entity.EmpleadoRol;
import entity.Orden;
import entity.OrdenProducto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.Date;
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
public class OrdenDAOTest {
    @Mock private EntityManager em;
    @InjectMocks private OrdenDAO dao;

    @Test
    void debeBuscarConLaEntidadYEntityManagerDelDAO() {
        UUID id = UUID.randomUUID();
        Orden registro = new Orden();
        when(em.find(Orden.class, id)).thenReturn(registro);
        assertSame(registro, dao.findById(id));
        verify(em).find(Orden.class, id);
    }

    @Test
    void debeConsultarEmpleadosConRolCamareroYMantenerCompatibilidad() {
        TypedQuery<EmpleadoRol> query = mock(TypedQuery.class);
        String jpql = "SELECT er FROM EmpleadoRol er JOIN FETCH er.idEmpleado e JOIN FETCH er.idRol r WHERE LOWER(TRIM(r.nombre)) = :nombreRol ORDER BY e.nombre, e.apellido";
        when(em.createQuery(jpql, EmpleadoRol.class)).thenReturn(query);
        when(query.setParameter("nombreRol", "camarero")).thenReturn(query);
        List<EmpleadoRol> empleados = List.of(new EmpleadoRol());
        when(query.getResultList()).thenReturn(empleados, empleados, List.of());
        assertSame(empleados, dao.findCamarerosActivos());
        assertSame(empleados, dao.findEmpleadosRolActivos());
        assertTrue(dao.findCamarerosActivos().isEmpty());
        verify(em, times(3)).createQuery(jpql, EmpleadoRol.class);
        verify(query, times(3)).setParameter("nombreRol", "camarero");
        verify(query, times(3)).getResultList();
    }

    @Test
    void debeDevolverPrimerCamareroONullSiNoHayCoincidencias() {
        UUID id = UUID.randomUUID();
        TypedQuery<EmpleadoRol> query = mock(TypedQuery.class);
        String jpql = "SELECT er FROM EmpleadoRol er JOIN FETCH er.idEmpleado e JOIN FETCH er.idRol r WHERE er.idEmpleadoRol = :idEmpleadoRol AND er.activo = true AND e.activo = true AND r.activo = true AND LOWER(TRIM(r.nombre)) = :nombreRol";
        when(em.createQuery(jpql, EmpleadoRol.class)).thenReturn(query);
        when(query.setParameter("idEmpleadoRol", id)).thenReturn(query);
        when(query.setParameter("nombreRol", "camarero")).thenReturn(query);
        EmpleadoRol primero = new EmpleadoRol();
        when(query.getResultList()).thenReturn(List.of(primero, new EmpleadoRol()), List.of());
        assertSame(primero, dao.findCamareroActivoById(id));
        assertNull(dao.findCamareroActivoById(id));
        verify(query, times(2)).setParameter("idEmpleadoRol", id);
        verify(query, times(2)).setParameter("nombreRol", "camarero");
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
    void debePersistirProducto() {
        OrdenProducto producto = new OrdenProducto();
        dao.agregarProducto(producto);
        verify(em).persist(same(producto));
    }

    @Test
    void debeDevolverProductoAdministradoAlActualizar() {
        OrdenProducto original = new OrdenProducto();
        OrdenProducto administrado = new OrdenProducto();
        when(em.merge(original)).thenReturn(administrado);
        assertSame(administrado, dao.actualizarProducto(original));
        verify(em).merge(same(original));
    }

    @Test
    void noDebeInteractuarConPersistenciaAlEliminarNull() {
        dao.eliminarProducto(null);
        verifyNoInteractions(em);
    }

    @Test
    void debeEliminarProductoAdministradoSinMerge() {
        OrdenProducto producto = new OrdenProducto();
        when(em.contains(producto)).thenReturn(true);
        dao.eliminarProducto(producto);
        verify(em).contains(same(producto));
        verify(em).remove(same(producto));
        verifyNoMoreInteractions(em);
    }

    @Test
    void debeAdministrarProductoSeparadoAntesDeEliminar() {
        OrdenProducto original = new OrdenProducto();
        OrdenProducto administrado = new OrdenProducto();
        when(em.contains(original)).thenReturn(false);
        when(em.merge(original)).thenReturn(administrado);
        dao.eliminarProducto(original);
        InOrder orden = inOrder(em);
        orden.verify(em).contains(same(original));
        orden.verify(em).merge(same(original));
        orden.verify(em).remove(same(administrado));
        verifyNoMoreInteractions(em);
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
    void debeDevolverConteoCeroYPositivo() {
        UUID id = UUID.randomUUID();
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery("SELECT COUNT(op) FROM OrdenProducto op WHERE op.idOrden.idOrden = :idOrden", Long.class)).thenReturn(query);
        when(query.setParameter("idOrden", id)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L, 4L);
        assertEquals(0L, dao.countProductosByOrden(id));
        assertEquals(4L, dao.countProductosByOrden(id));
        verify(query, times(2)).setParameter("idOrden", id);
        verify(query, times(2)).getSingleResult();
    }

    @Test
    void debeConsultarDescuentosPorProductoYVigencia() {
        UUID id = UUID.randomUUID();
        Date fecha = new Date(1700000000000L);
        TypedQuery<DescuentoProducto> query = mock(TypedQuery.class);
        String jpql = "SELECT dp FROM DescuentoProducto dp JOIN FETCH dp.idProducto p JOIN FETCH dp.idDescuento d WHERE p.idProducto = :idProducto AND dp.fechaDesde <= :fechaOrden AND (dp.fechaHasta IS NULL      OR dp.fechaHasta >= :fechaOrden) AND d.fechaDesde <= :fechaOrden AND (d.fechaHasta IS NULL      OR d.fechaHasta >= :fechaOrden) ORDER BY d.nombre";
        when(em.createQuery(jpql, DescuentoProducto.class)).thenReturn(query);
        when(query.setParameter("idProducto", id)).thenReturn(query);
        when(query.setParameter("fechaOrden", fecha)).thenReturn(query);
        List<DescuentoProducto> descuentos = List.of(new DescuentoProducto());
        when(query.getResultList()).thenReturn(descuentos, List.of());
        assertSame(descuentos, dao.findDescuentosAplicables(id, fecha));
        assertTrue(dao.findDescuentosAplicables(id, fecha).isEmpty());
        verify(query, times(2)).setParameter("idProducto", id);
        verify(query, times(2)).setParameter("fechaOrden", fecha);
    }

    @Test
    void debeCrearOrdenYAsociarTodosLosDetallesAntesDePersistir() {
        Orden cabecera = new Orden();
        OrdenProducto primero = new OrdenProducto();
        OrdenProducto segundo = new OrdenProducto();
        doAnswer(invocacion -> {
            Object registro = invocacion.getArgument(0);
            if (registro instanceof OrdenProducto detalle) {
                assertSame(cabecera, detalle.getIdOrden());
            }
            return null;
        }).when(em).persist(any());
        dao.crearOrdenCompleta(cabecera, List.of(primero, segundo));
        InOrder orden = inOrder(em);
        orden.verify(em).persist(same(cabecera));
        orden.verify(em).persist(same(primero));
        orden.verify(em).persist(same(segundo));
        verifyNoMoreInteractions(em);
    }

    @Test
    void debeCrearOrdenSinDetalles() {
        Orden cabecera = new Orden();
        dao.crearOrdenCompleta(cabecera, List.of());
        verify(em).persist(same(cabecera));
        verifyNoMoreInteractions(em);
    }
}
