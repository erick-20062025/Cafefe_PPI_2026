package control;

import entity.Caracteristica;
import entity.Producto;
import entity.ProductoCaracteristica;
import entity.TipoCaracteristica;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.persistence.TypedQuery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ProductoCaracteristicaValidacionDAOTest {

    private ProductoCaracteristicaDAO dao;
    private EntityManager em;

    @BeforeEach
    void configurar() throws Exception {
        dao = new ProductoCaracteristicaDAO();
        em = mock(EntityManager.class);

        TypedQuery<Long> consulta = mock(TypedQuery.class);

        when(em.createQuery(
                anyString(),
                eq(Long.class)
        )).thenReturn(consulta);

        when(consulta.setParameter(anyString(), any()))
                .thenReturn(consulta);

        when(consulta.getSingleResult())
                .thenReturn(0L);

        Field campo = ProductoCaracteristicaDAO.class
                .getDeclaredField("em");
        campo.setAccessible(true);
        campo.set(dao, em);
    }

    private ProductoCaracteristica crearRelacionValida() {
        Producto producto = new Producto();
        producto.setIdProducto(UUID.randomUUID());

        TipoCaracteristica tipo = new TipoCaracteristica();
        tipo.setActivo(true);

        Caracteristica caracteristica = new Caracteristica();
        caracteristica.setIdCaracteristica(UUID.randomUUID());
        caracteristica.setActivo(true);
        caracteristica.setIdTipoCaracteristica(tipo);

        ProductoCaracteristica relacion = new ProductoCaracteristica();
        relacion.setIdProductoCaracteristica(UUID.randomUUID());
        relacion.setIdProducto(producto);
        relacion.setIdCaracteristica(caracteristica);
        relacion.setValor("600 ml");

        return relacion;
    }

    @Test
    void crearRelacionValidaDebePersistir() {
        ProductoCaracteristica relacion = crearRelacionValida();

        dao.crear(relacion);

        verify(em).persist(relacion);
    }

    @Test
    void crearRelacionSinProductoNoDebePersistir() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.setIdProducto(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));

        verify(em, never()).persist(any());
    }

    @Test
    void crearRelacionDuplicadaNoDebePersistir() {
        ProductoCaracteristica relacion = crearRelacionValida();

        TypedQuery<Long> consulta = mock(TypedQuery.class);

        when(em.createQuery(anyString(), eq(Long.class)))
                .thenReturn(consulta);

        when(consulta.setParameter(anyString(), any()))
                .thenReturn(consulta);

        when(consulta.getSingleResult())
                .thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));

        verify(em, never()).persist(any());
    }

    @Test
    void modificarRelacionExistenteDebePermitirMerge() {
        ProductoCaracteristica relacion = crearRelacionValida();

        dao.modificar(relacion);

        verify(em).merge(relacion);

        verify(em, never()).persist(any());
    }

    @Test
    void crearRelacionSinValorNoDebePersistir() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.setValor(" ");

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));

        verify(em, never()).persist(any());
    }

    @Test
    void crearRelacionConCaracteristicaInactivaNoDebePersistir() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.getIdCaracteristica().setActivo(false);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));

        verify(em, never()).persist(any());
    }

    @Test
    void crearRelacionConFormatoIncorrectoNoDebePersistir() {
        ProductoCaracteristica relacion = crearRelacionValida();

        relacion.getIdCaracteristica()
                .getIdTipoCaracteristica()
                .setExpresionRegular("^[0-9]+$");

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));

        verify(em, never()).persist(any());
    }

    @Test
    void modificarRelacionValidaDebeEjecutarMerge() {
        ProductoCaracteristica relacion = crearRelacionValida();

        dao.modificar(relacion);

        verify(em).merge(relacion);
    }

    @Test
    void modificarRelacionInvalidaNoDebeEjecutarMerge() {
        ProductoCaracteristica relacion = crearRelacionValida();
        relacion.setValor(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.modificar(relacion));

        verify(em, never()).merge(any());
    }
}
