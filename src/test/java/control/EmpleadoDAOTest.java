package control;

import entity.Empleado;
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

/** Pruebas de persistencia y validaciones del DAO de empleados. */
@ExtendWith(MockitoExtension.class)
public class EmpleadoDAOTest {

    @Mock private EntityManager em;
    @Mock private TypedQuery<Empleado> consultaEmpleados;
    @Mock private TypedQuery<Long> consultaConteo;
    @InjectMocks private EmpleadoDAO dao;

    @Test
    void debeBuscarEmpleadoPorId() {
        UUID id = UUID.randomUUID();
        Empleado registro = empleadoValido();
        when(em.find(Empleado.class, id)).thenReturn(registro);

        assertSame(registro, dao.findById(id));
        verify(em).find(Empleado.class, id);
    }

    @Test
    void debeRechazarIdentificadorNuloODeOtroTipo() {
        assertThrows(IllegalArgumentException.class, () -> dao.findById(null));
        assertThrows(IllegalArgumentException.class, () -> dao.findById("no-uuid"));
        verifyNoInteractions(em);
    }

    @Test
    void listarDebeUsarNamedQuery() {
        Empleado empleado = empleadoValido();
        when(em.createNamedQuery("Empleado.findAll", Empleado.class))
                .thenReturn(consultaEmpleados);
        when(consultaEmpleados.getResultList()).thenReturn(List.of(empleado));

        assertEquals(List.of(empleado), dao.findAll());
        verify(em).createNamedQuery("Empleado.findAll", Empleado.class);
    }

    @Test
    void crearEmpleadoValidoDebePersistirYNormalizarEspacios() {
        Empleado empleado = empleadoValido();
        empleado.setNombre("  Ana  ");
        empleado.setApellido("  López ");

        dao.crear(empleado);

        assertEquals("Ana", empleado.getNombre());
        assertEquals("López", empleado.getApellido());
        verify(em).persist(same(empleado));
    }

    @Test
    void crearEmpleadoNuloDebeRechazarse() {
        assertThrows(IllegalArgumentException.class, () -> dao.crear(null));
        verifyNoInteractions(em);
    }

    @Test
    void crearEmpleadoSinIdDebeRechazarse() {
        Empleado empleado = empleadoValido();
        empleado.setIdEmpleado(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(empleado));
        verifyNoInteractions(em);
    }

    @Test
    void crearEmpleadoSinNombreDebeRechazarse() {
        Empleado empleado = empleadoValido();
        empleado.setNombre(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(empleado));
        verifyNoInteractions(em);
    }

    @Test
    void nombreSoloConEspaciosDebeRechazarse() {
        Empleado empleado = empleadoValido();
        empleado.setNombre("   ");
        assertThrows(IllegalArgumentException.class, () -> dao.crear(empleado));
        verifyNoInteractions(em);
    }

    @Test
    void nombreDeUnCaracterDebeRechazarse() {
        Empleado empleado = empleadoValido();
        empleado.setNombre("A");
        assertThrows(IllegalArgumentException.class, () -> dao.crear(empleado));
        verifyNoInteractions(em);
    }

    @Test
    void nombreMayorA255DebeRechazarse() {
        Empleado empleado = empleadoValido();
        empleado.setNombre("A".repeat(256));
        assertThrows(IllegalArgumentException.class, () -> dao.crear(empleado));
        verifyNoInteractions(em);
    }

    @Test
    void apellidoNuloDebeRechazarse() {
        Empleado empleado = empleadoValido();
        empleado.setApellido(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(empleado));
        verifyNoInteractions(em);
    }

    @Test
    void apellidoVacioDebeRechazarse() {
        Empleado empleado = empleadoValido();
        empleado.setApellido("  ");
        assertThrows(IllegalArgumentException.class, () -> dao.crear(empleado));
        verifyNoInteractions(em);
    }

    @Test
    void apellidoMuyCortoDebeRechazarse() {
        Empleado empleado = empleadoValido();
        empleado.setApellido("X");
        assertThrows(IllegalArgumentException.class, () -> dao.crear(empleado));
        verifyNoInteractions(em);
    }

