package boundary;

import control.OrdenDAO;
import control.ProductoDAO;
import entity.Descuento;
import entity.EmpleadoRol;
import entity.Orden;
import entity.OrdenProducto;
import entity.Producto;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pruebas de validaciones y orquestación de descuentos generales. */
@ExtendWith(MockitoExtension.class)
class OrdenModelTest {

    @Mock private OrdenDAO ordenDAO;
    @Mock private ProductoDAO productoDAO;
    private Modelo modelo;

    private static final class Modelo extends OrdenModel {
        String exito;
        String aviso;
        String error;
        @Override protected void mostrarExito(String s) { exito = s; }
        @Override protected void mostrarAdvertencia(String s) { aviso = s; }
        @Override protected void mostrarError(String s) { error = s; }
    }

    @BeforeEach
    void preparar() throws Exception {
        modelo = new Modelo();
        inyectar("ordenDAO", ordenDAO);
        inyectar("productoDAO", productoDAO);
        when(ordenDAO.findAll()).thenReturn(new ArrayList<>());
        when(ordenDAO.findCamarerosActivos()).thenReturn(new ArrayList<>());
        when(productoDAO.findAll()).thenReturn(new ArrayList<>());
        modelo.init();
    }

    private void inyectar(String campo, Object valor) throws Exception {
        Field f = OrdenModel.class.getDeclaredField(campo);
        f.setAccessible(true);
        f.set(modelo, valor);
    }

    private static Orden orden() {
        Orden o = new Orden(UUID.randomUUID());
        o.setFechaCreacion(new Date());
        return o;
    }

    private static Producto producto(String precio) {
        Producto p = new Producto(UUID.randomUUID());
        p.setNombre("Café de prueba");
        p.setActivo(true);
        p.setPrecioSugerido(new BigDecimal(precio));
        return p;
    }

    private static OrdenProducto detalle(Orden o) {
        OrdenProducto d = new OrdenProducto(UUID.randomUUID());
        d.setIdOrden(o);
        d.setIdProducto(producto("5.00"));
        d.setPrecio(new BigDecimal("5.00"));
        return d;
    }

    private static Descuento promo() {
        Descuento d = new Descuento(UUID.randomUUID());
        d.setNombre("Promoción café");
        return d;
    }

    private void habilitarEdicion(Orden o, List<OrdenProducto> productos, List<Descuento> descuentos) {
        when(ordenDAO.findProductosByOrden(o.getIdOrden())).thenReturn(productos);
        when(ordenDAO.findPromocionesAplicables(o.getIdOrden(), o.getFechaCreacion()))
                .thenReturn(descuentos);
        modelo.setRegistro(o);
        modelo.btnModificarHandler(null);
    }

    @Test
    void creaOrdenConFechaAnterior() {
        modelo.btnNuevoHandler(null);
        Orden orden = modelo.getRegistro();
        orden.setFechaCreacion(new Date(946684800000L));
        EmpleadoRol camarero = new EmpleadoRol(UUID.randomUUID());
        orden.setIdEmpleadoRol(camarero);
        modelo.btnGuardarHandler(null);
        verify(ordenDAO).crear(orden);
        assertNotNull(modelo.exito);
    }

    @Test
    void sinFechaNoGuardaLaOrden() {
        modelo.btnNuevoHandler(null);
        Orden orden = modelo.getRegistro();
        orden.setFechaCreacion(null);
        orden.setIdEmpleadoRol(new EmpleadoRol(UUID.randomUUID()));
        modelo.btnGuardarHandler(null);
        assertNotNull(modelo.aviso);
        verify(ordenDAO, never()).crear(any());
    }

    @Test
    void sinCamareroNoGuardaLaOrden() {
        modelo.btnNuevoHandler(null);
        modelo.btnGuardarHandler(null);
        assertNotNull(modelo.aviso);
        verify(ordenDAO, never()).crear(any());
    }

