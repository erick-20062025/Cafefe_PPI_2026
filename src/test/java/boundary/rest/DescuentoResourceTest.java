package boundary.rest;

import boundary.rest.dto.DescuentoDTO;
import boundary.rest.messages.ApiResponse;
import control.DescuentoDAO;
import control.DescuentoProductoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.DescuentoProducto;
import entity.TipoDescuento;
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

/** Pruebas REST sin conexión a PostgreSQL ni cambios en DAO. */
@ExtendWith(MockitoExtension.class)
class DescuentoResourceTest {
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TIPO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    @Mock private DescuentoDAO descuentoDAO;
    @Mock private TipoDescuentoDAO tipoDescuentoDAO;
    @Mock private DescuentoProductoDAO descuentoProductoDAO;
    @InjectMocks private DescuentoResource recurso;

    @BeforeEach void instalar() { RestTestRuntime.instalar(); }
    @AfterEach void limpiar() { RestTestRuntime.limpiar(); }

    private static String futuro() { return LocalDate.now(ZONA).plusDays(2).toString(); }
    private static String pasado() { return LocalDate.now(ZONA).minusDays(2).toString(); }
    private static String json() {
        return "{\"idTipoDescuento\":\"" + TIPO + "\",\"nombre\":\"Descuento temporal\","
                + "\"fechaDesde\":\"" + futuro() + "\"}";
    }
    private static Descuento descuento(UUID id) {
        Descuento d = new Descuento(id);
        d.setIdTipoDescuento(TIPO);
        d.setNombre("Descuento temporal");
        d.setFechaDesde(Date.from(LocalDate.now(ZONA).plusDays(2).atStartOfDay(ZONA).toInstant()));
        return d;
    }
    private static TipoDescuento tipo(boolean activo) {
        TipoDescuento t = new TipoDescuento(TIPO);
        t.setActivo(activo);
        return t;
    }
    private static void error(Response respuesta, int estado) {
        assertEquals(estado, respuesta.getStatus());
        ApiResponse dto = assertInstanceOf(ApiResponse.class, respuesta.getEntity());
        assertEquals("API-" + estado, dto.getCodigo());
        assertNotNull(dto.getMensaje());
    }

    @Test void listarYConsultarNoExponenRelaciones() {
        when(descuentoDAO.findAll()).thenReturn(List.of(descuento(ID)));
        Response listado = recurso.listar();
        assertEquals(200, listado.getStatus());
        List<?> elementos = assertInstanceOf(List.class, listado.getEntity());
        DescuentoDTO dato = assertInstanceOf(DescuentoDTO.class, elementos.get(0));
        assertEquals(TIPO, dato.getIdTipoDescuento());
        assertEquals(ID, dato.getIdDescuento());
        when(descuentoDAO.findById(ID)).thenReturn(descuento(ID));
        assertEquals(200, recurso.obtener(ID.toString()).getStatus());
    }

    @Test void uuidInvalidoNoConsultaDao() {
        error(recurso.obtener("incorrecto"), 400);
        error(recurso.modificar("incorrecto", json()), 400);
        error(recurso.eliminar("incorrecto"), 400);
        verifyNoInteractions(descuentoDAO, tipoDescuentoDAO, descuentoProductoDAO);
    }

    @Test void inexistenteDevuelve404EnGetPutYDelete() {
        error(recurso.obtener(ID.toString()), 404);
        error(recurso.modificar(ID.toString(), json()), 404);
        error(recurso.eliminar(ID.toString()), 404);
        verify(descuentoDAO, never()).modificar(any());
        verify(descuentoDAO, never()).eliminar(any());
    }

    @Test void crearRechazaJsonAntesDeConsultarDao() {
        error(recurso.crear("{}"), 422);
        error(recurso.crear(json().replace("Descuento temporal", " ")), 422);
        error(recurso.crear(json().replace("\"" + TIPO + "\"", "12")), 400);
        error(recurso.crear(json().replace("}", ",\"idDescuento\":\"x\"}")), 400);
        verifyNoInteractions(descuentoDAO, tipoDescuentoDAO, descuentoProductoDAO);
    }

