package control;

import entity.DescuentoProducto;
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

/**
 * Pruebas de compatibilidad de DescuentoProductoDAO al consultar
 * las asociaciones de un descuento existente.
 *
 * Utiliza Mockito: no requiere PostgreSQL ni Open Liberty.
 */
@ExtendWith(MockitoExtension.class)
class DescuentoProductoDAOTest {

    @Mock
    private EntityManager em;

    @InjectMocks
    private DescuentoProductoDAO dao;

    /** Comprueba que el DAO utiliza la consulta nombrada y el parámetro UUID. */
    @Test
    void debeBuscarProductosPorIdDescuento() {
        UUID idDescuento = UUID.randomUUID();

        @SuppressWarnings("unchecked")
        TypedQuery<DescuentoProducto> consulta = mock(TypedQuery.class);

        List<DescuentoProducto> esperados = List.of(
                new DescuentoProducto(UUID.randomUUID()),
                new DescuentoProducto(UUID.randomUUID())
        );

        when(em.createNamedQuery(
                "DescuentoProducto.findByIdDescuento",
                DescuentoProducto.class
        )).thenReturn(consulta);
        when(consulta.setParameter("idDescuento", idDescuento))
                .thenReturn(consulta);
        when(consulta.getResultList()).thenReturn(esperados);

        List<DescuentoProducto> obtenidos = dao.findByIdDescuento(idDescuento);

        assertSame(esperados, obtenidos);
        assertEquals(2, obtenidos.size());
        verify(em).createNamedQuery(
                "DescuentoProducto.findByIdDescuento",
                DescuentoProducto.class
        );
        verify(consulta).setParameter("idDescuento", idDescuento);
        verify(consulta).getResultList();
        verify(em, never()).persist(any());
        verify(em, never()).remove(any());
    }

    /** Un descuento sin asociaciones debe devolver una lista vacía. */
    @Test
    void descuentoSinProductosDevuelveListaVacia() {
        UUID idDescuento = UUID.randomUUID();

        @SuppressWarnings("unchecked")
        TypedQuery<DescuentoProducto> consulta = mock(TypedQuery.class);

        when(em.createNamedQuery(
                "DescuentoProducto.findByIdDescuento",
                DescuentoProducto.class
        )).thenReturn(consulta);
        when(consulta.setParameter("idDescuento", idDescuento))
                .thenReturn(consulta);
        when(consulta.getResultList()).thenReturn(List.of());

        assertTrue(dao.findByIdDescuento(idDescuento).isEmpty());
        verify(consulta).getResultList();
    }

    /** Un UUID nulo se rechaza antes de acceder al EntityManager. */
    @Test
    void noDebeConsultarConIdDescuentoNulo() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> dao.findByIdDescuento(null)
        );

        assertEquals(
                "El identificador de descuento es obligatorio",
                error.getMessage()
        );
        verifyNoInteractions(em);
    }

    /** Verifica que un fallo de consulta no devuelva silenciosamente una lista vacía. */
    @Test
    void falloAlCrearNamedQueryDebePropagarseSinEscrituras() {
        UUID idDescuento = UUID.randomUUID();
        IllegalStateException fallo =
                new IllegalStateException("Consulta no disponible");

        when(em.createNamedQuery(
                "DescuentoProducto.findByIdDescuento",
                DescuentoProducto.class
        )).thenThrow(fallo);

        IllegalStateException obtenida = assertThrows(
                IllegalStateException.class,
                () -> dao.findByIdDescuento(idDescuento)
        );

        assertSame(fallo, obtenida);
        verify(em, never()).persist(any());
        verify(em, never()).remove(any());
        verify(em, never()).flush();
    }

    /** Un error al leer resultados también debe propagarse sin alterar datos. */
    @Test
    void falloAlLeerResultadosDebePropagarseSinEscrituras() {
        UUID idDescuento = UUID.randomUUID();
        IllegalStateException fallo =
                new IllegalStateException("Error de lectura simulado");

        @SuppressWarnings("unchecked")
        TypedQuery<DescuentoProducto> consulta = mock(TypedQuery.class);

        when(em.createNamedQuery(
                "DescuentoProducto.findByIdDescuento",
                DescuentoProducto.class
        )).thenReturn(consulta);
        when(consulta.setParameter("idDescuento", idDescuento))
                .thenReturn(consulta);
        when(consulta.getResultList()).thenThrow(fallo);

        IllegalStateException obtenida = assertThrows(
                IllegalStateException.class,
                () -> dao.findByIdDescuento(idDescuento)
        );

        assertSame(fallo, obtenida);
        verify(consulta).getResultList();
        verify(em, never()).persist(any());
        verify(em, never()).flush();
    }
}
