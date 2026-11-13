package boundary.rest;

import boundary.rest.dto.CajaDTO;
import boundary.rest.messages.ApiResponse;
import control.CajaDAO;
import entity.Caja;
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

/** CRUD aislado de Caja: no requiere PostgreSQL. */
@ExtendWith(MockitoExtension.class)
class CajaResourceTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    @Mock private CajaDAO cajaDAO;
    @InjectMocks private CajaResource recurso;

    @BeforeEach void iniciar() { RestTestRuntime.instalar(); }
    @AfterEach void finalizar() { RestTestRuntime.limpiar(); }

    private static Caja caja(UUID id, String nombre, boolean activo) {
        Caja caja = new Caja(id);
        caja.setNombre(nombre);
        caja.setActivo(activo);
        return caja;
    }

    private static String json() {
        return "{\"nombre\":\" Caja Principal \",\"activo\":true,\"observaciones\":\" nota \"}";
    }

    private static void error(Response r, int codigo) {
        assertEquals(codigo, r.getStatus());
        assertInstanceOf(ApiResponse.class, r.getEntity());
    }

    @Test void listarRetornaDtosSinRelacionesJPA() {
        when(cajaDAO.findAll()).thenReturn(List.of(caja(ID, "Caja Principal", true)));
        Response r = recurso.listar();
        assertEquals(200, r.getStatus());
        List<?> salida = (List<?>) r.getEntity();
        assertEquals(1, salida.size());
        CajaDTO dto = assertInstanceOf(CajaDTO.class, salida.get(0));
        assertEquals(ID, dto.getIdCaja());
        assertEquals("Caja Principal", dto.getNombre());
    }

    @Test void getIdInvalidoNoConsultaBaseDeDatos() {
        error(recurso.obtener("no-es-uuid"), 400);
        verifyNoInteractions(cajaDAO);
    }

    @Test void getInexistenteRetorna404() {
        when(cajaDAO.findById(ID)).thenReturn(null);
        error(recurso.obtener(ID.toString()), 404);
    }

    @Test void getExistenteRetornaDto() {
        when(cajaDAO.findById(ID)).thenReturn(caja(ID, "Caja Central", false));
        Response r = recurso.obtener(ID.toString());
        assertEquals(200, r.getStatus());
        CajaDTO dto = (CajaDTO) r.getEntity();
        assertFalse(dto.getActivo());
        assertEquals("Caja Central", dto.getNombre());
    }

    @Test void crearValidaSinAccederDaoSiJsonEsInvalido() {
        error(recurso.crear("{}"), 422);
        error(recurso.crear("{\"nombre\":\"Caja\",\"activo\":\"true\"}"), 400);
        error(recurso.crear("{\"nombre\":\"Caja\",\"activo\":null}"), 422);
        error(recurso.crear("{\"nombre\":\"Caja\",\"activo\":true,\"idCaja\":\"x\"}"), 400);
        verifyNoInteractions(cajaDAO);
    }

    @Test void crearGeneraIdYDevuelve201() {
        Response r = recurso.crear(json());
        assertEquals(201, r.getStatus());
        CajaDTO dto = assertInstanceOf(CajaDTO.class, r.getEntity());
        assertNotNull(dto.getIdCaja());
        assertEquals("Caja Principal", dto.getNombre());
        assertEquals("nota", dto.getObservaciones());
        verify(cajaDAO).crear(argThat(c -> c.getIdCaja() != null
                && "Caja Principal".equals(c.getNombre()) && Boolean.TRUE.equals(c.getActivo())));
    }

    @Test void modificarIdInvalidoYJsonInvalido() {
        error(recurso.modificar("xxx", json()), 400);
        error(recurso.modificar(ID.toString(), "{\"nombre\":\"\",\"activo\":true}"), 422);
        verifyNoInteractions(cajaDAO);
    }

    @Test void modificarAusenteRetorna404() {
        when(cajaDAO.findById(ID)).thenReturn(null);
        error(recurso.modificar(ID.toString(), json()), 404);
        verify(cajaDAO, never()).modificar(any());
    }

    @Test void modificarValidoRetorna200() {
        when(cajaDAO.findById(ID)).thenReturn(caja(ID, "Anterior", true));
        Response r = recurso.modificar(ID.toString(), json());
        assertEquals(200, r.getStatus());
        CajaDTO dto = (CajaDTO) r.getEntity();
        assertEquals(ID, dto.getIdCaja());
        assertEquals("Caja Principal", dto.getNombre());
        verify(cajaDAO).modificar(argThat(c -> ID.equals(c.getIdCaja())
                && "Caja Principal".equals(c.getNombre())));
    }

    @Test void eliminarInexistenteRetorna404() {
        when(cajaDAO.findById(ID)).thenReturn(null);
        error(recurso.eliminar(ID.toString()), 404);
        verify(cajaDAO, never()).eliminar(any());
    }

    @Test void eliminarConFacturasRetorna409SinBorrar() {
        Caja existente = caja(ID, "Caja Principal", true);
        when(cajaDAO.findById(ID)).thenReturn(existente);
        when(cajaDAO.countFacturas(ID)).thenReturn(2L);
        error(recurso.eliminar(ID.toString()), 409);
        verify(cajaDAO, never()).eliminar(any());
    }

    @Test void eliminarSinFacturasRetorna200() {
        Caja existente = caja(ID, "Caja Principal", true);
        when(cajaDAO.findById(ID)).thenReturn(existente);
        Response r = recurso.eliminar(ID.toString());
        assertEquals(200, r.getStatus());
        verify(cajaDAO).eliminar(existente);
    }

    @Test void integridadConcurrenteDelDaoRetorna409() {
        when(cajaDAO.findById(ID)).thenReturn(caja(ID, "Caja Principal", true));
        doThrow(new RuntimeException(new IllegalArgumentException(
                "No se puede eliminar una caja que tiene facturas asociadas")))
                .when(cajaDAO).eliminar(any());
        error(recurso.eliminar(ID.toString()), 409);
    }

    @Test void falloPostgresqlRetorna500SinDatosInternos() {
        when(cajaDAO.findAll()).thenThrow(new RuntimeException("password=secreto; SELECT caja"));
        Response r = recurso.listar();
        error(r, 500);
        String detalle = ((ApiResponse) r.getEntity()).getDetalle();
        assertFalse(detalle.contains("secreto"));
        assertFalse(detalle.contains("SELECT"));
    }
}
