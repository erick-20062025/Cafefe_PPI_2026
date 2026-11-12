package boundary.rest;

import boundary.rest.dto.DescuentoProductoDTO;
import boundary.rest.messages.ApiResponse;
import control.DescuentoProductoDAO;
import entity.Descuento;
import entity.DescuentoProducto;
import entity.Producto;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pruebas de recurso REST con DAO simulado y sin alterar PostgreSQL. */
@ExtendWith(MockitoExtension.class)
class DescuentoProductoResourceTest {
    private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID DESCUENTO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PRODUCTO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    @Mock private DescuentoProductoDAO descuentoProductoDAO;
    @InjectMocks private DescuentoProductoResource recurso;

    @BeforeEach void instalar() { RestTestRuntime.instalar(); }
    @AfterEach void limpiar() { RestTestRuntime.limpiar(); }

    private static String json() {
        return "{\"idDescuento\":\"" + DESCUENTO + "\",\"idProducto\":\"" + PRODUCTO
                + "\",\"valor\":10,\"fechaDesde\":\"2026-11-18\",\"observaciones\":\"Temporal\"}";
    }

    private static DescuentoProducto relacion() {
        DescuentoProducto r = new DescuentoProducto(ID);
        r.setIdDescuento(new Descuento(DESCUENTO));
        r.setIdProducto(new Producto(PRODUCTO));
        r.setValor(10);
        r.setFechaDesde(Date.from(LocalDate.of(2026, 11, 18).atStartOfDay(ZONA).toInstant()));
        r.setObservaciones("Temporal");
        return r;
    }

    private static void error(Response respuesta, int estado) {
        assertEquals(estado, respuesta.getStatus());
        ApiResponse dto = assertInstanceOf(ApiResponse.class, respuesta.getEntity());
        assertEquals("API-" + estado, dto.getCodigo());
        assertNotNull(dto.getMensaje());
        assertNotNull(dto.getDetalle());
    }

    @Test void listarDevuelveIdsYNoRelacionesCirculares() {
        when(descuentoProductoDAO.findAll()).thenReturn(List.of(relacion()));
        Response respuesta = recurso.listar();
        assertEquals(200, respuesta.getStatus());
        List<?> lista = assertInstanceOf(List.class, respuesta.getEntity());
        DescuentoProductoDTO dto = assertInstanceOf(DescuentoProductoDTO.class, lista.get(0));
        assertEquals(ID, dto.getIdDescuentoProducto());
        assertEquals(DESCUENTO, dto.getIdDescuento());
        assertEquals(PRODUCTO, dto.getIdProducto());
        assertEquals(Integer.valueOf(10), dto.getValor());
    }

    @Test void consultasFiltranPorDescuentoYProducto() {
        when(descuentoProductoDAO.findByIdDescuento(DESCUENTO)).thenReturn(List.of(relacion()));
        when(descuentoProductoDAO.findByIdProducto(PRODUCTO)).thenReturn(List.of(relacion()));
        assertEquals(1, ((List<?>) recurso.listarPorDescuento(DESCUENTO.toString()).getEntity()).size());
        assertEquals(1, ((List<?>) recurso.listarPorProducto(PRODUCTO.toString()).getEntity()).size());
        verify(descuentoProductoDAO).findByIdDescuento(DESCUENTO);
        verify(descuentoProductoDAO).findByIdProducto(PRODUCTO);
    }

    @Test void rutasConUuidInvalidoNoAccedenDao() {
        error(recurso.obtener("incorrecto"), 400);
        error(recurso.eliminar("incorrecto"), 400);
        error(recurso.listarPorProducto("incorrecto"), 400);
        error(recurso.listarPorDescuento("incorrecto"), 400);
        verifyNoInteractions(descuentoProductoDAO);
    }

    @Test void obtenerRegistroExistenteEInexistente() {
        when(descuentoProductoDAO.findById(ID)).thenReturn(relacion()).thenReturn((DescuentoProducto) null);
        assertEquals(200, recurso.obtener(ID.toString()).getStatus());
        error(recurso.obtener(ID.toString()), 404);
    }