    @Test
    void alEditarSeCarganProductosYDescuentosGenerales() {
        Orden o = orden();
        OrdenProducto linea = detalle(o);
        Descuento descuento = promo();
        habilitarEdicion(o, List.of(linea), List.of(descuento));
        assertEquals(1, modelo.getProductosOrden().size());
        assertSame(descuento, modelo.getPromocionesDisponibles().get(0));
        assertEquals(new BigDecimal("5.00"), modelo.getTotalOrden());
    }

    @Test
    void noHayOpcionesSiNingunaPromocionAplica() {
        Orden o = orden();
        habilitarEdicion(o, List.of(detalle(o)), List.of());
        assertTrue(modelo.getPromocionesDisponibles().isEmpty());
    }

    @Test
    void noAplicaPromocionSinEstarEditando() {
        modelo.setRegistro(orden());
        modelo.setIdPromocionSeleccionada(UUID.randomUUID().toString());
        modelo.aplicarPromocion();
        assertNotNull(modelo.aviso);
        verify(ordenDAO, never()).aplicarPromocion(any(), any());
    }

    @Test
    void noAplicaPromocionSinSeleccion() {
        Orden o = orden();
        habilitarEdicion(o, List.of(detalle(o)), List.of(promo()));
        modelo.aplicarPromocion();
        assertNotNull(modelo.aviso);
        verify(ordenDAO, never()).aplicarPromocion(any(), any());
    }

    @Test
    void noAplicaPromocionNoDisponible() {
        Orden o = orden();
        habilitarEdicion(o, List.of(detalle(o)), List.of(promo()));
        modelo.setIdPromocionSeleccionada(UUID.randomUUID().toString());
        modelo.aplicarPromocion();
        assertNotNull(modelo.aviso);
        verify(ordenDAO, never()).aplicarPromocion(any(), any());
    }

    @Test
    void noAplicaIdentificadorQueNoEsUUID() {
        Orden o = orden();
        habilitarEdicion(o, List.of(detalle(o)), List.of(promo()));
        modelo.setIdPromocionSeleccionada("xyz");
        modelo.aplicarPromocion();
        assertNotNull(modelo.aviso);
        verify(ordenDAO, never()).aplicarPromocion(any(), any());
    }

    @Test
    void seleccionGeneralDelegaTransaccionYRefrescaLista() {
        Orden o = orden();
        Descuento descuento = promo();
        habilitarEdicion(o, List.of(detalle(o)), List.of(descuento));
        when(ordenDAO.aplicarPromocion(o.getIdOrden(), descuento.getIdDescuento()))
                .thenReturn(new OrdenDAO.ResultadoPromocion(2, new BigDecimal("1.80")));
        modelo.setIdPromocionSeleccionada(descuento.getIdDescuento().toString());
        modelo.aplicarPromocion();
        assertTrue(modelo.exito.contains("2 producto(s)"));
        assertNull(modelo.getIdPromocionSeleccionada());
        verify(ordenDAO).aplicarPromocion(o.getIdOrden(), descuento.getIdDescuento());
        verify(ordenDAO, times(2)).findProductosByOrden(o.getIdOrden());
        verify(ordenDAO, times(2)).findPromocionesAplicables(o.getIdOrden(), o.getFechaCreacion());
    }

    @Test
    void permiteElegirLaMismaPromocionDeNuevo() {
        Orden o = orden();
        Descuento descuento = promo();
        habilitarEdicion(o, List.of(detalle(o)), List.of(descuento));
        when(ordenDAO.aplicarPromocion(o.getIdOrden(), descuento.getIdDescuento()))
                .thenReturn(new OrdenDAO.ResultadoPromocion(1, new BigDecimal("1.00")));
        modelo.setIdPromocionSeleccionada(descuento.getIdDescuento().toString());
        modelo.aplicarPromocion();
        modelo.setIdPromocionSeleccionada(descuento.getIdDescuento().toString());
        modelo.aplicarPromocion();
        verify(ordenDAO, times(2)).aplicarPromocion(o.getIdOrden(), descuento.getIdDescuento());
    }

