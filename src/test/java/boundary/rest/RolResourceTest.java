package boundary.rest;

import boundary.rest.dto.RolDTO;
import boundary.rest.messages.ApiResponse;
import control.RolDAO;
import entity.Rol;
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

/** Pruebas de RolResource con RolDAO simulado; no tocan PostgreSQL. */
@ExtendWith(MockitoExtension.class)
class RolResourceTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    @Mock private RolDAO rolDAO;
    @InjectMocks private RolResource recurso;

    @BeforeEach void iniciar() { RestTestRuntime.instalar(); }
    @AfterEach void finalizar() { RestTestRuntime.limpiar(); }

    private static Rol rol(UUID id, String nombre, boolean activo) {
        Rol rol = new Rol(id);
        rol.setNombre(nombre);
        rol.setActivo(activo);
        return rol;
    }

    private static String json() {
        return "{\"nombre\":\"  Rol Temporal  \",\"activo\":true,\"observaciones\":\"  nota  \"}";
    }

    private static void error(Response r, int estado, String codigo) {
        assertEquals(estado, r.getStatus());
        ApiResponse respuesta = assertInstanceOf(ApiResponse.class, r.getEntity());
        assertEquals(codigo, respuesta.getCodigo());
    }

    @Test void listarDevuelveDatosSinRelaciones() {
        when(rolDAO.findAll()).thenReturn(List.of(rol(ID, "Camarero", true)));
        Response r = recurso.listar();
        assertEquals(200, r.getStatus());
        List<?> lista = assertInstanceOf(List.class, r.getEntity());
        assertEquals(1, lista.size());
        RolDTO dto = assertInstanceOf(RolDTO.class, lista.get(0));
        assertEquals(ID, dto.getIdRol());
        assertEquals("Camarero", dto.getNombre());
    }

    @Test void uuidIncorrectoNoLlegaAlDao() {
        error(recurso.obtener("incorrecto"), 400, "API-400");
        error(recurso.modificar("incorrecto", json()), 400, "API-400");
        error(recurso.eliminar("incorrecto"), 400, "API-400");
        verifyNoInteractions(rolDAO);
    }

    @Test void consultarExistenteEInexistente() {
        when(rolDAO.findById(ID)).thenReturn(rol(ID, "Administrador", false), null);
        Response encontrado = recurso.obtener(ID.toString());
        assertEquals(200, encontrado.getStatus());
        assertFalse(((RolDTO) encontrado.getEntity()).getActivo());
        error(recurso.obtener(ID.toString()), 404, "API-404");
    }

    @Test void crearRechazaValoresInvalidosSinPersistir() {
        error(recurso.crear("{}"), 422, "API-422");
        error(recurso.crear("{\"nombre\":\"Rol\",\"activo\":\"true\"}"), 400, "API-400");
        error(recurso.crear("{\"nombre\":\"Rol\",\"activo\":null}"), 422, "API-422");
        error(recurso.crear("{\"nombre\":\"Rol\",\"activo\":true,\"idRol\":\"x\"}"), 400, "API-400");
        verifyNoInteractions(rolDAO);
    }

    @Test void crearValidoGeneraUuidYNormalizaTexto() {
        Response r = recurso.crear(json());
        assertEquals(201, r.getStatus());
        RolDTO dto = assertInstanceOf(RolDTO.class, r.getEntity());
        assertNotNull(dto.getIdRol());
        assertEquals("Rol Temporal", dto.getNombre());
        assertEquals("nota", dto.getObservaciones());
        verify(rolDAO).crear(argThat(v -> v.getIdRol() != null
                && "Rol Temporal".equals(v.getNombre()) && Boolean.TRUE.equals(v.getActivo())));
    }

    @Test void modificarRechazaJsonVacioSinConsultarDao() {
        error(recurso.modificar(ID.toString(), "{\"nombre\":\"\",\"activo\":true}"), 422, "API-422");
        verifyNoInteractions(rolDAO);
    }

    @Test void modificarInexistenteNoPersiste() {
        when(rolDAO.findById(ID)).thenReturn(null);
        error(recurso.modificar(ID.toString(), json()), 404, "API-404");
        verify(rolDAO, never()).modificar(any());
    }

    @Test void modificarValidoMantieneUuid() {
        when(rolDAO.findById(ID)).thenReturn(rol(ID, "Anterior", true));
        Response r = recurso.modificar(ID.toString(), json());
        assertEquals(200, r.getStatus());
        assertEquals(ID, ((RolDTO) r.getEntity()).getIdRol());
        verify(rolDAO).modificar(argThat(v -> ID.equals(v.getIdRol())
                && "Rol Temporal".equals(v.getNombre())));
    }

    @Test void eliminarInexistenteNoBorra() {
        when(rolDAO.findById(ID)).thenReturn(null);
        error(recurso.eliminar(ID.toString()), 404, "API-404");
        verify(rolDAO, never()).eliminar(any());
    }

    @Test void eliminarValidoUsaDao() {
        Rol existente = rol(ID, "Temporal", true);
        when(rolDAO.findById(ID)).thenReturn(existente);
        Response r = recurso.eliminar(ID.toString());
        assertEquals(200, r.getStatus());
        verify(rolDAO).eliminar(existente);
    }

    @Test void rolReferenciadoRetornaConflictoSinExponerSql() {
        when(rolDAO.findById(ID)).thenReturn(rol(ID, "Camarero", true));
        doThrow(new RuntimeException("EJB rollback", new SQLException(
                "violates foreign key empleado_rol", "23503")))
                .when(rolDAO).eliminar(any());
        Response r = recurso.eliminar(ID.toString());
        error(r, 409, "API-409");
        String detalle = ((ApiResponse) r.getEntity()).getDetalle();
        assertFalse(detalle.contains("foreign key"));
    }

    @Test void erroresDeNegocioDelDaoConservanCodigo() {
        doThrow(new RuntimeException(new IllegalArgumentException(
                "El nombre del rol es obligatorio"))).when(rolDAO).crear(any());
        error(recurso.crear(json()), 422, "API-422");
    }

    @Test void errorDeBaseNoRevelaDatosInternos() {
        when(rolDAO.findAll()).thenThrow(new RuntimeException("password=secreto SELECT rol"));
        Response r = recurso.listar();
        error(r, 500, "API-500");
        String detalle = ((ApiResponse) r.getEntity()).getDetalle();
        assertFalse(detalle.contains("password"));
        assertFalse(detalle.contains("SELECT"));
    }
}
