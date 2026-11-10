
package control;

import entity.TipoCaracteristica;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TipoCaracteristicaValidacionDAOTest {

    private TipoCaracteristica crearTipoValido() {
        TipoCaracteristica tipo = new TipoCaracteristica();
        tipo.setIdTipoCaracteristica(UUID.randomUUID());
        tipo.setNombre("Tamaño");
        tipo.setActivo(true);
        tipo.setExpresionRegular("^(Pequeño|Mediano|Grande)$");
        return tipo;
    }

    @Test
    void crearNoDebePersistirDatosInvalidos() {
        EntityManager em = mock(EntityManager.class);

        TipoCaracteristicaDAO dao = new TipoCaracteristicaDAO() {
            @Override
            protected EntityManager getEntityManager() {
                return em;
            }
        };

        TipoCaracteristica tipo = crearTipoValido();
        tipo.setNombre(" ");

        assertThrows(
            IllegalArgumentException.class,
            () -> dao.crear(tipo)
        );

        verify(em, never()).persist(any());
    }

    @Test
    void modificarNoDebeGuardarExpresionInvalida() {
        EntityManager em = mock(EntityManager.class);

        TipoCaracteristicaDAO dao = new TipoCaracteristicaDAO() {
            @Override
            protected EntityManager getEntityManager() {
                return em;
            }
        };

        TipoCaracteristica tipo = crearTipoValido();
        tipo.setExpresionRegular("[abc");

        assertThrows(
            IllegalArgumentException.class,
            () -> dao.modificar(tipo)
        );

        verify(em, never()).merge(any());
    }

    @Test
    void crearDebePersistirDatosValidos() {
        EntityManager em = mock(EntityManager.class);

        TipoCaracteristicaDAO dao = new TipoCaracteristicaDAO() {
            @Override
            protected EntityManager getEntityManager() {
                return em;
            }
        };

        TipoCaracteristica tipo = crearTipoValido();

        assertDoesNotThrow(() -> dao.crear(tipo));

        verify(em).persist(tipo);
    }

    @Test
    void modificarDebeGuardarDatosValidos() {
        EntityManager em = mock(EntityManager.class);

        TipoCaracteristicaDAO dao = new TipoCaracteristicaDAO() {
            @Override
            protected EntityManager getEntityManager() {
                return em;
            }
        };

        TipoCaracteristica tipo = crearTipoValido();

        assertDoesNotThrow(() -> dao.modificar(tipo));

        verify(em).merge(tipo);
    }
}
