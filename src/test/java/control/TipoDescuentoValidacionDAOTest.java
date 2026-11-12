
package control;

import entity.TipoDescuento;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TipoDescuentoValidacionDAOTest {

    private TipoDescuentoDAO dao;
    private EntityManager em;

    @BeforeEach
    void configurar() throws Exception {
        dao = new TipoDescuentoDAO();
        em = mock(EntityManager.class);

        Field campo = TipoDescuentoDAO.class.getDeclaredField("em");
        campo.setAccessible(true);
        campo.set(dao, em);
    }

    private TipoDescuento crearTipoValido() {
        TipoDescuento tipo = new TipoDescuento();
        tipo.setIdTipoDescuento(UUID.randomUUID());
        tipo.setNombre("Descuento promocional");
        tipo.setActivo(true);
        tipo.setDescuentoMaximo(25);
        return tipo;
    }

    @Test
    void crearTipoValidoDebePersistir() {
        TipoDescuento tipo = crearTipoValido();

        dao.crear(tipo);

        verify(em).persist(tipo);
    }

    @Test
    void crearTipoNuloDebeSerRechazado() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(null));

        verifyNoInteractions(em);
    }

    @Test
    void crearTipoSinIdDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setIdTipoDescuento(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void crearTipoSinNombreDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setNombre("   ");

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void crearTipoSinEstadoDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setActivo(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void crearTipoSinDescuentoMaximoDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setDescuentoMaximo(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void crearTipoConDescuentoNegativoDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setDescuentoMaximo(-1);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void crearTipoConDescuentoMayorA100DebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setDescuentoMaximo(101);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void modificarTipoValidoDebeEjecutarMerge() {
        TipoDescuento tipo = crearTipoValido();

        dao.modificar(tipo);

        verify(em).merge(tipo);
    }

    @Test
    void modificarTipoInvalidoDebeSerRechazado() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setDescuentoMaximo(150);

        assertThrows(IllegalArgumentException.class,
                () -> dao.modificar(tipo));

        verifyNoInteractions(em);
    }

    @Test
    void modificarTipoInactivoDebeSerPermitido() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setActivo(false);

        dao.modificar(tipo);

        verify(em).merge(tipo);
    }

    @Test
    void crearTipoConDescuentoCeroDebeSerPermitido() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setDescuentoMaximo(0);

        dao.crear(tipo);

        verify(em).persist(tipo);
    }

    @Test
    void crearTipoConDescuento100DebeSerPermitido() {
        TipoDescuento tipo = crearTipoValido();
        tipo.setDescuentoMaximo(100);

        dao.crear(tipo);

        verify(em).persist(tipo);
    }
}
