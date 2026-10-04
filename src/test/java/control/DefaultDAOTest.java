package control;

import entity.Rol;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.TypedQuery;
import java.util.List;
import jakarta.persistence.EntityManager;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
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

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void debeConsultarTodasLasEntidadesYDevolverListaVacia() {
        CriteriaBuilder builder =
                mock(CriteriaBuilder.class);
        CriteriaQuery criteria =
                mock(CriteriaQuery.class);
        Root<Rol> root =
                mock(Root.class);
        TypedQuery<Rol> query =
                mock(TypedQuery.class);
        List<Rol> roles = List.of(new Rol(), new Rol());

        when(entityManager.getCriteriaBuilder()).thenReturn(builder);
        when(builder.createQuery()).thenReturn(criteria);
        when(criteria.from(Rol.class)).thenReturn(root);
        when(entityManager.createQuery(criteria)).thenReturn(query);
        when(query.getResultList()).thenReturn(roles, List.of());

        assertSame(roles, dao.findAll());
        assertTrue(dao.findAll().isEmpty());
        verify(criteria, times(2)).from(Rol.class);
        verify(criteria, times(2)).select(root);
        verify(query, times(2)).getResultList();
    }
}