    @Test
    void apellidoMayorA255DebeRechazarse() {
        Empleado empleado = empleadoValido();
        empleado.setApellido("B".repeat(256));
        assertThrows(IllegalArgumentException.class, () -> dao.crear(empleado));
        verifyNoInteractions(em);
    }

    @Test
    void estadoNuloDebeRechazarse() {
        Empleado empleado = empleadoValido();
        empleado.setActivo(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(empleado));
        verifyNoInteractions(em);
    }

    @Test
    void noDebeCrearEmpleadoConIdExistente() {
        Empleado empleado = empleadoValido();
        when(em.find(Empleado.class, empleado.getIdEmpleado())).thenReturn(empleado);

        assertThrows(IllegalArgumentException.class, () -> dao.crear(empleado));

        verify(em, never()).persist(any());
    }

    @Test
    void debeModificarEmpleadoExistente() {
        Empleado empleado = empleadoValido();
        when(em.find(Empleado.class, empleado.getIdEmpleado())).thenReturn(empleado);

        dao.modificar(empleado);

        verify(em).merge(same(empleado));
    }

    @Test
    void noDebeModificarEmpleadoInexistente() {
        Empleado empleado = empleadoValido();

        assertThrows(IllegalArgumentException.class, () -> dao.modificar(empleado));

        verify(em, never()).merge(any());
    }

    @Test
    void noDebeEliminarEmpleadoNulo() {
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(null));
        verifyNoInteractions(em);
    }

    @Test
    void noDebeEliminarEmpleadoSinId() {
        Empleado empleado = empleadoValido();
        empleado.setIdEmpleado(null);
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(empleado));
        verifyNoInteractions(em);
    }

    @Test
    void noDebeEliminarEmpleadoInexistente() {
        Empleado empleado = empleadoValido();
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(empleado));
        verify(em, never()).remove(any());
    }

    @Test
    void noDebeEliminarEmpleadoConRolesAsociados() {
        Empleado empleado = empleadoValido();
        when(em.find(Empleado.class, empleado.getIdEmpleado())).thenReturn(empleado);
        configurarConteoRelaciones(empleado.getIdEmpleado(), 2L);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> dao.eliminar(empleado));

        assertTrue(ex.getMessage().contains("roles asociados"));
        verify(em, never()).remove(any());
        verify(em, never()).flush();
    }

    @Test
    void debeEliminarEmpleadoSinRelaciones() {
        Empleado empleado = empleadoValido();
        when(em.find(Empleado.class, empleado.getIdEmpleado())).thenReturn(empleado);
        configurarConteoRelaciones(empleado.getIdEmpleado(), 0L);

        dao.eliminar(empleado);

        verify(em).remove(same(empleado));
        verify(em).flush();
    }

    @Test
    void fallaLaBaseAlCrearDebePropagarExcepcion() {
        Empleado empleado = empleadoValido();
        doThrow(new IllegalStateException("BD desconectada"))
                .when(em).persist(same(empleado));

        assertThrows(IllegalStateException.class, () -> dao.crear(empleado));
        verify(em).persist(same(empleado));
    }

    @Test
    void fallaLaBaseAlEliminarDebePropagarExcepcion() {
        Empleado empleado = empleadoValido();
        when(em.find(Empleado.class, empleado.getIdEmpleado())).thenReturn(empleado);
        configurarConteoRelaciones(empleado.getIdEmpleado(), 0L);
        doThrow(new IllegalStateException("Violación de integridad"))
                .when(em).flush();

        assertThrows(IllegalStateException.class, () -> dao.eliminar(empleado));
        verify(em).remove(same(empleado));
    }

    private Empleado empleadoValido() {
        Empleado empleado = new Empleado();
        empleado.setIdEmpleado(UUID.randomUUID());
        empleado.setNombre("Carlos");
        empleado.setApellido("Prueba");
        empleado.setActivo(true);
        return empleado;
    }

    private void configurarConteoRelaciones(UUID id, long cantidad) {
        when(em.createNamedQuery("EmpleadoRol.countByIdEmpleado", Long.class))
                .thenReturn(consultaConteo);
        when(consultaConteo.setParameter("idEmpleado", id))
                .thenReturn(consultaConteo);
        when(consultaConteo.getSingleResult()).thenReturn(cantidad);
    }
}
