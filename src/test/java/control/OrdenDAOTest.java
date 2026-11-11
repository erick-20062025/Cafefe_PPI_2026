package control;

import entity.Descuento;
import entity.DescuentoProducto;
import entity.EmpleadoRol;
import entity.Orden;
import entity.OrdenProducto;
import entity.Producto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pruebas de NamedQueries, validaciones y descuento colectivo acumulativo. */
@ExtendWith(MockitoExtension.class)
class OrdenDAOTest {

    @Mock private EntityManager em;
    @InjectMocks private OrdenDAO dao;

    private static Orden orden() {
        Orden o = new Orden(UUID.randomUUID());
        o.setFechaCreacion(new Date(1794000000000L));
        return o;
    }

    private static OrdenProducto fila(Orden orden, String precio) {
        OrdenProducto f = new OrdenProducto(UUID.randomUUID());
        f.setIdOrden(orden);
        Producto producto = new Producto(UUID.randomUUID());
        producto.setActivo(true);
        f.setIdProducto(producto);
        f.setPrecio(new BigDecimal(precio));
        return f;
    }

    private static DescuentoProducto relacion(Producto producto, Descuento descuento, int porcentaje) {
        DescuentoProducto dp = new DescuentoProducto(UUID.randomUUID());
        dp.setIdProducto(producto);
        dp.setIdDescuento(descuento);
        dp.setValor(porcentaje);
        return dp;
    }

    private static Descuento promocion(String nombre) {
        Descuento d = new Descuento(UUID.randomUUID());
        d.setNombre(nombre);
        return d;
    }

    private static <T> TypedQuery<T> consulta(Class<T> tipo) {
        @SuppressWarnings("unchecked")
        TypedQuery<T> q = mock(TypedQuery.class);
        return q;
    }

    private TypedQuery<DescuentoProducto> configurarRelacionesOrden(
            List<DescuentoProducto> relaciones) {
        TypedQuery<DescuentoProducto> q = consulta(DescuentoProducto.class);
        when(em.createNamedQuery("DescuentoProducto.findAplicablesOrden", DescuentoProducto.class))
                .thenReturn(q);
        when(q.setParameter(eq("idOrden"), any(UUID.class))).thenReturn(q);
        when(q.setParameter(eq("inicioDia"), any(Date.class))).thenReturn(q);
        when(q.setParameter(eq("finDia"), any(Date.class))).thenReturn(q);
        when(q.getResultList()).thenReturn(relaciones);
        return q;
    }

    private TypedQuery<OrdenProducto> configurarFilas(UUID id, List<OrdenProducto> filas) {
        TypedQuery<OrdenProducto> q = consulta(OrdenProducto.class);
        when(em.createNamedQuery("OrdenProducto.findByOrdenConProducto", OrdenProducto.class))
                .thenReturn(q);
        when(q.setParameter("idOrden", id)).thenReturn(q);
        when(q.getResultList()).thenReturn(filas);
        return q;
    }

    private TypedQuery<Long> configurarConteoFactura(long conteo) {
        TypedQuery<Long> q = consulta(Long.class);
        when(em.createNamedQuery("OrdenProducto.countFacturado", Long.class))
                .thenReturn(q);
        when(q.setParameter(eq("idOrdenProducto"), any(UUID.class))).thenReturn(q);
        when(q.getSingleResult()).thenReturn(conteo);
        return q;
    }

    @Test
    void buscarCamarerosUsaNamedQueryYNoJPQLEnDAO() {
        TypedQuery<EmpleadoRol> q = consulta(EmpleadoRol.class);
        List<EmpleadoRol> activos = List.of(new EmpleadoRol());
        when(em.createNamedQuery("Orden.findCamarerosActivos", EmpleadoRol.class)).thenReturn(q);
        when(q.setParameter("nombreRol", "camarero")).thenReturn(q);
        when(q.getResultList()).thenReturn(activos);
        assertSame(activos, dao.findCamarerosActivos());
        assertSame(activos, dao.findEmpleadosRolActivos());
        verify(em, never()).createQuery(anyString(), eq(EmpleadoRol.class));
    }

    @Test
    void camareroInexistenteDevuelveNull() {
        TypedQuery<EmpleadoRol> q = consulta(EmpleadoRol.class);
        UUID id = UUID.randomUUID();
        when(em.createNamedQuery("Orden.findCamareroActivoById", EmpleadoRol.class)).thenReturn(q);
        when(q.setParameter("idEmpleadoRol", id)).thenReturn(q);
        when(q.setParameter("nombreRol", "camarero")).thenReturn(q);
        when(q.getResultList()).thenReturn(List.of());
        assertNull(dao.findCamareroActivoById(id));
        assertNull(dao.findCamareroActivoById(null));
    }

