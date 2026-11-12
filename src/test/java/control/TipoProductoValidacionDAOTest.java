
package control;

import entity.TipoProducto;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TipoProductoValidacionDAOTest {

    private TipoProductoDAO dao;
    private EntityManager em;

    @BeforeEach
    void setUp() throws Exception {
        dao = new TipoProductoDAO();
        em = mock(EntityManager.class);

        Field campo = TipoProductoDAO.class.getDeclaredField("em");
        campo.setAccessible(true);
        campo.set(dao, em);
    }

    private TipoProducto crearTipoProductoValido() {
        TipoProducto tipo = new TipoProducto();
        tipo.setIdTipoProducto(UUID.randomUUID());
        tipo.setNombre("Bebidas");
        tipo.setActivo(true);
        return tipo;
    }

    @Test
    void crearTipoProductoValidoDebePersistir() {
        TipoProducto tipo = crearTipoProductoValido();

        assertDoesNotThrow(() -> dao.crear(tipo));

        verify(em).persist(tipo);
        verifyNoMoreInteractions(em);
    }

    @Test
    void crearConNombreNuloNoDebePersistir() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setNombre(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void crearConNombreVacioNoDebePersistir() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setNombre("   ");

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void crearConNombreMuyLargoNoDebePersistir() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setNombre("A".repeat(156));

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void crearConActivoNuloNoDebePersistir() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setActivo(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void modificarTipoProductoValidoDebeHacerMerge() {
        TipoProducto tipo = crearTipoProductoValido();

        assertDoesNotThrow(() -> dao.modificar(tipo));

        verify(em).merge(tipo);
        verifyNoMoreInteractions(em);
    }

    @Test
    void modificarConNombreNuloNoDebeHacerMerge() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setNombre(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.modificar(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void modificarConNombreMuyLargoNoDebeHacerMerge() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setNombre("A".repeat(156));

        assertThrows(IllegalArgumentException.class,
                () -> dao.modificar(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void modificarConActivoNuloNoDebeHacerMerge() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setActivo(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.modificar(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void crearTipoProductoInactivoDebePersistir() {
        TipoProducto tipo = crearTipoProductoValido();
        tipo.setActivo(false);

        assertDoesNotThrow(() -> dao.crear(tipo));

        verify(em).persist(tipo);
        verifyNoMoreInteractions(em);
    }
}