    @Test void crearRechazaJsonAntesDeAccederDao() {
        error(recurso.crear("{}"), 422);
        error(recurso.crear(json().replace("\"valor\":10", "\"valor\":null")), 422);
        error(recurso.crear(json().replace("\"valor\":10", "\"valor\":1.5")), 400);
        error(recurso.crear(json().replace("}", ",\"idDescuentoProducto\":\"x\"}")), 400);
        verifyNoInteractions(descuentoProductoDAO);
    }

    @Test void crearValidoGeneraUuidYRetorna201() {
        Response respuesta = recurso.crear(json());
        assertEquals(201, respuesta.getStatus());
        DescuentoProductoDTO dto = assertInstanceOf(DescuentoProductoDTO.class, respuesta.getEntity());
        assertNotNull(dto.getIdDescuentoProducto());
        assertEquals(DESCUENTO, dto.getIdDescuento());
        assertEquals(PRODUCTO, dto.getIdProducto());
        verify(descuentoProductoDAO).crear(argThat(r -> r.getIdDescuentoProducto() != null
                && DESCUENTO.equals(r.getIdDescuento().getIdDescuento())
                && PRODUCTO.equals(r.getIdProducto().getIdProducto())
                && r.getValor() == 10 && r.getFechaDesde() != null));
    }

    @Test void duplicadosDevuelven409AunqueEjBEnvuelvaError() {
        doThrow(new RuntimeException("EJB", new IllegalArgumentException(
                "Este descuento ya está asociado al producto")))
                .when(descuentoProductoDAO).crear(any());
        error(recurso.crear(json()), 409);
    }

    @Test void productoInactivoYReferenciaInexistenteDevuelven422() {
        doThrow(new IllegalArgumentException("No se pueden asociar descuentos a un producto inactivo"))
                .doThrow(new IllegalArgumentException("El descuento seleccionado no existe"))
                .when(descuentoProductoDAO).crear(any());
        error(recurso.crear(json()), 422);
        error(recurso.crear(json()), 422);
    }

    @Test void porcentajeSuperiorAlMaximoYFechaInvalidaDevuelven422() {
        doThrow(new IllegalArgumentException("El descuento aplicado no puede superar 5 %"))
                .doThrow(new IllegalArgumentException("Las fechas están fuera de la vigencia del descuento"))
                .doThrow(new IllegalArgumentException("Debe indicar la fecha hasta para este descuento"))
                .when(descuentoProductoDAO).crear(any());
        error(recurso.crear(json()), 422);
        error(recurso.crear(json()), 422);
        error(recurso.crear(json()), 422);
    }

    @Test void eliminarUnaAsociacionNoBorraProductoNiDescuento() {
        DescuentoProducto r = relacion();
        when(descuentoProductoDAO.findById(ID)).thenReturn(r);
        Response respuesta = recurso.eliminar(ID.toString());
        assertEquals(200, respuesta.getStatus());
        verify(descuentoProductoDAO).eliminar(r);
        verify(descuentoProductoDAO, never()).crear(any());
    }

    @Test void eliminarInexistenteRetorna404() {
        error(recurso.eliminar(ID.toString()), 404);
        verify(descuentoProductoDAO, never()).eliminar(any());
    }

    @Test void errorSqlDeIntegridadRetorna409SinFiltrarDetalles() {
        doThrow(new RuntimeException("EJB", new SQLException("foreign key", "23503")))
                .when(descuentoProductoDAO).crear(any());
        Response respuesta = recurso.crear(json());
        error(respuesta, 409);
        assertFalse(((ApiResponse) respuesta.getEntity()).getDetalle().contains("foreign key"));
    }

    @Test void errorTecnicoRetorna500SinRevelarSqlNiClaves() {
        when(descuentoProductoDAO.findAll()).thenThrow(
                new RuntimeException("password=privado SELECT * FROM descuento_producto"));
        Response respuesta = recurso.listar();
        error(respuesta, 500);
        String detalle = ((ApiResponse) respuesta.getEntity()).getDetalle();
        assertFalse(detalle.contains("password"));
        assertFalse(detalle.contains("SELECT"));
    }
}
