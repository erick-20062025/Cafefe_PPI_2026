package boundary.rest;

import boundary.rest.dto.OrdenDTO;
import boundary.rest.messages.ApiResponse;
import control.OrdenDAO;
import control.ProductoDAO;
import entity.Descuento;
import entity.EmpleadoRol;
import entity.Orden;
import entity.OrdenProducto;
import entity.Producto;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pruebas negativas y positivas del recurso sin arrancar PostgreSQL. */
@ExtendWith(MockitoExtension.class)
class OrdenResourceTest {
    @Mock control.RecepcionDAO recepcionDAO;
    private static final UUID ID_CAMARERO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ID_PRODUCTO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    @Mock private OrdenDAO ordenDAO;
    @Mock private ProductoDAO productoDAO;
    @Spy @InjectMocks private OrdenResource resource;

    @BeforeEach void iniciar() { RestTestRuntime.instalar(); }
    @AfterEach void terminar() { RestTestRuntime.limpiar(); }

    private static String cuerpo(String extras) {
        return "{\"idEmpleadoRol\":\"" + ID_CAMARERO
                + "\",\"productos\":[{\"idProducto\":\"" + ID_PRODUCTO + "\"" + extras + "}]}";
    }
    private static EmpleadoRol camarero() {
        EmpleadoRol empleadoRol = new EmpleadoRol();
        empleadoRol.setIdEmpleadoRol(ID_CAMARERO);
        return empleadoRol;
    }
    private static Producto producto(boolean activo, BigDecimal precio) {
        Producto producto = new Producto(ID_PRODUCTO);
        producto.setNombre("Café de prueba");
        producto.setActivo(activo);
        producto.setPrecioSugerido(precio);
        return producto;
    }
    private static Orden orden() {
        Orden o = new Orden(UUID.randomUUID());
        o.setFechaCreacion(new Date());
        o.setIdEmpleadoRol(camarero());
        return o;
    }
    private void catalogoValido() {
        when(ordenDAO.findCamareroActivoById(ID_CAMARERO)).thenReturn(camarero());
        when(productoDAO.findById(ID_PRODUCTO)).thenReturn(producto(true, new BigDecimal("3.50")));
    }
    private static void codigo(Response r, int estado) {
        assertEquals(estado, r.getStatus());
        assertTrue(r.getEntity() instanceof ApiResponse);
    }

    @Test void crearRechazaJsonInvalidoSinConsultarDao() {
        codigo(resource.crear("{mal"), 400);
        codigo(resource.crear("{}"), 422);
        codigo(resource.crear(cuerpo(",\"cantidad\":0")), 422);
        codigo(resource.crear(cuerpo(",\"precio\":0.01")), 400);
        verifyNoInteractions(ordenDAO, productoDAO);
    }

    @Test void crearRechazaCamareroInactivoSinGuardar() {
        codigo(resource.crear(cuerpo("")), 422);
        verify(ordenDAO).findCamareroActivoById(ID_CAMARERO);
        verify(ordenDAO, never()).crearOrdenCompleta(any(), anyList());
        verifyNoInteractions(productoDAO);
    }

    @Test void crearRechazaProductoInexistenteSinPersistir() {
        when(ordenDAO.findCamareroActivoById(ID_CAMARERO)).thenReturn(camarero());
        codigo(resource.crear(cuerpo("")), 422);
        verify(ordenDAO, never()).crearOrdenCompleta(any(), anyList());
    }

    @Test void crearRechazaProductoInactivoYPreciosIncoherentes() {
        when(ordenDAO.findCamareroActivoById(ID_CAMARERO)).thenReturn(camarero());
        when(productoDAO.findById(ID_PRODUCTO))
                .thenReturn(producto(false, new BigDecimal("1.00")),
                        producto(true, null),
                        producto(true, new BigDecimal("-1.00")),
                        producto(true, new BigDecimal("1.234")));
        for (int i = 0; i < 4; i++) codigo(resource.crear(cuerpo("")), 422);
        verify(ordenDAO, never()).crearOrdenCompleta(any(), anyList());
    }