    @Test
    void errorDeNegocioNoLlamaRecarga() {
        Orden o = orden();
        Descuento descuento = promo();
        habilitarEdicion(o, List.of(detalle(o)), List.of(descuento));
        when(ordenDAO.aplicarPromocion(o.getIdOrden(), descuento.getIdDescuento()))
                .thenThrow(new IllegalArgumentException("Producto facturado"));
        modelo.setIdPromocionSeleccionada(descuento.getIdDescuento().toString());
        modelo.aplicarPromocion();
        assertEquals("Producto facturado", modelo.aviso);
        assertEquals(descuento.getIdDescuento().toString(), modelo.getIdPromocionSeleccionada());
        verify(ordenDAO, times(1)).findProductosByOrden(o.getIdOrden());
    }

    @Test
    void errorTecnicoSeNotificaYSeRegistraEnLog() {
        Orden o = orden();
        Descuento descuento = promo();
        habilitarEdicion(o, List.of(detalle(o)), List.of(descuento));
        when(ordenDAO.aplicarPromocion(o.getIdOrden(), descuento.getIdDescuento()))
                .thenThrow(new IllegalStateException("BD desconectada"));
        modelo.setIdPromocionSeleccionada(descuento.getIdDescuento().toString());
        Logger logger = Logger.getLogger(OrdenModel.class.getName());
        List<LogRecord> eventos = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { eventos.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try {
            modelo.aplicarPromocion();
        } finally {
            logger.removeHandler(handler);
        }
        assertEquals("No se pudo aplicar la promoción", modelo.error);
        assertTrue(eventos.stream().anyMatch(x -> x.getLevel().intValue() >= Level.SEVERE.intValue()
                && x.getThrown() instanceof IllegalStateException));
    }

    @Test
    void recargaFallidaDespuesDeAplicarNoDiceQueFalloGuardado() {
        Orden o = orden();
        Descuento descuento = promo();
        habilitarEdicion(o, List.of(detalle(o)), List.of(descuento));
        when(ordenDAO.aplicarPromocion(o.getIdOrden(), descuento.getIdDescuento()))
                .thenReturn(new OrdenDAO.ResultadoPromocion(1, new BigDecimal("1.00")));
        doThrow(new IllegalStateException("Fallo de recarga"))
                .when(ordenDAO).findPromocionesAplicables(o.getIdOrden(), o.getFechaCreacion());
        modelo.setIdPromocionSeleccionada(descuento.getIdDescuento().toString());
        modelo.aplicarPromocion();
        assertNotNull(modelo.exito);
        assertTrue(modelo.exito.contains("Promoción aplicada"));
        assertNotNull(modelo.aviso);
        assertTrue(modelo.aviso.contains("Operación realizada"));
        assertNull(modelo.error);
    }

    @Test
    void guardaProductoDesdeSeleccionYRecargaPromociones() {
        Orden o = orden();
        habilitarEdicion(o, List.of(), List.of());
        Producto p = producto("5.00");
        when(productoDAO.findById(p.getIdProducto())).thenReturn(p);
        modelo.setIdProductoSeleccionado(p.getIdProducto().toString());
        modelo.agregarProducto();
        verify(ordenDAO).agregarProducto(any(OrdenProducto.class));
        verify(ordenDAO, times(2)).findPromocionesAplicables(o.getIdOrden(), o.getFechaCreacion());
    }

    @Test
    void noEliminaProductoQuePerteneceAOtraOrden() {
        Orden o = orden();
        habilitarEdicion(o, List.of(), List.of());
        modelo.quitarProducto(detalle(orden()));
        assertNotNull(modelo.aviso);
        verify(ordenDAO, never()).eliminarProducto(any());
    }

    @Test
    void eliminarOrdenConErrorDeNegocioConservaSeleccion() {
        Orden o = orden();
        modelo.setRegistro(o);
        doThrow(new IllegalArgumentException("Orden con productos"))
                .when(ordenDAO).eliminar(o);
        modelo.btnEliminarHandler(null);
        assertEquals("Orden con productos", modelo.aviso);
        assertSame(o, modelo.getRegistro());
    }
}
