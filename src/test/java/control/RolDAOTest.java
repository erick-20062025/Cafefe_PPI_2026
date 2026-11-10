
package control;

import entity.Rol;
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
public class RolDAOTest {

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<Rol> query;

    @InjectMocks
    private RolDAO dao;

    private Rol rolValido() {
        Rol rol = new Rol();
        rol.setIdRol(UUID.randomUUID());
        rol.setNombre("Administrador");
        rol.setActivo(true);
        return rol;
    }

    @Test
    void debeBuscarPorId() {

        UUID id = UUID.randomUUID();
        Rol rol = new Rol(id);

        when(em.find(Rol.class, id)).thenReturn(rol);

        assertSame(rol, dao.findById(id));
        verify(em).find(Rol.class, id);
    }

    @Test
    void idInvalidoDebeRechazarse() {

        assertThrows(IllegalArgumentException.class,
                () -> dao.findById(null));

        verifyNoInteractions(em);
    }

    @Test
    void debeUsarNamedQueryParaListar() {

        Rol rol = rolValido();

        when(em.createNamedQuery(
                "Rol.findAll", Rol.class))
                .thenReturn(query);

        when(query.getResultList())
                .thenReturn(List.of(rol));

        List<Rol> resultado = dao.findAll();

        assertEquals(1, resultado.size());
        assertEquals("Administrador",
                resultado.get(0).getNombre());
    }

    @Test
    void rolValidoDebePersistirse() {

        Rol rol = rolValido();

        dao.crear(rol);

        verify(em).persist(rol);
    }

    @Test
    void nombreVacioDebeRechazarse() {

        Rol rol = rolValido();
        rol.setNombre("   ");

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(rol));

        verifyNoInteractions(em);
    }

    @Test
    void nombreDemasiadoLargoDebeRechazarse() {

        Rol rol = rolValido();
        rol.setNombre("A".repeat(156));

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(rol));

        verifyNoInteractions(em);
    }

    @Test
    void estadoNuloDebeRechazarse() {

        Rol rol = rolValido();
        rol.setActivo(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(rol));

        verifyNoInteractions(em);
    }

    @Test
    void rolInexistenteNoDebeModificarse() {

        Rol rol = rolValido();

        assertThrows(IllegalArgumentException.class,
                () -> dao.modificar(rol));

        verify(em, never()).merge(any());
    }

    @Test
    void rolExistenteDebeModificarse() {

        Rol rol = rolValido();

        when(em.find(Rol.class, rol.getIdRol()))
                .thenReturn(rol);

        dao.modificar(rol);

        verify(em).merge(rol);
    }

    @Test
    void rolExistenteDebeEliminarse() {

        Rol rol = rolValido();

        when(em.find(Rol.class, rol.getIdRol()))
                .thenReturn(rol);

        dao.eliminar(rol);

        verify(em).remove(rol);
    }

    @Test
    void rolNuloNoDebeEliminarse() {

        assertThrows(IllegalArgumentException.class,
                () -> dao.eliminar(null));

        verifyNoInteractions(em);
    }
}
