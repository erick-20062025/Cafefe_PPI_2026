    
package control;

import entity.Caja;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CajaDAOTest {

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<Caja> consultaCajas;

    @Mock
    private TypedQuery<Long> consultaConteo;

    @InjectMocks
    private CajaDAO dao;

    private Caja crearCajaValida() {
        Caja caja = new Caja();
        caja.setIdCaja(UUID.randomUUID());
        caja.setNombre("Caja Principal");
        caja.setActivo(true);
        return caja;
    }

    @Test
    void debeBuscarCajaPorId() {
        Caja caja = crearCajaValida();

        when(em.find(Caja.class, caja.getIdCaja()))
                .thenReturn(caja);

        assertSame(caja, dao.findById(caja.getIdCaja()));
    }

    @Test
    void idNuloDebeRechazarse() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.findById(null));

        verifyNoInteractions(em);
    }

    @Test
    void debeListarMedianteNamedQuery() {
        Caja caja = crearCajaValida();

        when(em.createNamedQuery(
                "Caja.findAll", Caja.class))
                .thenReturn(consultaCajas);

        when(consultaCajas.getResultList())
                .thenReturn(List.of(caja));

        assertEquals(1, dao.findAll().size());
    }

    @Test
    void cajaValidaDebePersistirse() {
        Caja caja = crearCajaValida();

        dao.crear(caja);

        verify(em).persist(caja);
    }

    @Test
    void cajaNulaDebeRechazarse() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(null));

        verifyNoInteractions(em);
    }

    @Test
    void nombreVacioDebeRechazarse() {
        Caja caja = crearCajaValida();
        caja.setNombre("   ");

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(caja));

        verifyNoInteractions(em);
    }

    @Test
    void nombreMayorA155DebeRechazarse() {
        Caja caja = crearCajaValida();
        caja.setNombre("A".repeat(156));

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(caja));

        verifyNoInteractions(em);
    }

    @Test
    void estadoNuloDebeRechazarse() {
        Caja caja = crearCajaValida();
        caja.setActivo(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(caja));

        verifyNoInteractions(em);
    }

    @Test
    void cajaDuplicadaPorIdNoDebePersistirse() {
        Caja caja = crearCajaValida();

        when(em.find(Caja.class, caja.getIdCaja()))
                .thenReturn(caja);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(caja));

        verify(em, never()).persist(any());
    }

    @Test
    void cajaExistenteDebeModificarse() {
        Caja caja = crearCajaValida();

        when(em.find(Caja.class, caja.getIdCaja()))
                .thenReturn(caja);

        dao.modificar(caja);

        verify(em).merge(caja);
    }

    @Test
    void cajaInexistenteNoDebeModificarse() {
        Caja caja = crearCajaValida();

        assertThrows(IllegalArgumentException.class,
                () -> dao.modificar(caja));

        verify(em, never()).merge(any());
    }

    private void configurarConteo(UUID idCaja, long cantidad) {

        when(em.createNamedQuery(
                "Caja.countFacturas", Long.class))
                .thenReturn(consultaConteo);

        when(consultaConteo.setParameter("idCaja", idCaja))
                .thenReturn(consultaConteo);

        when(consultaConteo.getSingleResult())
                .thenReturn(cantidad);
    }

    @Test
    void cajaSinFacturasDebeEliminarse() {
        Caja caja = crearCajaValida();

        when(em.find(Caja.class, caja.getIdCaja()))
                .thenReturn(caja);

        configurarConteo(caja.getIdCaja(), 0L);

        dao.eliminar(caja);

        verify(em).remove(caja);
        verify(em).flush();
    }

    @Test
    void cajaConFacturasNoDebeEliminarse() {
        Caja caja = crearCajaValida();

        when(em.find(Caja.class, caja.getIdCaja()))
                .thenReturn(caja);

        configurarConteo(caja.getIdCaja(), 2L);

        assertThrows(IllegalArgumentException.class,
                () -> dao.eliminar(caja));

        verify(em, never()).remove(any());
        verify(em, never()).flush();
    }

    @Test
    void cajaInexistenteNoDebeEliminarse() {
        Caja caja = crearCajaValida();

        assertThrows(IllegalArgumentException.class,
                () -> dao.eliminar(caja));

        verify(em, never()).remove(any());
    }

    @Test
    void cajaNulaNoDebeEliminarse() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.eliminar(null));

        verifyNoInteractions(em);
    }
}
