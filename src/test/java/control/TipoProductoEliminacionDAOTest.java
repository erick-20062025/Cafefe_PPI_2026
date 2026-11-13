package control;

import entity.TipoProducto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TipoProductoEliminacionDAOTest {
    @Mock EntityManager em;
    @Mock TypedQuery<Long> conteo;
    @Mock Query borrado;
    @InjectMocks TipoProductoDAO dao;
    final UUID id = UUID.randomUUID();

    private TipoProducto preparar(Long... cantidades) {
        TipoProducto registro = new TipoProducto(id);
        when(em.find(TipoProducto.class, id, LockModeType.PESSIMISTIC_WRITE)).thenReturn(registro);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(conteo);
        when(conteo.setParameter("id", id)).thenReturn(conteo);
        when(conteo.getSingleResult()).thenReturn(cantidades[0],
                java.util.Arrays.copyOfRange(cantidades, 1, cantidades.length));
        return registro;
    }

    @Test void entradasInvalidasNoTocanPersistencia() {
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(null));
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(new TipoProducto()));
        assertThrows(IllegalArgumentException.class, () -> dao.countRelaciones(null));
        verifyNoInteractions(em);
    }

    @Test void ausenteNoIntentaEliminar() {
        assertEquals("El tipo de producto ya no existe", assertThrows(IllegalArgumentException.class,
                () -> dao.eliminar(new TipoProducto(id))).getMessage());
        verify(em, never()).createQuery(anyString());
        verify(em, never()).remove(any());
    }

    @Test
    void asociacionImpideBorrar() {
        TipoProducto registro = preparar(1L);
        assertEquals("No se puede eliminar un tipo de producto que tiene productos asociados",
                assertThrows(IllegalArgumentException.class, () -> dao.eliminar(registro)).getMessage());
        verify(em, never()).createQuery(anyString());
        verify(em, never()).remove(any());
        verify(em, never()).merge(any());
    }

    private TipoProducto prepararBorrado(int filas) {
        TipoProducto registro = preparar(0L);
        when(em.createQuery(anyString())).thenReturn(borrado);
        when(borrado.setParameter("id", id)).thenReturn(borrado);
        when(borrado.executeUpdate()).thenReturn(filas);
        return registro;
    }

    @Test void borraSinCascadaYConCondicionDeAusencia() {
        TipoProducto registro = prepararBorrado(1);
        dao.eliminar(registro);
        var orden = inOrder(em, borrado);
        orden.verify(em).find(TipoProducto.class, id, LockModeType.PESSIMISTIC_WRITE);
        orden.verify(em).flush();
        orden.verify(em).createQuery(argThat((String sql) -> sql.startsWith("DELETE FROM TipoProducto")
                && sql.contains("ProductoTipoProducto")
                && sql.contains("r.idTipoProducto.idTipoProducto = :id")
                && sql.split("NOT EXISTS", -1).length == 2));
        orden.verify(borrado).setParameter("id", id);
        orden.verify(borrado).executeUpdate();
        orden.verify(em).detach(registro);
        verify(em, never()).remove(any());
        verify(em, never()).merge(any());
    }

    @Test void relacionAparecidaAntesDelDeleteImpideExito() {
        TipoProducto registro = prepararBorrado(0);
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(registro));
        verify(em, never()).detach(any());
    }

    @Test void falloDeIntegridadSePropagaParaRollbackDelEjb() {
        TipoProducto registro = prepararBorrado(1);
        var fallo = new jakarta.persistence.PersistenceException("FK");
        when(borrado.executeUpdate()).thenThrow(fallo);
        assertSame(fallo, assertThrows(jakarta.persistence.PersistenceException.class,
                () -> dao.eliminar(registro)));
        verify(em, never()).detach(any());
    }
}
