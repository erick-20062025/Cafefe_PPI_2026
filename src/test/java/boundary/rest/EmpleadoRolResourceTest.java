package boundary.rest;

import boundary.rest.dto.EmpleadoRolDTO;
import boundary.rest.messages.ApiResponse;
import control.EmpleadoRolDAO;
import entity.Empleado;
import entity.EmpleadoRol;
import entity.Rol;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Simula EmpleadoRolDAO y valida HTTP, sin modificar PostgreSQL. */
@ExtendWith(MockitoExtension.class)
class EmpleadoRolResourceTest {
    private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID E = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID R = UUID.fromString("22222222-2222-4222-8222-222222222222");
    @Mock private EmpleadoRolDAO dao;
    @InjectMocks private EmpleadoRolResource recurso;

    @BeforeEach void antes() { RestTestRuntime.instalar(); }
    @AfterEach void despues() { RestTestRuntime.limpiar(); }

    private static EmpleadoRol relacion(UUID id, boolean activo) {
        Empleado e = new Empleado(); e.setIdEmpleado(E);
        Rol r = new Rol(R);
        EmpleadoRol er = new EmpleadoRol(id);
        er.setIdEmpleado(e); er.setIdRol(r);
        er.setActivo(activo); er.setObservaciones("prueba");
        return er;
    }
    private static String crear() {
        return "{\"idEmpleado\":\""+E+"\",\"idRol\":\""+R+"\",\"activo\":true,\"observaciones\":\"nota\"}";
    }
    private static void error(Response response,int http) {
        assertEquals(http,response.getStatus());
        ApiResponse dato=assertInstanceOf(ApiResponse.class,response.getEntity());
        assertEquals("API-"+http,dato.getCodigo());
    }
    @Test void listarYListarPorEmpleadoSoloDevuelvenDtos() {
        when(dao.findAll()).thenReturn(List.of(relacion(ID,true)));
        Response r=recurso.listar(); assertEquals(200,r.getStatus());
        List<?> lista=assertInstanceOf(List.class,r.getEntity());
        EmpleadoRolDTO dto=assertInstanceOf(EmpleadoRolDTO.class,lista.get(0));
        assertEquals(ID,dto.getIdEmpleadoRol()); assertEquals(E,dto.getIdEmpleado());
        assertEquals(R,dto.getIdRol());
        when(dao.findByIdEmpleado(E)).thenReturn(List.of(relacion(ID,true)));
        Response porEmpleado=recurso.listarPorEmpleado(E.toString());
        assertEquals(200,porEmpleado.getStatus());
        assertEquals(1,((List<?>)porEmpleado.getEntity()).size());
    }
    @Test void idMalformadoNoConsultaDao() {
        error(recurso.obtener("mal"),400);
        error(recurso.listarPorEmpleado("mal"),400);
        error(recurso.modificar("mal","{}"),400);
        error(recurso.eliminar("mal"),400);
        verifyNoInteractions(dao);
    }
    @Test void asignacionInexistenteRetorna404() {
        when(dao.findById(ID)).thenReturn(null);
        error(recurso.obtener(ID.toString()),404);
        error(recurso.modificar(ID.toString(),"{\"activo\":true}"),404);
        error(recurso.eliminar(ID.toString()),404);
        verify(dao,never()).modificar(any());
        verify(dao,never()).eliminar(any());
    }
    @Test void crearRechazaMalJsonYNullSinGuardar() {
        error(recurso.crear("{"),400);
        error(recurso.crear("{}"),422);
        error(recurso.crear(crear().replace("true","\"true\"")),400);
        verifyNoInteractions(dao);
    }
    @Test void crearValidoUsaDaoYGeneraUuid() {
        Response resp=recurso.crear(crear());
        assertEquals(201,resp.getStatus());
        EmpleadoRolDTO dto=assertInstanceOf(EmpleadoRolDTO.class,resp.getEntity());
        assertEquals(E,dto.getIdEmpleado()); assertEquals(R,dto.getIdRol());
        assertNotNull(dto.getIdEmpleadoRol());
        verify(dao).crear(argThat(v -> v.getIdEmpleadoRol()!=null
                && E.equals(v.getIdEmpleado().getIdEmpleado())
                && R.equals(v.getIdRol().getIdRol())
                && Boolean.TRUE.equals(v.getActivo())));
    }
    @Test void duplicadoRetorna409AunqueEjbEnvuelvaExcepcion() {
        doThrow(new RuntimeException("EJB", new IllegalArgumentException(
            "El empleado ya tiene ese rol asignado"))).when(dao).crear(any());
        error(recurso.crear(crear()),409);
    }
    @Test void empleadoOrolInexistenteOInactivoRetorna422() {
        doThrow(new RuntimeException(new IllegalArgumentException("El empleado o el rol no existe")),
                new RuntimeException(new IllegalArgumentException("No se puede asignar un rol inactivo")))
            .when(dao).crear(any());
        error(recurso.crear(crear()),422);
        error(recurso.crear(crear()),422);
    }
    @Test void editarSoloCamposPermitidos() {
        EmpleadoRol existente=relacion(ID,true);
        when(dao.findById(ID)).thenReturn(existente);
        Response respuesta=recurso.modificar(ID.toString(),
            "{\"activo\":false,\"observaciones\":\" desactivado \"}");
        assertEquals(200,respuesta.getStatus());
        EmpleadoRolDTO dto=(EmpleadoRolDTO)respuesta.getEntity();
        assertFalse(dto.getActivo()); assertEquals("desactivado",dto.getObservaciones());
        assertEquals(E,dto.getIdEmpleado()); assertEquals(R,dto.getIdRol());
        verify(dao).modificar(argThat(v -> ID.equals(v.getIdEmpleadoRol())
            && E.equals(v.getIdEmpleado().getIdEmpleado())
            && Boolean.FALSE.equals(v.getActivo())));
        assertTrue(existente.getActivo()); // no cambia entidad antes de la validacion del DAO
    }
    @Test void editarNoPermiteReasignacionNiCamposVacios() {
        error(recurso.modificar(ID.toString(),
            "{\"activo\":true,\"idEmpleado\":\""+E+"\"}"),400);
        error(recurso.modificar(ID.toString(),"{\"activo\":null}"),422);
        verifyNoInteractions(dao);
    }
    @Test void borrarUsaDaoYResponde200() {
        EmpleadoRol existente=relacion(ID,true);
        when(dao.findById(ID)).thenReturn(existente);
        assertEquals(200,recurso.eliminar(ID.toString()).getStatus());
        verify(dao).eliminar(existente);
    }
    @Test void borrarConHistorialRetorna409() {
        when(dao.findById(ID)).thenReturn(relacion(ID,true));
        doThrow(new RuntimeException(new IllegalArgumentException(
            "No se puede quitar este rol porque tiene órdenes o facturas asociadas")))
            .when(dao).eliminar(any());
        error(recurso.eliminar(ID.toString()),409);
    }
    @Test void errorSqlDeIntegridadNoFiltraMensajeInterno() {
        when(dao.findById(ID)).thenReturn(relacion(ID,true));
        doThrow(new RuntimeException("EJB",new SQLException(
            "violates foreign key FOREIGN_SECRET", "23503"))).when(dao).eliminar(any());
        Response r=recurso.eliminar(ID.toString()); error(r,409);
        assertFalse(((ApiResponse)r.getEntity()).getDetalle().contains("FOREIGN_SECRET"));
    }
    @Test void errorTecnicoNoFiltraSqlNiCredenciales() {
        when(dao.findAll()).thenThrow(new RuntimeException("password=secret SELECT * FROM empleado"));
        Response r=recurso.listar(); error(r,500);
        assertFalse(((ApiResponse)r.getEntity()).getDetalle().contains("password"));
        assertFalse(((ApiResponse)r.getEntity()).getDetalle().contains("SELECT"));
    }
}
