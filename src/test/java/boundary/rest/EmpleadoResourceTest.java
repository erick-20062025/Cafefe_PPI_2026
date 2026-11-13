package boundary.rest;

import boundary.rest.dto.EmpleadoDTO;
import boundary.rest.messages.ApiResponse;
import control.EmpleadoDAO;
import control.EmpleadoRolDAO;
import entity.Empleado;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
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

/** Pruebas de la API sin modificar registros reales. */
@ExtendWith(MockitoExtension.class)
class EmpleadoResourceTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    @Mock private EmpleadoDAO empleadoDAO;
    @Mock private EmpleadoRolDAO empleadoRolDAO;
    @InjectMocks private EmpleadoResource recurso;

    @BeforeEach void iniciar() { RestTestRuntime.instalar(); }
    @AfterEach void finalizar() { RestTestRuntime.limpiar(); }

    private static Empleado empleado(UUID id, String nombre, boolean activo) {
        Empleado e = new Empleado(id);
        e.setNombre(nombre);
        e.setApellido("García");
        e.setActivo(activo);
        return e;
    }

    private static String json() {
        return "{\"nombre\":\"  Ana  \",\"apellido\":\"  Pérez  \","
                + "\"activo\":true,\"comentarios\":\"  Caja  \"}";
    }

    private static void error(Response respuesta, int http) {
        assertEquals(http, respuesta.getStatus());
        ApiResponse cuerpo = assertInstanceOf(ApiResponse.class, respuesta.getEntity());
        assertEquals("API-" + http, cuerpo.getCodigo());
        assertNotNull(cuerpo.getMensaje());
        assertNotNull(cuerpo.getDetalle());
    }

    @Test void listarDevuelveDtosSinRelaciones() {
        when(empleadoDAO.findAll()).thenReturn(List.of(empleado(ID, "Carmen", true)));
        Response r = recurso.listar();
        assertEquals(200, r.getStatus());
        List<?> lista = assertInstanceOf(List.class, r.getEntity());
        EmpleadoDTO dto = assertInstanceOf(EmpleadoDTO.class, lista.get(0));
        assertEquals(ID, dto.getIdEmpleado());
        assertEquals("Carmen", dto.getNombre());
    }

    @Test void listarSinDatosEsListaVacia() {
        when(empleadoDAO.findAll()).thenReturn(List.of());
        Response r = recurso.listar();
        assertEquals(200, r.getStatus());
        assertTrue(((List<?>) r.getEntity()).isEmpty());
    }

    @Test void uuidMalformadoNoAccedeAlDao() {
        error(recurso.obtener("uuid-invalido"), 400);
        error(recurso.modificar("uuid-invalido", json()), 400);
        error(recurso.eliminar("uuid-invalido"), 400);
        verifyNoInteractions(empleadoDAO, empleadoRolDAO);
    }

    @Test void obtenerExistenteEInexistente() {
        when(empleadoDAO.findById(ID)).thenReturn(empleado(ID, "Ana", true), null);
        Response r = recurso.obtener(ID.toString());
        assertEquals(200, r.getStatus());
        assertEquals("Ana", ((EmpleadoDTO) r.getEntity()).getNombre());
        error(recurso.obtener(ID.toString()), 404);
    }

    @Test void postInvalidoNoCreaRegistros() {
        error(recurso.crear("{}"), 422);
        error(recurso.crear("{\"nombre\":\"Ana\",\"apellido\":\"García\",\"activo\":\"true\"}"), 400);
        error(recurso.crear("{\"nombre\":\"Ana\",\"apellido\":null,\"activo\":true}"), 422);
        error(recurso.crear("{\"nombre\":\"Ana\",\"apellido\":\"García\",\"activo\":true,\"idEmpleado\":\"x\"}"), 400);
        verifyNoInteractions(empleadoDAO, empleadoRolDAO);
    }

    @Test void postValidoGeneraUuidYNormalizaDatos() {
        Response r = recurso.crear(json());
        assertEquals(201, r.getStatus());
        EmpleadoDTO dto = assertInstanceOf(EmpleadoDTO.class, r.getEntity());
        assertNotNull(dto.getIdEmpleado());
        assertEquals("Ana", dto.getNombre());
        assertEquals("Pérez", dto.getApellido());
        assertEquals("Caja", dto.getComentarios());
        verify(empleadoDAO).crear(argThat(e -> e.getIdEmpleado() != null
                && "Ana".equals(e.getNombre()) && "Pérez".equals(e.getApellido())
                && Boolean.TRUE.equals(e.getActivo())));
    }

    @Test void putVacioNoConsultaNiModifica() {
        error(recurso.modificar(ID.toString(), "{\"nombre\":\"  \",\"apellido\":\"García\",\"activo\":true}"), 422);
        verifyNoInteractions(empleadoDAO, empleadoRolDAO);
    }

    @Test void putInexistenteNoModifica() {
        when(empleadoDAO.findById(ID)).thenReturn(null);
        error(recurso.modificar(ID.toString(), json()), 404);
        verify(empleadoDAO, never()).modificar(any());
    }

    @Test void putValidoMantieneUuidYActualizaCampos() {
        when(empleadoDAO.findById(ID)).thenReturn(empleado(ID, "Anterior", true));
        Response r = recurso.modificar(ID.toString(), json());
        assertEquals(200, r.getStatus());
        assertEquals(ID, ((EmpleadoDTO) r.getEntity()).getIdEmpleado());
        verify(empleadoDAO).modificar(argThat(e -> ID.equals(e.getIdEmpleado())
                && "Ana".equals(e.getNombre()) && "Pérez".equals(e.getApellido())));
    }

    @Test void deleteInexistenteNoBorra() {
        when(empleadoDAO.findById(ID)).thenReturn(null);
        error(recurso.eliminar(ID.toString()), 404);
        verify(empleadoDAO, never()).eliminar(any());
        verifyNoInteractions(empleadoRolDAO);
    }

    @Test void deleteConRolesRetorna409SinBorrar() {
        when(empleadoDAO.findById(ID)).thenReturn(empleado(ID, "Ana", true));
        when(empleadoRolDAO.countByIdEmpleado(ID)).thenReturn(2L);
        error(recurso.eliminar(ID.toString()), 409);
        verify(empleadoDAO, never()).eliminar(any());
    }

    @Test void deleteSinRolesUtilizaDaoYRetorna200() {
        Empleado existente = empleado(ID, "Ana", true);
        when(empleadoDAO.findById(ID)).thenReturn(existente);
        Response r = recurso.eliminar(ID.toString());
        assertEquals(200, r.getStatus());
        verify(empleadoRolDAO).countByIdEmpleado(ID);
        verify(empleadoDAO).eliminar(existente);
    }

    @Test void conflictoDeIntegridadRetorna409SinFiltrarSql() {
        when(empleadoDAO.findById(ID)).thenReturn(empleado(ID, "Ana", true));
        doThrow(new RuntimeException("EJB", new SQLException(
                "foreign key empleado_rol", "23503")))
                .when(empleadoDAO).eliminar(any());
        Response r = recurso.eliminar(ID.toString());
        error(r, 409);
        assertFalse(((ApiResponse) r.getEntity()).getDetalle().contains("foreign key"));
    }

    @Test void errorDeNegocioEnvueltoConservaCodigo422() {
        doThrow(new RuntimeException(new IllegalArgumentException(
                "El apellido es obligatorio"))).when(empleadoDAO).crear(any());
        error(recurso.crear(json()), 422);
    }

    @Test void errorDeNegocioPorEliminarConRolesConserva409() {
        when(empleadoDAO.findById(ID)).thenReturn(empleado(ID, "Ana", true));
        doThrow(new RuntimeException(new IllegalArgumentException(
                "No se puede eliminar el empleado porque tiene roles asociados")))
                .when(empleadoDAO).eliminar(any());
        error(recurso.eliminar(ID.toString()), 409);
    }

    @Test void baseDeDatosNoExponeCredencialesNiSql() {
        when(empleadoDAO.findAll()).thenThrow(new RuntimeException(
                "database password=secreto SELECT * FROM empleado"));
        Response r = recurso.listar();
        error(r, 500);
        String detalle = ((ApiResponse) r.getEntity()).getDetalle();
        assertFalse(detalle.contains("password"));
        assertFalse(detalle.contains("SELECT"));
    }
}
