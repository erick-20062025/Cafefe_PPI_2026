package boundary.rest;

import boundary.rest.dto.ProductoDTO;
import boundary.rest.messages.ApiResponse;
import control.ProductoDAO;
import entity.Producto;
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

/** CRUD aislado de Producto: no requiere PostgreSQL. */
@ExtendWith(MockitoExtension.class)
class ProductoResourceTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    @Mock private ProductoDAO productoDAO;
    @InjectMocks private ProductoResource recurso;

    @BeforeEach void iniciar() { RestTestRuntime.instalar(); }
    @AfterEach void finalizar() { RestTestRuntime.limpiar(); }

    private static Producto producto(UUID id, String nombre, boolean activo) {
        Producto producto = new Producto(id);
        producto.setNombre(nombre);
        producto.setActivo(activo);
        return producto;
    }

    private static String json() {
        return "{\"nombre\":\" Producto Principal \",\"activo\":true,\"precioSugerido\":0,\"comentarios\":\" nota \"}";
    }

    private static void error(Response r, int codigo) {
        assertEquals(codigo, r.getStatus());
        ApiResponse respuesta = assertInstanceOf(ApiResponse.class, r.getEntity());
        assertEquals("API-" + codigo, respuesta.getCodigo());
    }

    @Test void listarRetornaDtosSinRelacionesJPA() {
        when(productoDAO.findAll()).thenReturn(List.of(producto(ID, "Producto Principal", true)));
        Response r = recurso.listar();
        assertEquals(200, r.getStatus());
        List<?> salida = (List<?>) r.getEntity();
        assertEquals(1, salida.size());
        ProductoDTO dto = assertInstanceOf(ProductoDTO.class, salida.get(0));
        assertEquals(ID, dto.getIdProducto());
        assertEquals("Producto Principal", dto.getNombre());
    }

    @Test void getIdInvalidoNoConsultaBaseDeDatos() {
        error(recurso.obtener("no-es-uuid"), 400);
        verifyNoInteractions(productoDAO);
    }

    @Test void getInexistenteRetorna404() {
        when(productoDAO.findById(ID)).thenReturn(null);
        error(recurso.obtener(ID.toString()), 404);
    }

    @Test void getExistenteRetornaDto() {
        when(productoDAO.findById(ID)).thenReturn(producto(ID, "Producto Central", false));
        Response r = recurso.obtener(ID.toString());
        assertEquals(200, r.getStatus());
        ProductoDTO dto = (ProductoDTO) r.getEntity();
        assertFalse(dto.getActivo());
        assertEquals("Producto Central", dto.getNombre());
    }

    @Test void crearValidaSinAccederDaoSiJsonEsInvalido() {
        error(recurso.crear("{}"), 422);
        error(recurso.crear("{\"nombre\":\"Producto\",\"activo\":\"true\"}"), 400);
        error(recurso.crear("{\"nombre\":\"Producto\",\"activo\":null}"), 422);
        error(recurso.crear("{\"nombre\":\"Producto\",\"activo\":true,\"idProducto\":\"x\"}"), 400);
        verifyNoInteractions(productoDAO);
    }

    @Test void crearGeneraIdYDevuelve201() {
        Response r = recurso.crear(json());
        assertEquals(201, r.getStatus());
        ProductoDTO dto = assertInstanceOf(ProductoDTO.class, r.getEntity());
        assertNotNull(dto.getIdProducto());
        assertEquals("Producto Principal", dto.getNombre());
        assertEquals("nota", dto.getComentarios());
        verify(productoDAO).crear(argThat(c -> c.getIdProducto() != null
                && "Producto Principal".equals(c.getNombre()) && Boolean.TRUE.equals(c.getActivo())));
    }

    @Test void modificarIdInvalidoYJsonInvalido() {
        error(recurso.modificar("xxx", json()), 400);
        error(recurso.modificar(ID.toString(), "{\"nombre\":\"\",\"activo\":true}"), 422);
        verifyNoInteractions(productoDAO);
    }

    @Test void modificarAusenteRetorna404() {
        when(productoDAO.findById(ID)).thenReturn(null);
        error(recurso.modificar(ID.toString(), json()), 404);
        verify(productoDAO, never()).modificar(any());
    }

    @Test void modificarValidoRetorna200() {
        when(productoDAO.findById(ID)).thenReturn(producto(ID, "Anterior", true));
        Response r = recurso.modificar(ID.toString(), json());
        assertEquals(200, r.getStatus());
        ProductoDTO dto = (ProductoDTO) r.getEntity();
        assertEquals(ID, dto.getIdProducto());
        assertEquals("Producto Principal", dto.getNombre());
        verify(productoDAO).modificar(argThat(c -> ID.equals(c.getIdProducto())
                && "Producto Principal".equals(c.getNombre())));
    }

    @Test void eliminarInexistenteRetorna404() {
        when(productoDAO.findById(ID)).thenReturn(null);
        error(recurso.eliminar(ID.toString()), 404);
        verify(productoDAO, never()).eliminar(any());
    }

    @Test void eliminarConRelacionesRetorna409SinBorrar() {
        Producto existente = producto(ID, "Producto Principal", true);
        when(productoDAO.findById(ID)).thenReturn(existente);
        when(productoDAO.countRelaciones(ID)).thenReturn(2L);
        error(recurso.eliminar(ID.toString()), 409);
        verify(productoDAO, never()).eliminar(any());
    }

    @Test void eliminarSinRelacionesRetorna200() {
        Producto existente = producto(ID, "Producto Principal", true);
        when(productoDAO.findById(ID)).thenReturn(existente);
        Response r = recurso.eliminar(ID.toString());
        assertEquals(200, r.getStatus());
        verify(productoDAO).eliminar(existente);
    }

    @Test void integridadConcurrenteDelDaoRetorna409() {
        when(productoDAO.findById(ID)).thenReturn(producto(ID, "Producto Principal", true));
        doThrow(new RuntimeException(new IllegalArgumentException(
                "No se puede eliminar un producto que tiene relaciones asociadas")))
                .when(productoDAO).eliminar(any());
        error(recurso.eliminar(ID.toString()), 409);
    }

    @Test void falloPostgresqlRetorna500SinDatosInternos() {
        when(productoDAO.findAll()).thenThrow(new RuntimeException("password=secreto; SELECT producto"));
        Response r = recurso.listar();
        error(r, 500);
        String detalle = ((ApiResponse) r.getEntity()).getDetalle();
        assertFalse(detalle.contains("secreto"));
        assertFalse(detalle.contains("SELECT"));
    }

    @Test void excepcionesEnvueltasSeClasificanSinRevelarSql() {
        doThrow(new RuntimeException(new java.sql.SQLException("SELECT secreto", "23503")))
                .when(productoDAO).crear(any());
        error(recurso.crear(json()), 409);
        doThrow(new RuntimeException(new IllegalArgumentException("El precio sugerido no puede ser negativo")))
                .when(productoDAO).crear(any());
        error(recurso.crear(json()), 422);
        doThrow(new RuntimeException(new IllegalArgumentException("secreto")))
                .when(productoDAO).crear(any());
        Response r = recurso.crear(json());
        error(r, 500);
        assertFalse(((ApiResponse) r.getEntity()).getDetalle().contains("secreto"));
    }

    @Test void borradoConcurrenteAusenteYUuidInvalido() {
        error(recurso.eliminar("1-1-1-1-1"), 400);
        verifyNoInteractions(productoDAO);
        when(productoDAO.findById(ID)).thenReturn(producto(ID, "A", true));
        doThrow(new RuntimeException(new IllegalArgumentException("El producto ya no existe")))
                .when(productoDAO).eliminar(any());
        error(recurso.eliminar(ID.toString()), 404);
    }

    @Test void listaVaciaYPrecioEnDto() {
        when(productoDAO.findAll()).thenReturn(null);
        assertEquals(List.of(), recurso.listar().getEntity());
        ProductoDTO dto = (ProductoDTO) recurso.crear(json()).getEntity();
        assertEquals(0, dto.getPrecioSugerido().signum());
        assertEquals(5, java.util.Arrays.stream(ProductoDTO.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("get")).count());
    }
}