    @Test void crearNoAdmiteFechaPasada() {
        error(recurso.crear(json().replace(futuro(), pasado())), 422);
        verifyNoInteractions(descuentoDAO, tipoDescuentoDAO);
    }

    @Test void crearRechazaTipoInexistenteEInactivo() {
        when(tipoDescuentoDAO.findById(TIPO)).thenReturn(null, tipo(false));
        error(recurso.crear(json()), 422);
        error(recurso.crear(json()), 422);
        verify(descuentoDAO, never()).crear(any());
    }

    @Test void crearValidoRetorna201YGeneraUuid() {
        when(tipoDescuentoDAO.findById(TIPO)).thenReturn(tipo(true));
        Response r = recurso.crear(json());
        assertEquals(201, r.getStatus());
        DescuentoDTO dto = assertInstanceOf(DescuentoDTO.class, r.getEntity());
        assertNotNull(dto.getIdDescuento());
        assertEquals(TIPO, dto.getIdTipoDescuento());
        verify(descuentoDAO).crear(argThat(d -> d.getIdDescuento() != null
                && TIPO.equals(d.getIdTipoDescuento()) && d.getFechaDesde() != null));
    }

    @Test void modificarNoPermiteCambiarTipo() {
        when(descuentoDAO.findById(ID)).thenReturn(descuento(ID));
        String otroTipo = "33333333-3333-4333-8333-333333333333";
        error(recurso.modificar(ID.toString(), json().replace(TIPO.toString(), otroTipo)), 409);
        verify(descuentoDAO, never()).modificar(any());
    }

    @Test void modificarPermiteRegistrosHistoricos() {
        when(descuentoDAO.findById(ID)).thenReturn(descuento(ID));
        Response r = recurso.modificar(ID.toString(), json().replace(futuro(), pasado()));
        assertEquals(200, r.getStatus());
        assertEquals(ID, ((DescuentoDTO) r.getEntity()).getIdDescuento());
        verify(descuentoDAO).modificar(argThat(d -> d.getFechaDesde().toInstant()
                .atZone(ZONA).toLocalDate().isBefore(LocalDate.now(ZONA))));
    }

    @Test void eliminarConProductosDevuelve409SinLlamarEliminar() {
        when(descuentoDAO.findById(ID)).thenReturn(descuento(ID));
        when(descuentoProductoDAO.findByIdDescuento(ID))
                .thenReturn(List.of(new DescuentoProducto(UUID.randomUUID())));
        error(recurso.eliminar(ID.toString()), 409);
        verify(descuentoDAO, never()).eliminar(any());
    }

    @Test void eliminarSinProductosDevuelve200() {
        Descuento d = descuento(ID);
        when(descuentoDAO.findById(ID)).thenReturn(d);
        when(descuentoProductoDAO.findByIdDescuento(ID)).thenReturn(List.of());
        Response r = recurso.eliminar(ID.toString());
        assertEquals(200, r.getStatus());
        verify(descuentoDAO).eliminar(d);
    }

    @Test void errorIntegridadSeTraduceA409() {
        when(descuentoDAO.findById(ID)).thenReturn(descuento(ID));
        when(descuentoProductoDAO.findByIdDescuento(ID)).thenReturn(List.of());
        doThrow(new RuntimeException("EJB", new SQLException("foreign key producto", "23503")))
                .when(descuentoDAO).eliminar(any());
        Response r = recurso.eliminar(ID.toString());
        error(r, 409);
        assertFalse(((ApiResponse) r.getEntity()).getDetalle().contains("foreign key"));
    }

    @Test void errorTecnicoNoRevelaSqlNiCredenciales() {
        when(descuentoDAO.findAll()).thenThrow(new RuntimeException("password=privado SELECT descuento"));
        Response r = recurso.listar();
        error(r, 500);
        String detalle = ((ApiResponse) r.getEntity()).getDetalle();
        assertFalse(detalle.contains("password"));
        assertFalse(detalle.contains("SELECT"));
    }
}
