package boundary.rest;

import boundary.rest.dto.ProductoTipoProductoDTO;
import boundary.rest.messages.ApiResponse;
import control.ProductoTipoProductoDAO;
import entity.Producto;
import entity.TipoProducto;
import entity.ProductoTipoProducto;
import jakarta.ws.rs.core.Response;
import java.util.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoTipoProductoResourceTest {
    @Mock ProductoTipoProductoDAO dao;
    @InjectMocks ProductoTipoProductoResource recurso;
    final UUID id = UUID.randomUUID();
    @BeforeEach void iniciar() { RestTestRuntime.instalar(); }
    @AfterEach void terminar() { RestTestRuntime.limpiar(); }
    String json() { return "{\"idProducto\":\""+id+"\",\"idTipoProducto\":\""+id+"\",\"observaciones\":\" nota \"}"; }
    ProductoTipoProducto entidad() {
        var r = new ProductoTipoProducto(id);
        r.setProducto(new Producto(id));
        r.setIdTipoProducto(new TipoProducto(id));
        r.setFechaCreacion(new Date());
        return r;
    }
    void error(Response r, int status) {
        assertEquals(status, r.getStatus());
        assertEquals("API-"+status, assertInstanceOf(ApiResponse.class,r.getEntity()).getCodigo());
    }
    @Test void consultasYFiltrosDevuelvenDto() {
        var r = entidad();
        when(dao.findAll()).thenReturn(List.of(r));
        when(dao.findByIdProducto(id)).thenReturn(List.of(r));
        when(dao.findByIdTipoProducto(id)).thenReturn(List.of(r));
        for (Response respuesta : List.of(recurso.listar(), recurso.listarPorProducto(id.toString()), recurso.listarPorTipoProducto(id.toString()))) {
            assertEquals(200,respuesta.getStatus());
            var dto = assertInstanceOf(ProductoTipoProductoDTO.class, ((List<?>)respuesta.getEntity()).get(0));
            assertEquals(id,dto.getIdProductoTipoProducto());
            assertEquals(id,dto.getIdProducto());
            assertEquals(id,dto.getIdTipoProducto());
            assertEquals(r.getFechaCreacion().toInstant(),OffsetDateTime.parse(dto.getFechaCreacion()).toInstant());
        }
    }
    @Test void filtrosVaciosNoRequierenPadreExistente() {
        when(dao.findByIdProducto(id)).thenReturn(List.of());
        when(dao.findByIdTipoProducto(id)).thenReturn(List.of());
        assertEquals(List.of(),recurso.listarPorProducto(id.toString()).getEntity());
        assertEquals(List.of(),recurso.listarPorTipoProducto(id.toString()).getEntity());
        assertEquals(List.of(),recurso.listar().getEntity());
    }
    @Test void uuidInvalidosNoConsultanDao() {
        error(recurso.obtener("1-1-1-1-1"),400);
        error(recurso.eliminar("x"),400);
        error(recurso.listarPorProducto("x"),400);
        error(recurso.listarPorTipoProducto("x"),400);
        verifyNoInteractions(dao);
    }
    @Test void obtenerExistenteYAusente() {
        error(recurso.obtener(id.toString()),404);
        when(dao.findById(id)).thenReturn(entidad());
        assertInstanceOf(ProductoTipoProductoDTO.class,recurso.obtener(id.toString()).getEntity());
    }
    @Test void solicitudInvalidaNoPersiste() {
        error(recurso.crear("{}"),422);
        error(recurso.crear("[]"),400);
        verifyNoInteractions(dao);
    }
    @Test void crearGeneraUuidFechaYNormalizaObservaciones() {
        long antes=System.currentTimeMillis();
        Response respuesta=recurso.crear(json());
        assertEquals(201,respuesta.getStatus());
        var dto=(ProductoTipoProductoDTO)respuesta.getEntity();
        assertNotNull(dto.getIdProductoTipoProducto());
        assertEquals("nota",dto.getObservaciones());
        long fecha=OffsetDateTime.parse(dto.getFechaCreacion()).toInstant().toEpochMilli();
        assertTrue(fecha>=antes && fecha<=System.currentTimeMillis());
        verify(dao).crear(argThat(r -> id.equals(r.getProducto().getIdProducto()) && id.equals(r.getIdTipoProducto().getIdTipoProducto())));
    }
    @Test void erroresDeNegocioEnvueltos() {
        for (String mensaje : List.of("El producto seleccionado no existe", "El tipo de producto seleccionado no existe", "No se puede asignar un tipo de producto inactivo")) {
            doThrow(new RuntimeException(new IllegalArgumentException(mensaje))).when(dao).crear(any());
            error(recurso.crear(json()),422);
        }
        doThrow(new RuntimeException(new IllegalArgumentException("Este tipo de producto ya está asignado al producto"))).when(dao).crear(any());
        error(recurso.crear(json()),409);
    }
    @Test void eliminarSoloAsociacion() {
        var r=entidad();
        when(dao.findById(id)).thenReturn(r);
        assertEquals(200,recurso.eliminar(id.toString()).getStatus());
        verify(dao).eliminar(r);
    }
    @Test void eliminarAusenteYConcurrentementeAusente() {
        error(recurso.eliminar(id.toString()),404);
        verify(dao,never()).eliminar(any());
        when(dao.findById(id)).thenReturn(entidad());
        doThrow(new RuntimeException(new IllegalArgumentException("La asociación ya no existe"))).when(dao).eliminar(any());
        error(recurso.eliminar(id.toString()),404);
    }
    @Test void sqlYFalloTecnicoNoRevelanDatos() {
        doThrow(new RuntimeException(new java.sql.SQLException("secreto", "23503"))).when(dao).crear(any());
        error(recurso.crear(json()),409);
        when(dao.findAll()).thenThrow(new RuntimeException("secreto"));
        Response r=recurso.listar();
        error(r,500);
        assertFalse(((ApiResponse)r.getEntity()).getDetalle().contains("secreto"));
    }
    @Test void noExponePutNiPatchYDtoToleraDatosAntiguos() {
        for (var metodo: ProductoTipoProductoResource.class.getDeclaredMethods()) {
            assertFalse(metodo.isAnnotationPresent(jakarta.ws.rs.PUT.class));
            assertFalse(metodo.isAnnotationPresent(jakarta.ws.rs.PATCH.class));
        }
        var dto=new ProductoTipoProductoDTO(new ProductoTipoProducto(id));
        assertNull(dto.getFechaCreacion());
        assertNull(dto.getIdProducto());
        assertNull(dto.getIdTipoProducto());
    }
}
