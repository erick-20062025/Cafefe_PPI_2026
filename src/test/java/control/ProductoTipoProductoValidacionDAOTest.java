
package control;

import entity.Producto;
import entity.ProductoTipoProducto;
import entity.TipoProducto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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

        lenient().when(em.find(Producto.class, producto.getIdProducto(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(producto);
        lenient().when(em.find(TipoProducto.class, tipo.getIdTipoProducto(), LockModeType.PESSIMISTIC_READ)).thenReturn(tipo);
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

        verify(em, never()).persist(any());
    }

    @Test
    void tipoProductoConActivoNuloNoDebePersistir() {
        ProductoTipoProducto relacion = crearRelacionValida();
        relacion.getIdTipoProducto().setActivo(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));

        verify(em, never()).persist(any());
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


    @Test void referenciasRealesYOrdenDeBloqueos() {
        ProductoTipoProducto r = crearRelacionValida();
        Producto real = new Producto(r.getProducto().getIdProducto());
        real.setActivo(false);
        TipoProducto tipo = new TipoProducto(r.getIdTipoProducto().getIdTipoProducto());
        tipo.setActivo(true);
        when(em.find(Producto.class, real.getIdProducto(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(real);
        when(em.find(TipoProducto.class, tipo.getIdTipoProducto(), LockModeType.PESSIMISTIC_READ)).thenReturn(tipo);
        r.getIdTipoProducto().setActivo(false); // No confiar en estado antiguo del cliente JSF.
        simularCantidadRelaciones(0L);
        dao.crear(r);
        assertSame(real, r.getProducto());
        assertSame(tipo, r.getIdTipoProducto());
        var orden = inOrder(em, query);
        orden.verify(em).find(Producto.class, real.getIdProducto(), LockModeType.PESSIMISTIC_WRITE);
        orden.verify(em).find(TipoProducto.class, tipo.getIdTipoProducto(), LockModeType.PESSIMISTIC_READ);
        orden.verify(em).refresh(tipo, LockModeType.PESSIMISTIC_READ);
        orden.verify(query).getSingleResult();
        orden.verify(em).persist(r);
        orden.verify(em).flush();
    }

    @Test void estadoRealInactivoRechazaAunqueClienteDigaActivo() {
        ProductoTipoProducto r = crearRelacionValida();
        TipoProducto real = new TipoProducto(r.getIdTipoProducto().getIdTipoProducto());
        real.setActivo(false);
        when(em.find(TipoProducto.class, real.getIdTipoProducto(), LockModeType.PESSIMISTIC_READ)).thenReturn(real);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(r));
        verify(em, never()).persist(any());
    }

    @Test void referenciasInexistentesNoPersisten() {
        ProductoTipoProducto r = crearRelacionValida();
        when(em.find(Producto.class, r.getProducto().getIdProducto(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
        assertEquals("El producto seleccionado no existe", assertThrows(IllegalArgumentException.class, () -> dao.crear(r)).getMessage());
        ProductoTipoProducto otra = crearRelacionValida();
        when(em.find(TipoProducto.class, otra.getIdTipoProducto().getIdTipoProducto(), LockModeType.PESSIMISTIC_READ)).thenReturn(null);
        assertEquals("El tipo de producto seleccionado no existe", assertThrows(IllegalArgumentException.class, () -> dao.crear(otra)).getMessage());
        verify(em, never()).persist(any());
    }

    @Test void identificadorObligatorioYNoRepetido() {
        ProductoTipoProducto r = crearRelacionValida();
        when(em.find(ProductoTipoProducto.class, r.getIdProductoTipoProducto())).thenReturn(r);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(r));
        r.setIdProductoTipoProducto(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(r));
        verify(em, never()).persist(any());
    }

    @Test void eliminaSoloAsociacionAdministrada() {
        ProductoTipoProducto entrada = crearRelacionValida();
        ProductoTipoProducto actual = new ProductoTipoProducto(entrada.getIdProductoTipoProducto());
        when(em.find(ProductoTipoProducto.class, entrada.getIdProductoTipoProducto(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(actual);
        dao.eliminar(entrada);
        verify(em).remove(actual);
        verify(em).flush();
        verify(em, never()).merge(any());
        verify(em, never()).remove(isA(Producto.class));
        verify(em, never()).remove(isA(TipoProducto.class));
    }

    @Test void eliminarInvalidaOAusenteNoBorra() {
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(null));
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(new ProductoTipoProducto()));
        assertEquals("La asociación ya no existe", assertThrows(IllegalArgumentException.class,
                () -> dao.eliminar(new ProductoTipoProducto(UUID.randomUUID()))).getMessage());
        verify(em, never()).remove(any());
    }
}
