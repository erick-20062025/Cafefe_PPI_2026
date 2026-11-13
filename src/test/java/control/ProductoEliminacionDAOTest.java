package control;

import entity.Producto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoEliminacionDAOTest {
    @Mock EntityManager em;
    @Mock TypedQuery<Long> conteo;
    @Mock Query borrado;
    @InjectMocks ProductoDAO dao;
    final UUID id = UUID.randomUUID();

    private Producto preparar(Long... cantidades) {
        Producto producto = new Producto(id);
        when(em.find(Producto.class, id, LockModeType.PESSIMISTIC_WRITE)).thenReturn(producto);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(conteo);
        when(conteo.setParameter("id", id)).thenReturn(conteo);
        when(conteo.getSingleResult()).thenReturn(cantidades[0],
                java.util.Arrays.copyOfRange(cantidades, 1, cantidades.length));
        return producto;
    }

    @Test void entradasInvalidasNoTocanPersistencia() {
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(null));
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(new Producto()));
        assertThrows(IllegalArgumentException.class, () -> dao.countRelaciones(null));
        verifyNoInteractions(em);
    }

    @Test void ausenteNoIntentaEliminar() {
        assertEquals("El producto ya no existe", assertThrows(IllegalArgumentException.class,
                () -> dao.eliminar(new Producto(id))).getMessage());
        verify(em, never()).createQuery(anyString());
        verify(em, never()).remove(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void cualquieraDeLasCuatroRelacionesImpideBorrar(int indice) {
        Long[] cantidades = {0L, 0L, 0L, 0L};
        cantidades[indice] = 1L;
        Producto producto = preparar(cantidades);
        assertEquals("No se puede eliminar un producto que tiene relaciones asociadas",
                assertThrows(IllegalArgumentException.class, () -> dao.eliminar(producto)).getMessage());
        verify(em, never()).createQuery(anyString());
        verify(em, never()).remove(any());
        verify(em, never()).merge(any());
    }

    private Producto prepararBorrado(int filas) {
        Producto producto = preparar(0L, 0L, 0L, 0L);
        when(em.createQuery(anyString())).thenReturn(borrado);
        when(borrado.setParameter("id", id)).thenReturn(borrado);
        when(borrado.executeUpdate()).thenReturn(filas);
        return producto;
    }

    @Test void borraSinCascadaYConLasCuatroCondiciones() {
        Producto producto = prepararBorrado(1);
        dao.eliminar(producto);
        var orden = inOrder(em, borrado);
        orden.verify(em).find(Producto.class, id, LockModeType.PESSIMISTIC_WRITE);
        orden.verify(em).flush();
        orden.verify(em).createQuery(argThat((String sql) -> sql.startsWith("DELETE FROM Producto")
                && sql.contains("OrdenProducto") && sql.contains("DescuentoProducto")
                && sql.contains("ProductoCaracteristica") && sql.contains("ProductoTipoProducto")
                && sql.split("NOT EXISTS", -1).length == 5));
        orden.verify(borrado).setParameter("id", id);
        orden.verify(borrado).executeUpdate();
        orden.verify(em).detach(producto);
        verify(em, never()).remove(any());
        verify(em, never()).merge(any());
    }

    @Test void relacionAparecidaAntesDelDeleteImpideExito() {
        Producto producto = prepararBorrado(0);
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(producto));
        verify(em, never()).detach(any());
    }

    @Test void falloDeIntegridadSePropagaParaRollbackDelEjb() {
        Producto producto = prepararBorrado(1);
        var fallo = new jakarta.persistence.PersistenceException("FK");
        when(borrado.executeUpdate()).thenThrow(fallo);
        assertSame(fallo, assertThrows(jakarta.persistence.PersistenceException.class,
                () -> dao.eliminar(producto)));
        verify(em, never()).detach(any());
    }
}
