package boundary;

import control.FacturaDAO;
import control.FacturaOrdenProductoDAO;
import control.OrdenDAO;
import entity.Caja;
import entity.EmpleadoRol;
import entity.Factura;
import entity.FacturaOrdenProducto;
import entity.Orden;
import entity.OrdenProducto;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacturaModelTest {

    @Mock
    private FacturaDAO facturaDAO;
    @Mock
    private FacturaOrdenProductoDAO facturaOrdenProductoDAO;
    @Mock
    private OrdenDAO ordenDAO;

    private FacturaModelPrueba model;

    private static class FacturaModelPrueba extends FacturaModel {

        private String exito;
        private String advertencia;
        private String error;

        @Override
        protected void mostrarExito(String detalle) {
            exito = detalle;
        }

        @Override
        protected void mostrarAdvertencia(String detalle) {
            advertencia = detalle;
        }

        @Override
        protected void mostrarError(String detalle) {
            error = detalle;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        model = new FacturaModelPrueba();
        inyectarCampo("facturaDAO", facturaDAO);
        inyectarCampo("facturaOrdenProductoDAO", facturaOrdenProductoDAO);
        inyectarCampo("ordenDAO", ordenDAO);
        model.init();
        clearInvocations(facturaDAO, facturaOrdenProductoDAO, ordenDAO);
    }

    @Test
    void inicializarDebeCargarFacturasCajasEmpleadosYOrdenes() {
        List<Factura> facturas = List.of(facturaPendiente());
        List<Caja> cajas = List.of(new Caja(UUID.randomUUID()));
        List<EmpleadoRol> empleados = List.of(new EmpleadoRol(UUID.randomUUID()));
        List<Orden> ordenes = List.of(new Orden(UUID.randomUUID()));
        when(facturaDAO.findAll()).thenReturn(facturas);
        when(facturaDAO.findCajasActivas()).thenReturn(cajas);
        when(facturaDAO.findEmpleadosRolActivos()).thenReturn(empleados);
        when(ordenDAO.findAll()).thenReturn(ordenes);

        model.init();

        assertSame(facturas, model.getModelo());
        assertSame(cajas, model.getCajas());
        assertSame(empleados, model.getEmpleadosRol());
        assertSame(ordenes, model.getOrdenesDisponibles());
        assertTrue(model.getProductosOrden().isEmpty());
        assertTrue(model.getProductosFactura().isEmpty());
        verify(facturaDAO).findCajasActivas();
        verify(facturaDAO).findEmpleadosRolActivos();
    }

    @Test
    void nuevaFacturaDebeIniciarPendienteConIdentificadorYFechaActual() {
        long inicio = System.currentTimeMillis();

        model.btnNuevoHandler(null);

        assertNotNull(model.getRegistro().getIdFactura());
        assertEquals("PENDIENTE", model.getRegistro().getEstado());
        long fecha = model.getRegistro().getFechaFacturacion().getTime();
        assertTrue(fecha >= inicio && fecha <= System.currentTimeMillis());
        assertTrue(model.isNuevo());
        assertFalse(model.isEditando());
        assertFalse(model.isConsultando());
        verifyNoInteractions(facturaDAO);
    }

    @Test
    void verFacturaDebeAdvertirSinSeleccionYActivarConsultaConSeleccion() {
        model.verFactura();

        assertEquals("Debe seleccionar una factura", model.advertencia);
        assertFalse(model.isConsultando());

        model.btnNuevoHandler(null);
        model.verFactura();

        assertTrue(model.isConsultando());
        assertFalse(model.isNuevo());
        assertFalse(model.isEditando());
        verifyNoInteractions(facturaDAO, facturaOrdenProductoDAO);
    }

    @Test
    void seleccionarFacturaDebeRecuperarRelacionesYLimpiarEntradaDeProductos() {
        prepararEntradaProducto();
        Factura factura = facturaCompleta();
        List<FacturaOrdenProducto> detalles = List.of(detalle(productoOrden()));
        when(facturaOrdenProductoDAO.findByIdFactura(factura.getIdFactura()))
                .thenReturn(detalles);
        model.setRegistro(factura);

        model.onRowSelect();

        assertEquals(factura.getIdCaja().getIdCaja().toString(), model.getIdCajaSeleccionada());
        assertEquals(factura.getIdEmpleadoRol().getIdEmpleadoRol().toString(),
                model.getIdEmpleadoRolSeleccionado());
        assertSame(detalles, model.getProductosFactura());
        assertNull(model.getIdOrdenSeleccionada());
        assertTrue(model.getProductosOrden().isEmpty());
        comprobarEntradaProductoLimpia();
        assertFalse(model.isNuevo());
        assertFalse(model.isEditando());
        assertFalse(model.isConsultando());
    }

    @Test
    void seleccionarFacturaDebeAceptarRelacionesAusentesOSinIdentificador() {
        Factura factura = facturaPendiente();
        model.setRegistro(factura);
        model.onRowSelect();
        assertNull(model.getIdCajaSeleccionada());
        assertNull(model.getIdEmpleadoRolSeleccionado());

        factura.setIdCaja(new Caja());
        factura.setIdEmpleadoRol(new EmpleadoRol());
        model.onRowSelect();
        assertNull(model.getIdCajaSeleccionada());
        assertNull(model.getIdEmpleadoRolSeleccionado());

        model.setRegistro(null);
        model.onRowSelect();
        verify(facturaOrdenProductoDAO, times(2)).findByIdFactura(factura.getIdFactura());
    }

    @Test
    void deseleccionarDebeLimpiarFacturaRelacionesYProductos() {
        prepararFacturaConEntradaYDetalle();

        model.onRowUnselect();

        comprobarEstadoLimpio();
    }

    @Test
    void cancelarDebeLimpiarTambienUnaFacturaEnConsulta() {
        prepararFacturaConEntradaYDetalle();
        model.verFactura();

        model.btnCancelarHandler(null);

        comprobarEstadoLimpio();
    }

    @Test
    void seleccionarOrdenDebeCargarProductosYReiniciarEntradaAnterior() {
        prepararEntradaProducto();
        UUID idOrden = UUID.randomUUID();
        List<OrdenProducto> productos = List.of(productoOrden());
        when(ordenDAO.findProductosByOrden(idOrden)).thenReturn(productos);

        model.setIdOrdenSeleccionada(idOrden.toString());

        assertSame(productos, model.getProductosOrden());
        comprobarEntradaProductoLimpia();
        verify(ordenDAO).findProductosByOrden(idOrden);
    }

    @Test
    void seleccionDeOrdenNulaVaciaOMalformadaDebeLimpiarSinConsultar() {
        for (String id : new String[]{null, "  ", "orden-invalida"}) {
            model.getProductosOrden().add(productoOrden());
            model.setPrecioProducto(BigDecimal.TEN);
            model.setObservacionesProducto("Anterior");

            model.setIdOrdenSeleccionada(id);

            assertTrue(model.getProductosOrden().isEmpty());
            comprobarEntradaProductoLimpia();
        }
        verifyNoInteractions(ordenDAO);
    }

    @Test
    void seleccionarProductoDebeCopiarPrecioYObservacionesDeLaOrden() {
        OrdenProducto producto = prepararEntradaProducto();

        assertEquals(producto.getPrecio(), model.getPrecioProducto());
        assertEquals(producto.getObservaciones(), model.getObservacionesProducto());
        verifyNoInteractions(facturaOrdenProductoDAO);
    }

    @Test
    void seleccionDeProductoAusenteOInvalidaDebeDescartarDatosAnteriores() {
        model.getProductosOrden().add(productoOrden());
        for (String id : new String[]{null, "  ", "producto-invalido", UUID.randomUUID().toString()}) {
            model.setPrecioProducto(BigDecimal.TEN);
            model.setObservacionesProducto("Anterior");

            model.setIdOrdenProductoSeleccionado(id);

            assertNull(model.getPrecioProducto());
            assertNull(model.getObservacionesProducto());
        }
        verifyNoInteractions(ordenDAO, facturaOrdenProductoDAO);
    }

    @Test
    void seleccionarCajaYEmpleadoDebeResolverLasRelacionesPorIdentificador() {
        Factura factura = facturaPendiente();
        Caja caja = new Caja(UUID.randomUUID());
        EmpleadoRol empleadoRol = new EmpleadoRol(UUID.randomUUID());
        when(facturaDAO.findCajaById(caja.getIdCaja())).thenReturn(caja);
        when(facturaDAO.findEmpleadoRolById(empleadoRol.getIdEmpleadoRol())).thenReturn(empleadoRol);
        model.setRegistro(factura);

        model.setIdCajaSeleccionada(caja.getIdCaja().toString());
        model.setIdEmpleadoRolSeleccionado(empleadoRol.getIdEmpleadoRol().toString());

        assertSame(caja, factura.getIdCaja());
        assertSame(empleadoRol, factura.getIdEmpleadoRol());
        verify(facturaDAO).findCajaById(caja.getIdCaja());
        verify(facturaDAO).findEmpleadoRolById(empleadoRol.getIdEmpleadoRol());
    }

    @Test
    void seleccionDeCajaYEmpleadoInvalidaDebeQuitarLasRelacionesAnteriores() {
        Factura factura = facturaPendiente();
        model.setRegistro(factura);
        for (String id : new String[]{null, "  ", "identificador-invalido"}) {
            factura.setIdCaja(new Caja(UUID.randomUUID()));
            factura.setIdEmpleadoRol(new EmpleadoRol(UUID.randomUUID()));

            model.setIdCajaSeleccionada(id);
            model.setIdEmpleadoRolSeleccionado(id);

            assertNull(factura.getIdCaja());
            assertNull(factura.getIdEmpleadoRol());
        }
        verifyNoInteractions(facturaDAO);
    }

    @Test
    void seleccionarRelacionesSinFacturaNoDebeConsultar() {
        model.setIdCajaSeleccionada(UUID.randomUUID().toString());
        model.setIdEmpleadoRolSeleccionado(UUID.randomUUID().toString());

        assertNull(model.getRegistro());
        verifyNoInteractions(facturaDAO);
    }

    @Test
    void agregarProductoDebeExigirFacturaPendienteYProductoSeleccionado() {
        model.agregarProducto();
        assertEquals("Debe seleccionar o crear una factura", model.advertencia);

        Factura factura = facturaPendiente();
        factura.setEstado("PAGADA");
        model.setRegistro(factura);
        model.agregarProducto();
        assertEquals("No se pueden agregar productos a una factura pagada", model.advertencia);

        factura.setEstado("PENDIENTE");
        for (String id : new String[]{null, "  "}) {
            model.setIdOrdenProductoSeleccionado(id);
            model.advertencia = null;
            model.agregarProducto();
            assertEquals("Debe seleccionar un producto de la orden", model.advertencia);
        }
        assertTrue(model.getProductosFactura().isEmpty());
        verifyNoInteractions(facturaOrdenProductoDAO);
    }

    @Test
    void agregarProductoDebeRechazarIdentificadorInvalidoOProductoAjenoALaOrden() {
        model.setRegistro(facturaPendiente());
        model.setIdOrdenProductoSeleccionado("invalido");
        model.agregarProducto();
        assertEquals("El producto seleccionado no es válido", model.advertencia);

        model.setIdOrdenProductoSeleccionado(UUID.randomUUID().toString());
        model.agregarProducto();
        assertEquals("El producto seleccionado no existe", model.advertencia);
        assertTrue(model.getProductosFactura().isEmpty());
        verifyNoInteractions(facturaOrdenProductoDAO);
    }

    @Test
    void agregarProductoDebeRechazarPrecioNuloONegativo() {
        model.btnNuevoHandler(null);
        prepararEntradaProducto();
        for (BigDecimal precio : new BigDecimal[]{null, new BigDecimal("-0.01")}) {
            model.setPrecioProducto(precio);

            model.advertencia = null;
            model.agregarProducto();

            assertEquals("El producto no tiene un precio válido", model.advertencia);
            assertTrue(model.getProductosFactura().isEmpty());
        }
        verifyNoInteractions(facturaOrdenProductoDAO);
    }

    @Test
    void agregarProductoANuevaFacturaDebeMantenerDetalleEnMemoriaYPermitirPrecioCero() {
        model.btnNuevoHandler(null);
        Factura factura = model.getRegistro();
        OrdenProducto producto = prepararEntradaProducto();
        model.setPrecioProducto(BigDecimal.ZERO);
        model.setObservacionesProducto("Cortesía");
        FacturaOrdenProducto detalleSinProducto = detalle(null);
        model.getProductosFactura().add(detalleSinProducto);

        model.agregarProducto();

        assertEquals(2, model.getProductosFactura().size());
        FacturaOrdenProducto agregado = model.getProductosFactura().get(1);
        assertNotNull(agregado.getIdFacturaOrdenProducto());
        assertSame(factura, agregado.getIdFactura());
        assertSame(producto, agregado.getIdOrdenProducto());
        assertEquals(BigDecimal.ZERO, agregado.getPrecio());
        assertEquals("Cortesía", agregado.getObservaciones());
        comprobarEntradaProductoLimpia();
        verifyNoInteractions(facturaOrdenProductoDAO, facturaDAO);
    }

    @Test
    void agregarProductoDuplicadoDebeConservarUnSoloDetalle() {
        model.btnNuevoHandler(null);
        OrdenProducto producto = prepararEntradaProducto();
        FacturaOrdenProducto existente = detalle(producto);
        model.getProductosFactura().add(existente);

        model.agregarProducto();

        assertEquals(List.of(existente), model.getProductosFactura());
        assertEquals("Este producto ya fue agregado a la factura", model.advertencia);
        verifyNoInteractions(facturaOrdenProductoDAO);
    }

    @Test
    void agregarProductoAFacturaExistenteDebePersistirYRecargarDetalles() {
        Factura factura = facturaPendiente();
        model.setRegistro(factura);
        OrdenProducto producto = prepararEntradaProducto();
        List<FacturaOrdenProducto> recargados = List.of(detalle(producto));
        when(facturaOrdenProductoDAO.findByIdFactura(factura.getIdFactura()))
                .thenReturn(recargados);

        model.agregarProducto();

        ArgumentCaptor<FacturaOrdenProducto> captor = ArgumentCaptor.forClass(FacturaOrdenProducto.class);
        verify(facturaOrdenProductoDAO).agregarProductoAFactura(captor.capture());
        FacturaOrdenProducto agregado = captor.getValue();
        assertSame(factura, agregado.getIdFactura());
        assertSame(producto, agregado.getIdOrdenProducto());
        assertEquals(producto.getPrecio(), agregado.getPrecio());
        assertEquals(producto.getObservaciones(), agregado.getObservaciones());
        assertSame(recargados, model.getProductosFactura());
        assertEquals("Producto agregado a la factura", model.exito);
        comprobarEntradaProductoLimpia();
    }

    @Test
    void quitarProductoDebeRetirarSoloElDetalleIndicadoEIgnorarNull() {
        FacturaOrdenProducto primero = detalle(productoOrden());
        FacturaOrdenProducto segundo = detalle(productoOrden());
        model.getProductosFactura().addAll(List.of(primero, segundo));

        model.quitarProducto(null);
        assertEquals(2, model.getProductosFactura().size());
        model.quitarProducto(primero);

        assertEquals(List.of(segundo), model.getProductosFactura());
        verifyNoInteractions(facturaOrdenProductoDAO);
    }

    @Test
    void quitarProductoDebeTolerarListaDeDetallesNula() {
        Factura factura = facturaPendiente();
        model.setRegistro(factura);
        when(facturaOrdenProductoDAO.findByIdFactura(factura.getIdFactura())).thenReturn(null);
        model.onRowSelect();

        assertDoesNotThrow(() -> model.quitarProducto(detalle(productoOrden())));
        assertNull(model.getProductosFactura());
    }

    @Test
    void guardarDebeExigirRegistroCajaYEmpleadoAntesDePersistir() {
        model.btnGuardarHandler(null);
        assertNull(model.advertencia);
        model.btnNuevoHandler(null);
        Factura factura = model.getRegistro();
        model.btnGuardarHandler(null);
        assertEquals("Debe seleccionar una caja", model.advertencia);

        factura.setIdCaja(new Caja(UUID.randomUUID()));
        model.btnGuardarHandler(null);
        assertEquals("Debe seleccionar el empleado y su rol", model.advertencia);
        assertSame(factura, model.getRegistro());
        assertTrue(model.isNuevo());
        verifyNoInteractions(facturaDAO);
    }

    @Test
    void guardarFacturaNuevaDebeExigirAlMenosUnDetalle() {
        model.btnNuevoHandler(null);
        model.setRegistro(facturaCompleta());

        model.btnGuardarHandler(null);

        assertEquals("Debe agregar al menos un producto a la factura", model.advertencia);
        assertTrue(model.isNuevo());
        verifyNoInteractions(facturaDAO);
    }

    @Test
    void guardarFacturaNuevaDebeAceptarValidacionDeListaNulaSinPersistir() {
        Factura factura = facturaCompleta();
        model.setRegistro(factura);
        when(facturaOrdenProductoDAO.findByIdFactura(factura.getIdFactura())).thenReturn(null);
        model.onRowSelect();
        model.btnNuevoHandler(null);
        model.setRegistro(facturaCompleta());

        model.btnGuardarHandler(null);

        assertEquals("Debe agregar al menos un producto a la factura", model.advertencia);
        assertTrue(model.isNuevo());
        verifyNoInteractions(facturaDAO);
    }

    @Test
    void guardarFacturaNuevaDebeEnviarCabeceraYDetallesYLimpiarEstado() {
        prepararFacturaConEntradaYDetalle();
        Factura factura = model.getRegistro();
        List<FacturaOrdenProducto> detalles = model.getProductosFactura();
        List<Factura> recargadas = List.of(factura);
        when(facturaDAO.findAll()).thenReturn(recargadas);

        model.btnGuardarHandler(null);

        verify(facturaDAO).crearFacturaCompleta(same(factura), same(detalles));
        verify(facturaDAO, never()).modificar(any());
        assertSame(recargadas, model.getModelo());
        assertEquals("Factura guardada correctamente", model.exito);
        comprobarEstadoLimpio();
    }

    @Test
    void guardarFacturaEditadaDebeModificarCabeceraSinExigirNuevosProductos() {
        Factura factura = facturaCompleta();
        model.setRegistro(factura);
        model.btnModificarHandler(null);

        model.btnGuardarHandler(null);

        verify(facturaDAO).modificar(factura);
        verify(facturaDAO, never()).crearFacturaCompleta(any(), any());
        verify(facturaDAO).findAll();
        assertEquals("Factura modificada correctamente", model.exito);
        comprobarEstadoLimpio();
    }

    @Test
    void errorAlGuardarDebeConservarFacturaYDetallesParaCorregir() {
        prepararFacturaConEntradaYDetalle();
        Factura factura = model.getRegistro();
        List<FacturaOrdenProducto> detalles = model.getProductosFactura();
        doThrow(new IllegalStateException("Fallo simulado"))
                .when(facturaDAO).crearFacturaCompleta(factura, detalles);

        model.btnGuardarHandler(null);

        assertSame(factura, model.getRegistro());
        assertSame(detalles, model.getProductosFactura());
        assertTrue(model.isNuevo());
        assertEquals("No se pudo guardar la factura", model.error);
        assertNull(model.exito);
        verify(facturaDAO, never()).findAll();
    }

    @Test
    void permisosDebenDependerDeSeleccionYEstadoPagadoSinImportarMayusculas() {
        assertFalse(model.isPuedeEditar());
        assertFalse(model.isPuedeEliminar());
        assertFalse(model.isFacturaPendiente());
        assertFalse(model.isFacturaPagada());

        Factura factura = facturaPendiente();
        model.setRegistro(factura);
        factura.setEstado("pendiente");
        assertTrue(model.isFacturaPendiente());
        assertFalse(model.isFacturaPagada());
        assertTrue(model.isPuedeEditar());
        assertTrue(model.isPuedeEliminar());

        factura.setEstado("pagada");
        assertTrue(model.isFacturaPagada());
        assertFalse(model.isFacturaPendiente());
        assertFalse(model.isPuedeEditar());
        assertFalse(model.isPuedeEliminar());

        factura.setEstado(null);
        assertFalse(model.isFacturaPendiente());
        assertFalse(model.isFacturaPagada());
    }

    @Test
    void eliminarDebeIgnorarAusenciaDeSeleccionYRechazarFacturaPagada() {
        model.btnEliminarHandler(null);
        Factura factura = facturaPendiente();
        factura.setEstado("PAGADA");
        model.setRegistro(factura);

        model.btnEliminarHandler(null);

        assertEquals("No se puede eliminar una factura pagada", model.advertencia);
        assertSame(factura, model.getRegistro());
        verifyNoInteractions(facturaDAO);
    }

    @Test
    void eliminarFacturaPendienteDebeEliminarCompletaYLimpiarListas() {
        prepararFacturaConEntradaYDetalle();
        Factura factura = model.getRegistro();

        model.btnEliminarHandler(null);

        verify(facturaDAO).eliminarFacturaCompleta(factura);
        verify(facturaDAO).findAll();
        assertNull(model.getRegistro());
        assertFalse(model.isNuevo());
        assertFalse(model.isEditando());
        assertFalse(model.isConsultando());
        assertTrue(model.getProductosFactura().isEmpty());
        assertTrue(model.getProductosOrden().isEmpty());
        assertEquals("Factura eliminada correctamente", model.exito);
    }

    @Test
    void errorAlEliminarDebeConservarSeleccionYDetalles() {
        prepararFacturaConEntradaYDetalle();
        Factura factura = model.getRegistro();
        doThrow(new IllegalStateException("Fallo simulado"))
                .when(facturaDAO).eliminarFacturaCompleta(factura);

        model.btnEliminarHandler(null);

        assertSame(factura, model.getRegistro());
        assertEquals(1, model.getProductosFactura().size());
        assertEquals("No se pudo eliminar la factura", model.error);
        assertNull(model.exito);
        verify(facturaDAO, never()).findAll();
    }

    @Test
    void confirmarPagadaDebeExigirFacturaPersistidaYPendiente() {
        model.confirmarPagada();
        assertEquals("Debe seleccionar una factura", model.advertencia);

        model.setRegistro(new Factura());
        model.confirmarPagada();
        assertEquals("Debe seleccionar una factura", model.advertencia);

        Factura factura = facturaPendiente();
        factura.setEstado("pagada");
        model.setRegistro(factura);
        model.confirmarPagada();
        assertEquals("La factura ya se encuentra pagada", model.advertencia);
        verifyNoInteractions(facturaDAO, facturaOrdenProductoDAO);
    }

    @Test
    void confirmarPagadaDebeRechazarFacturaSinProductosPersistidos() {
        Factura factura = facturaPendiente();
        model.setRegistro(factura);
        when(facturaOrdenProductoDAO.findByIdFactura(factura.getIdFactura()))
                .thenReturn(null, List.of());

        model.confirmarPagada();
        assertEquals("No se puede confirmar una factura sin productos", model.advertencia);
        model.confirmarPagada();

        assertEquals("No se puede confirmar una factura sin productos", model.advertencia);
        assertEquals("PENDIENTE", factura.getEstado());
        verifyNoInteractions(facturaDAO);
    }

    @Test
    void confirmarPagadaDebeCambiarEstadoPersistirYRecargar() {
        Factura factura = facturaPendiente();
        model.setRegistro(factura);
        when(facturaOrdenProductoDAO.findByIdFactura(factura.getIdFactura()))
                .thenReturn(List.of(detalle(productoOrden())));
        List<Factura> facturas = List.of(factura);
        when(facturaDAO.findAll()).thenReturn(facturas);

        model.confirmarPagada();

        assertEquals("PAGADA", factura.getEstado());
        verify(facturaDAO).modificar(factura);
        assertSame(facturas, model.getModelo());
        assertEquals("Factura confirmada como pagada", model.exito);
        assertFalse(model.isPuedeEditar());
        assertFalse(model.isPuedeEliminar());
    }

    private Factura facturaPendiente() {
        Factura factura = new Factura(UUID.randomUUID());
        factura.setEstado("PENDIENTE");
        return factura;
    }

    private Factura facturaCompleta() {
        Factura factura = facturaPendiente();
        factura.setIdCaja(new Caja(UUID.randomUUID()));
        factura.setIdEmpleadoRol(new EmpleadoRol(UUID.randomUUID()));
        return factura;
    }

    private OrdenProducto productoOrden() {
        OrdenProducto producto = new OrdenProducto(UUID.randomUUID());
        producto.setPrecio(new BigDecimal("4.50"));
        producto.setObservaciones("Sin azúcar");
        return producto;
    }

    private FacturaOrdenProducto detalle(OrdenProducto producto) {
        FacturaOrdenProducto detalle = new FacturaOrdenProducto(UUID.randomUUID());
        detalle.setIdOrdenProducto(producto);
        return detalle;
    }

    private OrdenProducto prepararEntradaProducto() {
        UUID idOrden = UUID.randomUUID();
        OrdenProducto producto = productoOrden();
        when(ordenDAO.findProductosByOrden(idOrden))
                .thenReturn(new ArrayList<>(List.of(producto)));
        model.setIdOrdenSeleccionada(idOrden.toString());
        model.setIdOrdenProductoSeleccionado(producto.getIdOrdenProducto().toString());
        return producto;
    }

    private void prepararFacturaConEntradaYDetalle() {
        model.btnNuevoHandler(null);
        Factura factura = facturaCompleta();
        model.setRegistro(factura);
        Caja caja = factura.getIdCaja();
        EmpleadoRol empleado = factura.getIdEmpleadoRol();
        when(facturaDAO.findCajaById(caja.getIdCaja())).thenReturn(caja);
        when(facturaDAO.findEmpleadoRolById(empleado.getIdEmpleadoRol())).thenReturn(empleado);
        model.setIdCajaSeleccionada(caja.getIdCaja().toString());
        model.setIdEmpleadoRolSeleccionado(empleado.getIdEmpleadoRol().toString());
        OrdenProducto producto = prepararEntradaProducto();
        model.getProductosFactura().add(detalle(producto));
    }

    private void comprobarEntradaProductoLimpia() {
        assertNull(model.getIdOrdenProductoSeleccionado());
        assertNull(model.getPrecioProducto());
        assertNull(model.getObservacionesProducto());
    }

    private void comprobarEstadoLimpio() {
        assertNull(model.getRegistro());
        assertFalse(model.isNuevo());
        assertFalse(model.isEditando());
        assertFalse(model.isConsultando());
        assertNull(model.getIdCajaSeleccionada());
        assertNull(model.getIdEmpleadoRolSeleccionado());
        assertNull(model.getIdOrdenSeleccionada());
        assertTrue(model.getProductosOrden().isEmpty());
        assertTrue(model.getProductosFactura().isEmpty());
        comprobarEntradaProductoLimpia();
    }

    private void inyectarCampo(String nombre, Object valor) throws Exception {
        Field campo = FacturaModel.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(model, valor);
    }
}
