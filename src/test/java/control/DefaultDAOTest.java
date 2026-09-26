package control;

import entity.Rol;
import jakarta.persistence.EntityManager;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultDAOTest {

    private EntityManager entityManager;
    private DefaultDAO<Rol> dao;

    @BeforeEach
    void setUp() {

        entityManager = mock(EntityManager.class);

        dao = new DefaultDAO<Rol>(Rol.class) {

            @Override
            protected EntityManager getEntityManager() {
                return entityManager;
            }
        };
    }

    @Test
    void debeCrearRegistroUsandoEntityManager() {

        Rol rol = new Rol();

        dao.crear(rol);

        verify(entityManager).persist(rol);
    }

    @Test
    void debeModificarRegistroUsandoEntityManager() {

        Rol rol = new Rol();

        dao.modificar(rol);

        verify(entityManager).merge(rol);
    }

    @Test
    void debeEliminarRegistroUsandoEntityManager() {

        Rol rol = new Rol();
        Rol rolAdministrado = new Rol();

        when(entityManager.merge(rol)).thenReturn(rolAdministrado);

        dao.eliminar(rol);

        verify(entityManager).merge(rol);
        verify(entityManager).remove(rolAdministrado);
    }

    @Test
    void debeBuscarRegistroPorId() {

        UUID id = UUID.randomUUID();
        Rol rol = new Rol();

        when(entityManager.find(Rol.class, id)).thenReturn(rol);

        Rol resultado = dao.findById(id);

        assertSame(rol, resultado);
        verify(entityManager).find(Rol.class, id);
    }
}