    @Test void crearExitosoTomaPrecioDelCatalogoYGeneraFilasPorUnidad() {
        catalogoValido();
        Response r = resource.crear(cuerpo(",\"cantidad\":2,\"observaciones\":\"sin azúcar\""));
        assertEquals(201, r.getStatus());
        OrdenDTO dto = (OrdenDTO) r.getEntity();
        assertNotNull(dto.getIdOrden());
        assertEquals(2, dto.getProductos().size());
        assertEquals(new BigDecimal("7.00"), dto.getTotal());
        ArgumentCaptor<Orden> cabecera = ArgumentCaptor.forClass(Orden.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrdenProducto>> filas = ArgumentCaptor.forClass(List.class);
        verify(ordenDAO).crearOrdenCompleta(cabecera.capture(), filas.capture());
        assertEquals(ID_CAMARERO, cabecera.getValue().getIdEmpleadoRol().getIdEmpleadoRol());
        assertEquals(2, filas.getValue().size());
        assertEquals(new BigDecimal("3.50"), filas.getValue().get(0).getPrecio());
        assertEquals("sin azúcar", filas.getValue().get(0).getObservaciones());
        assertNotEquals(filas.getValue().get(0).getIdOrdenProducto(), filas.getValue().get(1).getIdOrdenProducto());
    }

    @Test void publicaNoPermiteElegirCamareroDesdeNavegador() {
        String conEmpleado = "{\"nombreCliente\":\"Ana\",\"idEmpleadoRol\":\"" + ID_CAMARERO
                + "\",\"productos\":[{\"idProducto\":\"" + ID_PRODUCTO + "\"}]}";
        codigo(resource.crearPublica(conEmpleado), 400);
        verifyNoInteractions(ordenDAO, productoDAO);
    }

    @Test void publicaUsaCamareroVirtualYEscribeNombreEnObservaciones() {
        doReturn(ID_CAMARERO.toString()).when(resource).camareroVirtualConfigurado();
        catalogoValido();
        String cuerpo = "{\"nombreCliente\":\" Ana \" ,\"productos\":[{\"idProducto\":\"" + ID_PRODUCTO
                + "\",\"observaciones\":\"taza grande\"}]}";
        Response r = resource.crearPublica(cuerpo);
        assertEquals(201, r.getStatus());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrdenProducto>> filas = ArgumentCaptor.forClass(List.class);
        verify(ordenDAO).crearOrdenCompleta(any(), filas.capture());
        assertEquals("Cliente: Ana | taza grande", filas.getValue().get(0).getObservaciones());
    }

    @Test void publicaNoCreaSiNoHayCamareroVirtualConfigurado() {
        doReturn(null).when(resource).camareroVirtualConfigurado();
        String json = "{\"nombreCliente\":\"Ana\",\"productos\":[{\"idProducto\":\"" + ID_PRODUCTO + "\"}]}";
        codigo(resource.crearPublica(json), 503);
        verifyNoInteractions(ordenDAO, productoDAO);
    }

    @Test void publicaNoCreaConIdVirtualInvalido() {
        doReturn("no-es-uuid").when(resource).camareroVirtualConfigurado();
        String json = "{\"nombreCliente\":\"Ana\",\"productos\":[{\"idProducto\":\"" + ID_PRODUCTO + "\"}]}";
        codigo(resource.crearPublica(json), 503);
        verifyNoInteractions(ordenDAO, productoDAO);
    }

    @Test void errorDeBaseDeDatosNoExponeInformacionInterna() {
        catalogoValido();
        doThrow(new RuntimeException("password=password123, SQL SELECT * FROM orden"))
                .when(ordenDAO).crearOrdenCompleta(any(), anyList());
        Response r = resource.crear(cuerpo(""));
        codigo(r, 500);
        assertFalse(((ApiResponse) r.getEntity()).getDetalle().contains("password123"));
        assertFalse(((ApiResponse) r.getEntity()).getDetalle().contains("SELECT"));
    }

    @Test void consultasDevuelvenSoloDtosNoEntidadesRecursivas() {
        Orden existente = orden();
        when(ordenDAO.findAll()).thenReturn(List.of(existente));
        Response r = resource.listar();
        assertEquals(200, r.getStatus());
        assertTrue(((List<?>) r.getEntity()).get(0) instanceof OrdenDTO);
        verify(ordenDAO, never()).findProductosByOrden(any());
    }

    @Test void pendientesUsaConsultaEspecialDelDao() {
        when(recepcionDAO.pendientes()).thenReturn(List.of(new control.RecepcionDAO.Resumen(orden().getIdOrden(), new java.util.Date())));
        Response r = resource.pendientes();
        assertEquals(200, r.getStatus());
        assertEquals(1, ((List<?>) r.getEntity()).size());
    }

    @Test void buscarNoConsultaDetallesConUuidMalo() {
        codigo(resource.buscar("cualquiera"), 400);
        verifyNoInteractions(ordenDAO);
    }

    @Test void buscarDevuelve404SiNoExiste() {
        codigo(resource.buscar(UUID.randomUUID().toString()), 404);
        verify(ordenDAO, never()).findProductosByOrden(any());
    }

    @Test void buscarValidoFiltraSoloProductosDeLaOrden() {
        Orden o = orden();
        when(ordenDAO.findById(o.getIdOrden())).thenReturn(o);
        when(ordenDAO.findProductosByOrden(o.getIdOrden())).thenReturn(List.of());
        Response r = resource.buscar(o.getIdOrden().toString());
        assertEquals(200, r.getStatus());
        assertTrue(r.getEntity() instanceof OrdenDTO);
        verify(ordenDAO).findProductosByOrden(o.getIdOrden());
    }

    @Test void productosNoExponeEntidadCompleta() {
        Orden o = orden();
        when(ordenDAO.findById(o.getIdOrden())).thenReturn(o);
        when(ordenDAO.findProductosByOrden(o.getIdOrden())).thenReturn(List.of());
        Response r = resource.productos(o.getIdOrden().toString());
        assertEquals(200, r.getStatus());
        assertEquals(List.of(), r.getEntity());
    }

    @Test void promocionesDeUnaOrdenNoDevuelvenTodoElCatalogo() {
        Orden o = orden();
        Descuento descuento = new Descuento(UUID.randomUUID());
        descuento.setNombre("Especial");
        when(ordenDAO.findById(o.getIdOrden())).thenReturn(o);
        when(ordenDAO.findPromocionesAplicables(eq(o.getIdOrden()), any(Date.class)))
                .thenReturn(List.of(descuento));
        Response r = resource.promociones(o.getIdOrden().toString());
        assertEquals(200, r.getStatus());
        assertTrue(((List<?>) r.getEntity()).get(0) instanceof OrdenDTO.PromocionDTO);
    }

    @Test void aplicarPromocionRechazaUuidIncorrecto() {
        codigo(resource.aplicarPromocion("no", UUID.randomUUID().toString()), 400);
        verifyNoInteractions(ordenDAO);
    }

    @Test void aplicarPromocionInvalidaNoCambiaPrecios() {
        Orden o = orden();
        when(ordenDAO.findById(o.getIdOrden())).thenReturn(o);
        UUID promo = UUID.randomUUID();
        when(ordenDAO.aplicarPromocion(o.getIdOrden(), promo))
                .thenThrow(new IllegalArgumentException("Promoción no aplicable"));
        codigo(resource.aplicarPromocion(o.getIdOrden().toString(), promo.toString()), 422);
    }

    @Test void eliminarNoBorraOrdenInexistente() {
        codigo(resource.eliminar(UUID.randomUUID().toString()), 404);
        verify(ordenDAO, never()).eliminar(any());
    }

    @Test void eliminarOrdenConProductosEsRechazadoPorDao() {
        Orden o = orden();
        when(ordenDAO.findById(o.getIdOrden())).thenReturn(o);
        doThrow(new IllegalArgumentException("Orden contiene productos"))
                .when(ordenDAO).eliminar(o);
        codigo(resource.eliminar(o.getIdOrden().toString()), 422);
    }

    @Test void eliminarOrdenConFilasDevuelve422AntesDeLlamarEliminar() {
        Orden o = orden();
        when(ordenDAO.findById(o.getIdOrden())).thenReturn(o);
        when(ordenDAO.countProductosByOrden(o.getIdOrden())).thenReturn(3L);

        Response r = resource.eliminar(o.getIdOrden().toString());
        codigo(r, 422);
        assertEquals("No se puede eliminar una orden con productos",
                ((ApiResponse) r.getEntity()).getDetalle());
        verify(ordenDAO, never()).eliminar(any());
    }

    @Test void eliminarOrdenSinProductosSigueDelegandoEnElDao() {
        Orden o = orden();
        when(ordenDAO.findById(o.getIdOrden())).thenReturn(o);
        when(ordenDAO.countProductosByOrden(o.getIdOrden())).thenReturn(0L);

        Response r = resource.eliminar(o.getIdOrden().toString());
        assertEquals(200, r.getStatus());
        verify(ordenDAO).eliminar(o);
    }

    @Test void errorAlConsultarCantidadDeFilasDevuelve500SinFiltrarSql() {
        Orden o = orden();
        when(ordenDAO.findById(o.getIdOrden())).thenReturn(o);
        when(ordenDAO.countProductosByOrden(o.getIdOrden()))
                .thenThrow(new RuntimeException("password=password123 SELECT * FROM orden_producto"));

        Response r = resource.eliminar(o.getIdOrden().toString());
        codigo(r, 500);
        String detalle = ((ApiResponse) r.getEntity()).getDetalle();
        assertFalse(detalle.contains("password123"));
        assertFalse(detalle.contains("SELECT"));
        verify(ordenDAO, never()).eliminar(any());
    }
    @Test
    void agregaFilasUsandoCantidadYProductoDeLaSolicitud() {
        UUID orden = UUID.randomUUID();
        when(ordenDAO.agregarProductos(orden, ID_PRODUCTO, 2, null)).thenReturn(List.of());
        String json = """
                {"idProducto":"%s","cantidad":2}
                """.formatted(ID_PRODUCTO);
        assertEquals(201, resource.agregarProductos(orden.toString(), json).getStatus());
        verify(ordenDAO).agregarProductos(orden, ID_PRODUCTO, 2, null);
    }

    @Test
    void quitaFilasSoloDeLaOrdenIndicadaYTraduceConflictos() {
        UUID orden = UUID.randomUUID();
        UUID fila = UUID.randomUUID();
        assertEquals(200, resource.quitarProducto(orden.toString(), fila.toString()).getStatus());
        doThrow(new RuntimeException(new OrdenDAO.OrdenFacturada())).when(ordenDAO).quitarProducto(orden, fila);
        assertEquals(409, resource.quitarProducto(orden.toString(), fila.toString()).getStatus());
        assertEquals(400, resource.quitarProducto(orden.toString(), "invalido").getStatus());
    }

    @Test
    void filtraFilasParaFacturacion() {
        UUID orden = UUID.randomUUID();
        when(ordenDAO.findById(orden)).thenReturn(new Orden(orden));
        when(ordenDAO.findProductosFacturables(orden)).thenReturn(List.of());
        var respuesta = resource.productosFacturables(orden.toString());
        assertEquals(200, respuesta.getStatus());
        assertEquals(List.of(), respuesta.getEntity());
        assertEquals(404, resource.productosFacturables(UUID.randomUUID().toString()).getStatus());
    }
}
