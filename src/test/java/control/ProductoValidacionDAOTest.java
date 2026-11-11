
package control;

import entity.Producto;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductoValidacionDAOTest {

    private ProductoDAO dao;
    private EntityManager em;

    @BeforeEach
    void configurar() throws Exception {
        dao = new ProductoDAO();
        em = mock(EntityManager.class);

        Field campo = ProductoDAO.class.getDeclaredField("em");
        campo.setAccessible(true);
        campo.set(dao, em);
    }

    private Producto crearProductoValido() {
        Producto producto = new Producto();
        producto.setIdProducto(UUID.randomUUID());
        producto.setNombre("Café americano");
        producto.setActivo(true);
        producto.setPrecioSugerido(new BigDecimal("2.50"));
        return producto;
    }

    @Test
    void crearProductoValidoDebePersistir() {
        Producto producto = crearProductoValido();

        dao.crear(producto);

        verify(em).persist(producto);
    }

    @Test
    void crearProductoNuloDebeSerRechazado() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(null));

        verifyNoInteractions(em);
    }

    @Test
    void crearProductoSinIdDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setIdProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(producto));

        verifyNoInteractions(em);
    }

    @Test
    void crearProductoSinNombreDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setNombre("   ");

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(producto));

        verifyNoInteractions(em);
    }

    @Test
    void crearProductoConNombreLargoDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setNombre("A".repeat(156));

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(producto));

        verifyNoInteractions(em);
    }

    @Test
    void crearProductoSinEstadoDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setActivo(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(producto));

        verifyNoInteractions(em);
    }

    @Test
    void crearProductoSinPrecioDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setPrecioSugerido(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(producto));

        verifyNoInteractions(em);
    }

    @Test
    void crearProductoConPrecioNegativoDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setPrecioSugerido(new BigDecimal("-1.00"));

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(producto));

        verifyNoInteractions(em);
    }

    @Test
    void modificarProductoValidoDebeEjecutarMerge() {
        Producto producto = crearProductoValido();

        dao.modificar(producto);

        verify(em).merge(producto);
    }

    @Test
    void modificarProductoInvalidoDebeSerRechazado() {
        Producto producto = crearProductoValido();
        producto.setPrecioSugerido(new BigDecimal("-5.00"));

        assertThrows(IllegalArgumentException.class,
                () -> dao.modificar(producto));

        verifyNoInteractions(em);
    }

    @Test
    void modificarProductoInactivoDebeSerPermitido() {
        Producto producto = crearProductoValido();
        producto.setActivo(false);

        dao.modificar(producto);

        verify(em).merge(producto);
    }

    @Test
    void crearProductoConPrecioCeroDebeSerPermitido() {
        Producto producto = crearProductoValido();
        producto.setPrecioSugerido(BigDecimal.ZERO);

        dao.crear(producto);

        verify(em).persist(producto);
    }
}