    @Test
    void noCreaOrdenSinFechaNiCamarero() {
        Orden o = new Orden(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> dao.crear(o));
        o.setFechaCreacion(new Date());
        assertThrows(IllegalArgumentException.class, () -> dao.crear(o));
        verifyNoInteractions(em);
    }

    @Test
    void permiteGuardarOrdenConFechaPasada() {
        Orden o = orden();
        o.setFechaCreacion(new Date(946684800000L)); // Año 2000.
        EmpleadoRol camarero = new EmpleadoRol(UUID.randomUUID());
        o.setIdEmpleadoRol(camarero);
        TypedQuery<EmpleadoRol> q = consulta(EmpleadoRol.class);
        when(em.createNamedQuery("Orden.findCamareroActivoById", EmpleadoRol.class)).thenReturn(q);
        when(q.setParameter("idEmpleadoRol", camarero.getIdEmpleadoRol())).thenReturn(q);
        when(q.setParameter("nombreRol", "camarero")).thenReturn(q);
        when(q.getResultList()).thenReturn(List.of(camarero));
        dao.crear(o);
        verify(em).persist(o);
        verify(em).flush();
    }

    @Test
    void rechazaOrdenConCamareroInactivo() {
        Orden o = orden();
        o.setIdEmpleadoRol(new EmpleadoRol(UUID.randomUUID()));
        TypedQuery<EmpleadoRol> q = consulta(EmpleadoRol.class);
        when(em.createNamedQuery("Orden.findCamareroActivoById", EmpleadoRol.class)).thenReturn(q);
        when(q.setParameter("idEmpleadoRol", o.getIdEmpleadoRol().getIdEmpleadoRol())).thenReturn(q);
        when(q.setParameter("nombreRol", "camarero")).thenReturn(q);
        when(q.getResultList()).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> dao.crear(o));
        verify(em, never()).persist(any());
    }

    @Test
    void buscaFilasConNamedQuery() {
        UUID id = UUID.randomUUID();
        List<OrdenProducto> filas = List.of(new OrdenProducto());
        configurarFilas(id, filas);
        assertSame(filas, dao.findProductosByOrden(id));
        assertThrows(IllegalArgumentException.class, () -> dao.findProductosByOrden(null));
    }

    @Test
    void promocionesNoAparecenSiLaOrdenNoTieneAsociaciones() {
        Orden o = orden();
        configurarRelacionesOrden(List.of());
        assertTrue(dao.findPromocionesAplicables(o.getIdOrden(), o.getFechaCreacion()).isEmpty());
    }

    @Test
    void promocionApareceUnaSolaVezAunqueApliqueADosProductos() {
        Orden o = orden();
        Descuento zeta = promocion("Zeta");
        Descuento alfa = promocion("Alfa");
        Producto p1 = new Producto(UUID.randomUUID());
        Producto p2 = new Producto(UUID.randomUUID());
        configurarRelacionesOrden(List.of(
                relacion(p1, zeta, 20), relacion(p2, zeta, 15),
                relacion(p1, alfa, 10)));
        List<Descuento> lista = dao.findPromocionesAplicables(o.getIdOrden(), o.getFechaCreacion());
        assertEquals(2, lista.size());
        assertSame(alfa, lista.get(0));
        assertSame(zeta, lista.get(1));
    }

    @Test
    void promocionesRequierenOrdenYFecha() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.findPromocionesAplicables(null, new Date()));
        assertThrows(IllegalArgumentException.class,
                () -> dao.findPromocionesAplicables(UUID.randomUUID(), null));
        verifyNoInteractions(em);
    }

    @Test
    void aplicaPromocionSoloAElegiblesYLaRepiteSobrePrecioActual() {
        Orden o = orden();
        OrdenProducto cafe = fila(o, "5.00");
        OrdenProducto pastel = fila(o, "4.00");
        OrdenProducto te = fila(o, "3.00");
        Descuento promo = promocion("PROMO20");
        when(em.find(Orden.class, o.getIdOrden(), LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(o);
        configurarFilas(o.getIdOrden(), List.of(cafe, pastel, te));
        configurarRelacionesOrden(List.of(
                relacion(cafe.getIdProducto(), promo, 20),
                relacion(pastel.getIdProducto(), promo, 20)));
        configurarConteoFactura(0);

        OrdenDAO.ResultadoPromocion uno = dao.aplicarPromocion(o.getIdOrden(), promo.getIdDescuento());
        assertEquals(2, uno.getProductosAfectados());
        assertEquals(new BigDecimal("1.80"), uno.getDescuentoTotal());
        assertEquals(new BigDecimal("4.00"), cafe.getPrecio());
        assertEquals(new BigDecimal("3.20"), pastel.getPrecio());
        assertEquals(new BigDecimal("3.00"), te.getPrecio());

        OrdenDAO.ResultadoPromocion dos = dao.aplicarPromocion(o.getIdOrden(), promo.getIdDescuento());
        assertEquals(2, dos.getProductosAfectados());
        assertEquals(new BigDecimal("1.44"), dos.getDescuentoTotal());
        assertEquals(new BigDecimal("3.20"), cafe.getPrecio());
        assertEquals(new BigDecimal("2.56"), pastel.getPrecio());
        assertEquals(new BigDecimal("3.00"), te.getPrecio());
        assertTrue(cafe.getObservaciones().contains("sobre $4.00"));
        verify(em, times(2)).flush();
        verify(em, times(4)).lock(any(OrdenProducto.class), eq(LockModeType.PESSIMISTIC_WRITE));
    }

    @Test
    void admiteDiferentePorcentajeParaCadaProductoDeLaMismaPromocion() {
        Orden o = orden();
        OrdenProducto cafe = fila(o, "10.00");
        OrdenProducto pastel = fila(o, "10.00");
        Descuento promo = promocion("Promoción combinada");
        when(em.find(Orden.class, o.getIdOrden(), LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(o);
        configurarFilas(o.getIdOrden(), List.of(cafe, pastel));
        configurarRelacionesOrden(List.of(
                relacion(cafe.getIdProducto(), promo, 10),
                relacion(pastel.getIdProducto(), promo, 30)));
        configurarConteoFactura(0);
        OrdenDAO.ResultadoPromocion r = dao.aplicarPromocion(o.getIdOrden(), promo.getIdDescuento());
        assertEquals(2, r.getProductosAfectados());
        assertEquals(new BigDecimal("9.00"), cafe.getPrecio());
        assertEquals(new BigDecimal("7.00"), pastel.getPrecio());
        assertEquals(new BigDecimal("4.00"), r.getDescuentoTotal());
    }

    @Test
    void productosRepetidosRecibenLaMismaPromocionPorFila() {
        Orden o = orden();
        OrdenProducto fila1 = fila(o, "10.00");
        OrdenProducto fila2 = fila(o, "10.00");
        fila2.setIdProducto(fila1.getIdProducto());
        Descuento promo = promocion("Mitad");
        when(em.find(Orden.class, o.getIdOrden(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(o);
        configurarFilas(o.getIdOrden(), List.of(fila1, fila2));
        configurarRelacionesOrden(List.of(relacion(fila1.getIdProducto(), promo, 50)));
        configurarConteoFactura(0);
        OrdenDAO.ResultadoPromocion r = dao.aplicarPromocion(o.getIdOrden(), promo.getIdDescuento());
        assertEquals(2, r.getProductosAfectados());
        assertEquals(new BigDecimal("5.00"), fila1.getPrecio());
        assertEquals(new BigDecimal("5.00"), fila2.getPrecio());
    }

    @Test
    void descuentaCorrectamenteCentavosConRedondeo() {
        Orden o = orden();
        OrdenProducto linea = fila(o, "0.05");
        Descuento promo = promocion("10");
        when(em.find(Orden.class, o.getIdOrden(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(o);
        configurarFilas(o.getIdOrden(), List.of(linea));
        configurarRelacionesOrden(List.of(relacion(linea.getIdProducto(), promo, 10)));
        configurarConteoFactura(0);
        dao.aplicarPromocion(o.getIdOrden(), promo.getIdDescuento());
        assertEquals(new BigDecimal("0.04"), linea.getPrecio());
    }

    @Test
    void noDescuentaSiLaPromocionNoAplica() {
        Orden o = orden();
        OrdenProducto linea = fila(o, "5.00");
        when(em.find(Orden.class, o.getIdOrden(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(o);
        configurarFilas(o.getIdOrden(), List.of(linea));
        configurarRelacionesOrden(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> dao.aplicarPromocion(o.getIdOrden(), UUID.randomUUID()));
        assertEquals(new BigDecimal("5.00"), linea.getPrecio());
        verify(em, never()).flush();
    }

    @Test
    void siHayProductoFacturadoNoDescuentaNinguno() {
        Orden o = orden();
        OrdenProducto cafe = fila(o, "5.00");
        OrdenProducto pastel = fila(o, "4.00");
        Descuento promo = promocion("Oferta");
        when(em.find(Orden.class, o.getIdOrden(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(o);
        configurarFilas(o.getIdOrden(), List.of(cafe, pastel));
        configurarRelacionesOrden(List.of(
                relacion(cafe.getIdProducto(), promo, 20),
                relacion(pastel.getIdProducto(), promo, 20)));
        configurarConteoFactura(1);
        assertThrows(IllegalArgumentException.class,
                () -> dao.aplicarPromocion(o.getIdOrden(), promo.getIdDescuento()));
        assertEquals(new BigDecimal("5.00"), cafe.getPrecio());
        assertEquals(new BigDecimal("4.00"), pastel.getPrecio());
        verify(em, never()).flush();
    }

    @Test
    void siPrecioEsInvalidoNoAplicaNiGuarda() {
        Orden o = orden();
        OrdenProducto linea = fila(o, "-1.00");
        Descuento promo = promocion("Oferta");
        when(em.find(Orden.class, o.getIdOrden(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(o);
        configurarFilas(o.getIdOrden(), List.of(linea));
        configurarRelacionesOrden(List.of(relacion(linea.getIdProducto(), promo, 20)));
        configurarConteoFactura(0);
        assertThrows(IllegalArgumentException.class,
                () -> dao.aplicarPromocion(o.getIdOrden(), promo.getIdDescuento()));
        assertEquals(new BigDecimal("-1.00"), linea.getPrecio());
        verify(em, never()).flush();
    }

    @Test
    void siFlushFallaSePropagaExcepcionYSeRestauranObjetos() {
        Orden o = orden();
        OrdenProducto linea = fila(o, "5.00");
        Descuento promo = promocion("Oferta");
        linea.setObservaciones("Sin hielo");
        when(em.find(Orden.class, o.getIdOrden(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(o);
        configurarFilas(o.getIdOrden(), List.of(linea));
        configurarRelacionesOrden(List.of(relacion(linea.getIdProducto(), promo, 20)));
        configurarConteoFactura(0);
        doThrow(new IllegalStateException("BD fuera de servicio")).when(em).flush();
        assertThrows(IllegalStateException.class,
                () -> dao.aplicarPromocion(o.getIdOrden(), promo.getIdDescuento()));
        assertEquals(new BigDecimal("5.00"), linea.getPrecio());
        assertEquals("Sin hielo", linea.getObservaciones());
    }

    @Test
    void rechazoIdentificadoresInvalidosSinPersistencia() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.aplicarPromocion(null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> dao.aplicarPromocion(UUID.randomUUID(), null));
        verifyNoInteractions(em);
    }

    @Test
    void noBorraFilaFacturada() {
        OrdenProducto fila = new OrdenProducto(UUID.randomUUID());
        when(em.find(OrdenProducto.class, fila.getIdOrdenProducto(), LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(fila);
        configurarConteoFactura(1);
        assertThrows(IllegalArgumentException.class, () -> dao.eliminarProducto(fila));
        verify(em, never()).remove(any());
    }

    @Test
    void eliminacionValidaFilaYLaRemueve() {
        OrdenProducto fila = new OrdenProducto(UUID.randomUUID());
        when(em.find(OrdenProducto.class, fila.getIdOrdenProducto(), LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(fila);
        configurarConteoFactura(0);
        dao.eliminarProducto(fila);
        verify(em).remove(fila);
        verify(em).flush();
    }

    @Test
    void noBorraOrdenConProductos() {
        Orden o = orden();
        when(em.find(Orden.class, o.getIdOrden(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(o);
        TypedQuery<Long> q = consulta(Long.class);
        when(em.createNamedQuery("OrdenProducto.countByOrden", Long.class)).thenReturn(q);
        when(q.setParameter("idOrden", o.getIdOrden())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(2L);
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(o));
        verify(em, never()).remove(any());
    }

    @Test
    void noCreaFilasSiListaCompletaContieneUnElementoInvalido() {
        Orden o = orden();
        List<OrdenProducto> lista = new ArrayList<>();
        lista.add(fila(o, "5.00"));
        lista.add(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crearOrdenCompleta(o, lista));
        verifyNoInteractions(em);
    }
}
