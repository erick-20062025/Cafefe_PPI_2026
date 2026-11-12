
package control;

import entity.Producto;
import entity.ProductoTipoProducto;
import entity.TipoProducto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductoTipoProductoValidacionDAOTest {

    private ProductoTipoProductoDAO dao;
    private EntityManager em;
    private TypedQuery<Long> query;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        dao = new ProductoTipoProductoDAO();
        em = mock(EntityManager.class);
        query = mock(TypedQuery.class);

        Field campo = ProductoTipoProductoDAO.class
                .getDeclaredField("em");
        campo.setAccessible(true);
        campo.set(dao, em);
    }

    private ProductoTipoProducto crearRelacionValida() {
        Producto producto = new Producto();
        producto.setIdProducto(UUID.randomUUID());

        TipoProducto tipo = new TipoProducto();
        tipo.setIdTipoProducto(UUID.randomUUID());
        tipo.setActivo(true);

        ProductoTipoProducto relacion =
                new ProductoTipoProducto();

        relacion.setIdProductoTipoProducto(UUID.randomUUID());
        relacion.setProducto(producto);
        relacion.setIdTipoProducto(tipo);

        return relacion;
    }

    private void simularCantidadRelaciones(long cantidad) {
        when(em.createQuery(anyString(), eq(Long.class)))
                .thenReturn(query);
        when(query.setParameter(anyString(), any()))
                .thenReturn(query);
        when(query.getSingleResult())
                .thenReturn(cantidad);
    }

    @Test
    void relacionValidaSinDuplicadoDebePersistir() {
        ProductoTipoProducto relacion = crearRelacionValida();
        simularCantidadRelaciones(0L);

        assertDoesNotThrow(() -> dao.crear(relacion));

        verify(em).persist(relacion);
        verify(query).setParameter(
                "idProducto",
                relacion.getProducto().getIdProducto());
        verify(query).setParameter(
                "idTipoProducto",
                relacion.getIdTipoProducto().getIdTipoProducto());
    }

    @Test
    void relacionDuplicadaNoDebePersistir() {
        ProductoTipoProducto relacion = crearRelacionValida();
        simularCantidadRelaciones(1L);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> dao.crear(relacion));

        assertEquals(
                "Este tipo de producto ya está asignado al producto",
                error.getMessage());

        verify(em, never()).persist(any());
    }

    @Test
    void relacionNulaNoDebeAccederAlEntityManager() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(null));

        verifyNoInteractions(em);
    }

    @Test
    void productoNuloNoDebePersistir() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.setProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));

        verifyNoInteractions(em);
    }

    @Test
    void productoSinUuidNoDebePersistir() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.getProducto().setIdProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));

        verifyNoInteractions(em);
    }

    @Test
    void tipoProductoNuloNoDebePersistir() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.setIdTipoProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));

        verifyNoInteractions(em);
    }

    @Test
    void tipoProductoSinUuidNoDebePersistir() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.getIdTipoProducto().setIdTipoProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));

        verifyNoInteractions(em);
    }

    @Test
    void tipoProductoInactivoNoDebePersistir() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.getIdTipoProducto().setActivo(false);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));

        verifyNoInteractions(em);
    }

    @Test
    void tipoProductoConActivoNuloNoDebePersistir() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.getIdTipoProducto().setActivo(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));

        verifyNoInteractions(em);
    }

    @Test
    void relacionValidaConObservacionesNulasDebePersistir() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.setObservaciones(null);
        simularCantidadRelaciones(0L);

        assertDoesNotThrow(() -> dao.crear(relacion));

        verify(em).persist(relacion);
    }
    
@Test
void modificarRelacionDebeSerRechazado() {
    ProductoTipoProducto relacion = crearRelacionValida();

    UnsupportedOperationException error = assertThrows(
            UnsupportedOperationException.class,
            () -> dao.modificar(relacion));

    assertEquals(
            "La relación producto-tipo no admite modificación; "
            + "debe eliminarse y crearse nuevamente",
            error.getMessage());

    verifyNoInteractions(em);
}

}
