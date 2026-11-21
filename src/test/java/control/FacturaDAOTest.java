package control;

import entity.Caja;
import entity.EmpleadoRol;
import entity.Factura;
import entity.FacturaOrdenProducto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.LockModeType;
import entity.Orden;
import entity.OrdenProducto;
import entity.Pago;
import java.math.BigDecimal;
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

    private Factura nueva() {
        Factura factura = new Factura(UUID.randomUUID());
        factura.setEstado("PENDIENTE");
        Caja caja = new Caja(UUID.randomUUID());
        caja.setActivo(true);
        EmpleadoRol empleado = new EmpleadoRol(UUID.randomUUID());
        empleado.setActivo(true);
        factura.setIdCaja(caja);
        factura.setIdEmpleadoRol(empleado);
        when(em.find(Caja.class, caja.getIdCaja())).thenReturn(caja);
        when(em.find(EmpleadoRol.class, empleado.getIdEmpleadoRol())).thenReturn(empleado);
        return factura;
    }

    private FacturaOrdenProducto detalle(Factura factura, long ocupados) {
        OrdenProducto op = new OrdenProducto(UUID.randomUUID());
        Orden orden = new Orden();
        orden.setIdOrden(UUID.randomUUID());
        op.setIdOrden(orden);
        when(em.find(OrdenProducto.class,op.getIdOrdenProducto())).thenReturn(op);
        when(em.find(Orden.class,orden.getIdOrden(),LockModeType.PESSIMISTIC_WRITE)).thenReturn(orden);
        when(em.find(OrdenProducto.class, op.getIdOrdenProducto(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(op);
        TypedQuery<Long> consulta = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(startsWith("SELECT COUNT(f) FROM FacturaOrdenProducto"), eq(Long.class)))
                .thenReturn(consulta);
        when(consulta.getSingleResult()).thenReturn(ocupados);
        FacturaOrdenProducto detalle = new FacturaOrdenProducto();
        detalle.setIdOrdenProducto(op);
        detalle.setPrecio(new BigDecimal("7.25"));
        return detalle;
    }

    @Test
    void guardaCabeceraYDetalleTrasBloquearFilaDeOrden() {
        Factura factura = nueva();
        FacturaOrdenProducto detalle = detalle(factura, 0);
        dao.crearFacturaCompleta(factura, List.of(detalle));
        assertSame(factura, detalle.getIdFactura());
        assertNotNull(detalle.getIdFacturaOrdenProducto());
        InOrder orden = inOrder(em);
        orden.verify(em).find(OrdenProducto.class, detalle.getIdOrdenProducto().getIdOrdenProducto(),
                LockModeType.PESSIMISTIC_WRITE);
        orden.verify(em).persist(same(factura));
        orden.verify(em).persist(same(detalle));
        orden.verify(em).flush();
    }

    @Test
    void noPersisteSiLaFilaYaEstaFacturada() {
        Factura factura = nueva();
        var detalle = detalle(factura, 1);
        assertThrows(FacturaDAO.Conflicto.class, () -> dao.crearFacturaCompleta(factura, List.of(detalle)));
        verify(em, never()).persist(any());
    }

    @Test
    void noPersisteSinDetalles() {
        Factura factura = nueva();
        assertThrows(FacturaDAO.DatosInvalidos.class, () -> dao.crearFacturaCompleta(factura, List.of()));
        verify(em, never()).persist(any());
    }

    @Test
    void modificarConAbonosNoEscribe() {
        Factura factura = new Factura(UUID.randomUUID());
        factura.setEstado("PENDIENTE");
        when(em.find(Factura.class, factura.getIdFactura(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(factura);
        TypedQuery<BigDecimal> abonos = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createNamedQuery("PagoDetalle.totalAbonadoFactura", BigDecimal.class)).thenReturn(abonos);
        when(abonos.getSingleResult()).thenReturn(BigDecimal.ONE);
        assertThrows(FacturaDAO.Conflicto.class, () -> dao.modificarFacturaCompleta(factura, List.of()));
        verify(em, never()).remove(any());
        verify(em, never()).persist(any());
    }

    @Test
    void eliminarConPagoAnuladoNoTocaLosDetalles() {
        Factura factura = new Factura(UUID.randomUUID());
        factura.setEstado("PENDIENTE");
        when(em.find(Factura.class, factura.getIdFactura(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(factura);
        TypedQuery<Pago> pagos = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createNamedQuery("Pago.findByFactura", Pago.class)).thenReturn(pagos);
        Pago pago = new Pago();
        pago.setEstado("ANULADO");
        when(pagos.getResultList()).thenReturn(List.of(pago));
        assertThrows(FacturaDAO.Conflicto.class, () -> dao.eliminarFacturaCompleta(factura));
        verify(em, never()).remove(any());
    }

    @Test
    void eliminaDetallesYFacturaPendienteSinPagos() {
        Factura factura = new Factura(UUID.randomUUID());
        factura.setEstado("PENDIENTE");
        when(em.find(Factura.class, factura.getIdFactura(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(factura);
        TypedQuery<Pago> pagos = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createNamedQuery("Pago.findByFactura", Pago.class)).thenReturn(pagos);
        when(pagos.getResultList()).thenReturn(List.of());
        TypedQuery<FacturaOrdenProducto> detalles = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(startsWith("SELECT fop"), eq(FacturaOrdenProducto.class))).thenReturn(detalles);
        FacturaOrdenProducto detalle = new FacturaOrdenProducto();
        when(detalles.getResultList()).thenReturn(List.of(detalle));
        dao.eliminarFacturaCompleta(factura);
        InOrder orden = inOrder(em);
        orden.verify(em).remove(detalle);
        orden.verify(em).remove(factura);
        orden.verify(em).flush();
    }

    @Test
    void noEliminaSiNoExiste() {
        assertThrows(FacturaDAO.NoEncontrada.class,
                () -> dao.eliminarFacturaCompleta(new Factura(UUID.randomUUID())));
        verify(em, never()).remove(any());
    }
}
