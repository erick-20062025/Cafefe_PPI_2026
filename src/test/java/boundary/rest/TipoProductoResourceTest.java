package boundary.rest;

import boundary.rest.dto.TipoProductoDTO;
import boundary.rest.messages.ApiResponse;
import control.TipoProductoDAO;
import entity.TipoProducto;
import jakarta.ws.rs.core.Response;
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

/** CRUD aislado de TipoProducto: no requiere PostgreSQL. */
@ExtendWith(MockitoExtension.class)
class TipoProductoResourceTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    @Mock private TipoProductoDAO tipoProductoDAO;
    @InjectMocks private TipoProductoResource recurso;

    @BeforeEach void iniciar() { RestTestRuntime.instalar(); }
    @AfterEach void finalizar() { RestTestRuntime.limpiar(); }

    private static TipoProducto tipoProducto(UUID id, String nombre, boolean activo) {
        TipoProducto tipoProducto = new TipoProducto(id);
        tipoProducto.setNombre(nombre);
        tipoProducto.setActivo(activo);
        return tipoProducto;
    }

    private static String json() {
        return "{\"nombre\":\" TipoProducto Principal \",\"activo\":true,\"observaciones\":\" nota \"}";
    }

    private static void error(Response r, int codigo) {
        assertEquals(codigo, r.getStatus());
        ApiResponse respuesta = assertInstanceOf(ApiResponse.class, r.getEntity());
        assertEquals("API-" + codigo, respuesta.getCodigo());
    }

    @Test void listarRetornaDtosSinRelacionesJPA() {
        when(tipoProductoDAO.findAll()).thenReturn(List.of(tipoProducto(ID, "TipoProducto Principal", true)));
        Response r = recurso.listar();
        assertEquals(200, r.getStatus());
        List<?> salida = (List<?>) r.getEntity();
        assertEquals(1, salida.size());
        TipoProductoDTO dto = assertInstanceOf(TipoProductoDTO.class, salida.get(0));
        assertEquals(ID, dto.getIdTipoProducto());
        assertEquals("TipoProducto Principal", dto.getNombre());
    }

    @Test void getIdInvalidoNoConsultaBaseDeDatos() {
        error(recurso.obtener("no-es-uuid"), 400);
        verifyNoInteractions(tipoProductoDAO);
    }

    @Test void getInexistenteRetorna404() {
        when(tipoProductoDAO.findById(ID)).thenReturn(null);
        error(recurso.obtener(ID.toString()), 404);
    }

    @Test void getExistenteRetornaDto() {
        when(tipoProductoDAO.findById(ID)).thenReturn(tipoProducto(ID, "TipoProducto Central", false));
        Response r = recurso.obtener(ID.toString());
        assertEquals(200, r.getStatus());
        TipoProductoDTO dto = (TipoProductoDTO) r.getEntity();
        assertFalse(dto.getActivo());
        assertEquals("TipoProducto Central", dto.getNombre());
    }

    @Test void crearValidaSinAccederDaoSiJsonEsInvalido() {
        error(recurso.crear("{}"), 422);
        error(recurso.crear("{\"nombre\":\"TipoProducto\",\"activo\":\"true\"}"), 400);
        error(recurso.crear("{\"nombre\":\"TipoProducto\",\"activo\":null}"), 422);
        error(recurso.crear("{\"nombre\":\"TipoProducto\",\"activo\":true,\"idTipoProducto\":\"x\"}"), 400);
        verifyNoInteractions(tipoProductoDAO);
    }

    @Test void crearGeneraIdYDevuelve201() {
        Response r = recurso.crear(json());
        assertEquals(201, r.getStatus());
        TipoProductoDTO dto = assertInstanceOf(TipoProductoDTO.class, r.getEntity());
        assertNotNull(dto.getIdTipoProducto());
        assertEquals("TipoProducto Principal", dto.getNombre());
        assertEquals("nota", dto.getObservaciones());
        verify(tipoProductoDAO).crear(argThat(c -> c.getIdTipoProducto() != null
                && "TipoProducto Principal".equals(c.getNombre()) && Boolean.TRUE.equals(c.getActivo())));
    }

    @Test void modificarIdInvalidoYJsonInvalido() {
        error(recurso.modificar("xxx", json()), 400);
        error(recurso.modificar(ID.toString(), "{\"nombre\":\"\",\"activo\":true}"), 422);
        verifyNoInteractions(tipoProductoDAO);
    }

    @Test void modificarAusenteRetorna404() {
        when(tipoProductoDAO.findById(ID)).thenReturn(null);
        error(recurso.modificar(ID.toString(), json()), 404);
        verify(tipoProductoDAO, never()).modificar(any());
    }

    @Test void modificarValidoRetorna200() {
        when(tipoProductoDAO.findById(ID)).thenReturn(tipoProducto(ID, "Anterior", true));
        Response r = recurso.modificar(ID.toString(), json());
        assertEquals(200, r.getStatus());
        TipoProductoDTO dto = (TipoProductoDTO) r.getEntity();
        assertEquals(ID, dto.getIdTipoProducto());
        assertEquals("TipoProducto Principal", dto.getNombre());
        verify(tipoProductoDAO).modificar(argThat(c -> ID.equals(c.getIdTipoProducto())
                && "TipoProducto Principal".equals(c.getNombre())));
    }

    @Test void eliminarInexistenteRetorna404() {
        when(tipoProductoDAO.findById(ID)).thenReturn(null);
        error(recurso.eliminar(ID.toString()), 404);
        verify(tipoProductoDAO, never()).eliminar(any());
    }

    @Test void eliminarConAsociacionesRetorna409SinBorrar() {
        TipoProducto existente = tipoProducto(ID, "TipoProducto Principal", true);
        when(tipoProductoDAO.findById(ID)).thenReturn(existente);
        when(tipoProductoDAO.countRelaciones(ID)).thenReturn(2L);
        error(recurso.eliminar(ID.toString()), 409);
        verify(tipoProductoDAO, never()).eliminar(any());
    }

    @Test void eliminarSinAsociacionesRetorna200() {
        TipoProducto existente = tipoProducto(ID, "TipoProducto Principal", true);
        when(tipoProductoDAO.findById(ID)).thenReturn(existente);
        Response r = recurso.eliminar(ID.toString());
        assertEquals(200, r.getStatus());
        verify(tipoProductoDAO).eliminar(existente);
    }

    @Test void integridadConcurrenteDelDaoRetorna409() {
        when(tipoProductoDAO.findById(ID)).thenReturn(tipoProducto(ID, "TipoProducto Principal", true));
        doThrow(new RuntimeException(new IllegalArgumentException(
                "No se puede eliminar un tipo de producto que tiene productos asociados")))
                .when(tipoProductoDAO).eliminar(any());
        error(recurso.eliminar(ID.toString()), 409);
    }

    @Test void falloPostgresqlRetorna500SinDatosInternos() {
        when(tipoProductoDAO.findAll()).thenThrow(new RuntimeException("password=secreto; SELECT tipoProducto"));
        Response r = recurso.listar();
        error(r, 500);
        String detalle = ((ApiResponse) r.getEntity()).getDetalle();
        assertFalse(detalle.contains("secreto"));
        assertFalse(detalle.contains("SELECT"));
    }

    @Test void excepcionesEnvueltasSeClasificanSinRevelarDetalles() {
        doThrow(new RuntimeException(new java.sql.SQLException("SQL secreto", "23503")))
                .when(tipoProductoDAO).crear(any());
        error(recurso.crear(json()), 409);
        doThrow(new RuntimeException(new IllegalArgumentException("Debe ingresar el nombre del tipo de producto")))
                .when(tipoProductoDAO).crear(any());
        error(recurso.crear(json()), 422);
        doThrow(new RuntimeException(new IllegalArgumentException("secreto")))
                .when(tipoProductoDAO).crear(any());
        Response respuesta = recurso.crear(json());
        error(respuesta, 500);
        assertFalse(((ApiResponse) respuesta.getEntity()).getDetalle().contains("secreto"));
    }

    @Test void borradoConcurrenteYUuidInvalido() {
        error(recurso.eliminar("1-1-1-1-1"), 400);
        verifyNoInteractions(tipoProductoDAO);
        when(tipoProductoDAO.findById(ID)).thenReturn(tipoProducto(ID, "A", true));
        doThrow(new RuntimeException(new IllegalArgumentException("El tipo de producto ya no existe")))
                .when(tipoProductoDAO).eliminar(any());
        error(recurso.eliminar(ID.toString()), 404);
    }

    @Test void permiteNombresRepetidosYDesactivacionSinConsultarAsociaciones() {
        recurso.crear(json());
        recurso.crear(json());
        verify(tipoProductoDAO, times(2)).crear(any());
        when(tipoProductoDAO.findById(ID)).thenReturn(tipoProducto(ID, "A", true));
        Response respuesta = recurso.modificar(ID.toString(), "{\"nombre\":\"A\",\"activo\":false}");
        assertEquals(200, respuesta.getStatus());
        assertFalse(((TipoProductoDTO) respuesta.getEntity()).getActivo());
        verify(tipoProductoDAO, never()).countRelaciones(any());
    }

    @Test void listaVaciaYDtoSoloCamposPropios() {
        when(tipoProductoDAO.findAll()).thenReturn(null);
        assertEquals(List.of(), recurso.listar().getEntity());
        assertEquals(4, java.util.Arrays.stream(TipoProductoDTO.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("get")).count());
    }
}
