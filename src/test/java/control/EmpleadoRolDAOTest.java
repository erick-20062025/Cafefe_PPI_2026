package control;

import entity.Empleado;
import entity.EmpleadoRol;
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

/**
 * Pruebas del DAO de asignaciones y su integridad referencial.
 */
@ExtendWith(MockitoExtension.class)
public class EmpleadoRolDAOTest {

    @Mock
    private EntityManager em;
    @Mock
    private TypedQuery<EmpleadoRol> consultaRelaciones;
    @Mock
    private TypedQuery<Long> consultaConteo;
    @InjectMocks
    private EmpleadoRolDAO dao;

    @Test
    void listarDebeUsarNamedQuery() {
        List<EmpleadoRol> registros = List.of(relacionValida());
        when(em.createNamedQuery("EmpleadoRol.findAll", EmpleadoRol.class))
                .thenReturn(consultaRelaciones);
        when(consultaRelaciones.getResultList()).thenReturn(registros);

        assertSame(registros, dao.findAll());
        verify(em).createNamedQuery("EmpleadoRol.findAll", EmpleadoRol.class);
    }

    @Test
    void debeBuscarAsignacionPorId() {
        UUID id = UUID.randomUUID();
        EmpleadoRol relacion = relacionValida();
        when(em.find(EmpleadoRol.class, id)).thenReturn(relacion);

        assertSame(relacion, dao.findById(id));
        verify(em).find(EmpleadoRol.class, id);
    }

    @Test
    void identificadorDeAsignacionInvalidoDebeRechazarse() {
        assertThrows(IllegalArgumentException.class, () -> dao.findById(null));
        assertThrows(IllegalArgumentException.class, () -> dao.findById("texto"));
        verifyNoInteractions(em);
    }

    @Test
    void debeBuscarRelacionesYDevolverListaVacia() {
        UUID id = UUID.randomUUID();
        List<EmpleadoRol> registros = List.of(relacionValida(), relacionValida());
        when(em.createNamedQuery("EmpleadoRol.findByIdEmpleado", EmpleadoRol.class))
                .thenReturn(consultaRelaciones);
        when(consultaRelaciones.setParameter("idEmpleado", id))
                .thenReturn(consultaRelaciones);
        when(consultaRelaciones.getResultList()).thenReturn(registros, List.of());

        assertSame(registros, dao.findByIdEmpleado(id));
        assertTrue(dao.findByIdEmpleado(id).isEmpty());
        verify(consultaRelaciones, times(2)).setParameter("idEmpleado", id);
        verify(consultaRelaciones, times(2)).getResultList();
    }

    @Test
    void debeContarCeroYVariasRelaciones() {
        UUID id = UUID.randomUUID();
        configurarConteo("EmpleadoRol.countByIdEmpleado", "idEmpleado", id, 0L, 4L);

        assertEquals(0L, dao.countByIdEmpleado(id));
        assertEquals(4L, dao.countByIdEmpleado(id));
        verify(consultaConteo, times(2)).setParameter("idEmpleado", id);
    }

    @Test
    void debeDistinguirRelacionExistenteDeAusente() {
        UUID idEmpleado = UUID.randomUUID();
        UUID idRol = UUID.randomUUID();
        when(em.createNamedQuery("EmpleadoRol.countByEmpleadoAndRol", Long.class))
                .thenReturn(consultaConteo);
        when(consultaConteo.setParameter("idEmpleado", idEmpleado))
                .thenReturn(consultaConteo);
        when(consultaConteo.setParameter("idRol", idRol))
                .thenReturn(consultaConteo);
        when(consultaConteo.getSingleResult()).thenReturn(0L, 1L, 3L);

        assertFalse(dao.existeRelacion(idEmpleado, idRol));
        assertTrue(dao.existeRelacion(idEmpleado, idRol));
        assertTrue(dao.existeRelacion(idEmpleado, idRol));
        verify(consultaConteo, times(3)).getSingleResult();
    }

    @Test
    void identificadoresNulosDebenRechazarseSinConsultar() {
        assertThrows(IllegalArgumentException.class, () -> dao.findByIdEmpleado(null));
        assertThrows(IllegalArgumentException.class, () -> dao.countByIdEmpleado(null));
        assertThrows(IllegalArgumentException.class,
                () -> dao.existeRelacion(null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> dao.existeRelacion(UUID.randomUUID(), null));
        verifyNoInteractions(em);
    }

    @Test
    void debeCrearAsignacionValida() {
        EmpleadoRol relacion = relacionValida();
        configurarReferencias(relacion);
        configurarConteoDuplicados(relacion, 0L);

        dao.crear(relacion);

        verify(em).persist(same(relacion));
        verify(em).flush();
    }

    @Test
    void relacionNulaDebeRechazarse() {
        assertThrows(IllegalArgumentException.class, () -> dao.crear(null));
        verifyNoInteractions(em);
    }

    @Test
    void relacionSinIdDebeRechazarse() {
        EmpleadoRol relacion = relacionValida();
        relacion.setIdEmpleadoRol(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verifyNoInteractions(em);
    }

    @Test
    void relacionSinEmpleadoORolDebeRechazarse() {
        EmpleadoRol sinEmpleado = relacionValida();
        sinEmpleado.setIdEmpleado(null);
        EmpleadoRol sinRol = relacionValida();
        sinRol.setIdRol(null);

        assertThrows(IllegalArgumentException.class, () -> dao.crear(sinEmpleado));
        assertThrows(IllegalArgumentException.class, () -> dao.crear(sinRol));
        verifyNoInteractions(em);
    }

    @Test
    void relacionConEstadoNuloDebeRechazarse() {
        EmpleadoRol relacion = relacionValida();
        relacion.setActivo(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verifyNoInteractions(em);
    }

    /**
     * Verifica que no se pueda asignar un rol a un empleado que no existe en la
     * base de datos.
     *
     * También comprueba que no se guarden datos ni se ejecuten operaciones de
     * persistencia.
     */
    @Test
    void noDebeCrearConEmpleadoInexistente() {

        // Empleado con UUID, pero inexistente en la BD.
        Empleado empleado = new Empleado();
        empleado.setIdEmpleado(UUID.randomUUID());
        empleado.setActivo(true);

        // Rol de prueba.
        Rol rol = new Rol();
        rol.setIdRol(UUID.randomUUID());
        rol.setActivo(true);

        // Relación que se intenta registrar.
        EmpleadoRol relacion = new EmpleadoRol();
        relacion.setIdEmpleadoRol(UUID.randomUUID());
        relacion.setIdEmpleado(empleado);
        relacion.setIdRol(rol);
        relacion.setActivo(true);

        // No configuramos respuestas para em.find().
        // Mockito devuelve null por defecto,
        // simulando que el empleado no existe.
        IllegalArgumentException excepcion
                = assertThrows(
                        IllegalArgumentException.class,
                        () -> dao.crear(relacion)
                );

        // Debe mostrar un error comprensible.
        assertEquals(
                "El empleado o el rol no existe",
                excepcion.getMessage()
        );

        // Debe haber buscado al empleado.
        verify(em).find(
                Empleado.class,
                empleado.getIdEmpleado()
        );

        // No debe registrar la relación.
        verify(em, never()).persist(any());

        // Tampoco debe confirmar escrituras.
        verify(em, never()).flush();
    }

    @Test
    void noDebeCrearConRolInexistente() {
        EmpleadoRol relacion = relacionValida();
        Empleado empleado = relacion.getIdEmpleado();
        when(em.find(Empleado.class, empleado.getIdEmpleado())).thenReturn(empleado);

        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verify(em, never()).persist(any());
    }

    @Test
    void empleadoInactivoNoDebeRecibirRoles() {
        EmpleadoRol relacion = relacionValida();
        relacion.getIdEmpleado().setActivo(false);
        configurarReferencias(relacion);

        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verify(em, never()).persist(any());
    }

    @Test
    void rolInactivoNoDebeAsignarse() {
        EmpleadoRol relacion = relacionValida();
        relacion.getIdRol().setActivo(false);
        configurarReferencias(relacion);

        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verify(em, never()).persist(any());
    }

    @Test
    void identificadorDeAsignacionDuplicadoDebeRechazarse() {
        EmpleadoRol relacion = relacionValida();
        configurarReferencias(relacion);
        when(em.find(EmpleadoRol.class, relacion.getIdEmpleadoRol()))
                .thenReturn(relacion);

        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verify(em, never()).persist(any());
    }

    @Test
    void mismaParejaEmpleadoRolDebeRechazarse() {
        EmpleadoRol relacion = relacionValida();
        configurarReferencias(relacion);
        configurarConteoDuplicados(relacion, 1L);

        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> dao.crear(relacion));

        assertEquals("El empleado ya tiene ese rol asignado", e.getMessage());
        verify(em, never()).persist(any());
    }

    @Test
    void falloAlPersistirDebePropagarse() {
        EmpleadoRol relacion = relacionValida();
        configurarReferencias(relacion);
        configurarConteoDuplicados(relacion, 0L);
        doThrow(new IllegalStateException("Fallo simulado"))
                .when(em).persist(same(relacion));

        assertThrows(IllegalStateException.class, () -> dao.crear(relacion));
        verify(em, never()).flush();
    }

    @Test
    void debeModificarEstadoYObservacionesSinCambiarReferencias() {
        EmpleadoRol actual = relacionValida();
        EmpleadoRol cambios = relacionValida();
        cambios.setIdEmpleadoRol(actual.getIdEmpleadoRol());
        cambios.setIdEmpleado(actual.getIdEmpleado());
        cambios.setIdRol(actual.getIdRol());
        cambios.setActivo(false);
        cambios.setObservaciones("Cambio autorizado");
        when(em.find(EmpleadoRol.class, actual.getIdEmpleadoRol())).thenReturn(actual);

        dao.modificar(cambios);

        assertFalse(actual.getActivo());
        assertEquals("Cambio autorizado", actual.getObservaciones());
        verify(em, never()).merge(any());
    }

    @Test
    void noDebeCambiarElEmpleadoOElRolDeRelacionExistente() {
        EmpleadoRol actual = relacionValida();
        EmpleadoRol cambios = relacionValida();
        cambios.setIdEmpleadoRol(actual.getIdEmpleadoRol());
        cambios.setIdRol(actual.getIdRol());
        when(em.find(EmpleadoRol.class, actual.getIdEmpleadoRol())).thenReturn(actual);

        assertThrows(IllegalArgumentException.class, () -> dao.modificar(cambios));
        verify(em, never()).merge(any());
    }

    @Test
    void noDebeModificarRelacionInexistente() {
        EmpleadoRol relacion = relacionValida();
        assertThrows(IllegalArgumentException.class, () -> dao.modificar(relacion));
        verify(em, never()).merge(any());
    }

    @Test
    void noDebeReactivarRelacionConEmpleadoInactivo() {
        EmpleadoRol actual = relacionValida();
        actual.getIdEmpleado().setActivo(false);
        actual.setActivo(false);
        EmpleadoRol cambios = relacionValida();
        cambios.setIdEmpleadoRol(actual.getIdEmpleadoRol());
        cambios.setIdEmpleado(actual.getIdEmpleado());
        cambios.setIdRol(actual.getIdRol());
        cambios.setActivo(true);
        when(em.find(EmpleadoRol.class, actual.getIdEmpleadoRol())).thenReturn(actual);

        assertThrows(IllegalArgumentException.class, () -> dao.modificar(cambios));
        assertFalse(actual.getActivo());
    }

    @Test
    void debeEliminarRelacionSinOrdenesNiFacturas() {
        EmpleadoRol relacion = relacionValida();
        when(em.find(EmpleadoRol.class, relacion.getIdEmpleadoRol())).thenReturn(relacion);
        configurarHistorial(relacion.getIdEmpleadoRol(), 0L, 0L);

        dao.eliminar(relacion);

        verify(em).remove(same(relacion));
        verify(em).flush();
    }

    @Test
    void noDebeEliminarRelacionConOrdenes() {
        EmpleadoRol relacion = relacionValida();
        when(em.find(EmpleadoRol.class, relacion.getIdEmpleadoRol())).thenReturn(relacion);
        configurarHistorial(relacion.getIdEmpleadoRol(), 2L, 0L);

        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(relacion));
        verify(em, never()).remove(any());
    }

    @Test
    void noDebeEliminarRelacionConFacturas() {
        EmpleadoRol relacion = relacionValida();
        when(em.find(EmpleadoRol.class, relacion.getIdEmpleadoRol())).thenReturn(relacion);
        configurarHistorial(relacion.getIdEmpleadoRol(), 0L, 1L);

        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(relacion));
        verify(em, never()).remove(any());
    }

    @Test
    void noDebeEliminarRelacionInexistente() {
        EmpleadoRol relacion = relacionValida();
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(relacion));
        verify(em, never()).remove(any());
    }

    @Test
    void noDebeEliminarRelacionSinId() {
        EmpleadoRol relacion = new EmpleadoRol();
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(relacion));
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(null));
        verifyNoInteractions(em);
    }

    @Test
    void errorDeIntegridadAlEliminarDebePropagarse() {
        EmpleadoRol relacion = relacionValida();
        when(em.find(EmpleadoRol.class, relacion.getIdEmpleadoRol())).thenReturn(relacion);
        configurarHistorial(relacion.getIdEmpleadoRol(), 0L, 0L);
        doThrow(new IllegalStateException("Fallo de integridad"))
                .when(em).flush();

        assertThrows(IllegalStateException.class, () -> dao.eliminar(relacion));
        verify(em).remove(same(relacion));
    }

    private EmpleadoRol relacionValida() {
        Empleado empleado = new Empleado();
        empleado.setIdEmpleado(UUID.randomUUID());
        empleado.setActivo(true);
        Rol rol = new Rol();
        rol.setIdRol(UUID.randomUUID());
        rol.setActivo(true);
        EmpleadoRol relacion = new EmpleadoRol();
        relacion.setIdEmpleadoRol(UUID.randomUUID());
        relacion.setIdEmpleado(empleado);
        relacion.setIdRol(rol);
        relacion.setActivo(true);
        return relacion;
    }

    private void configurarReferencias(EmpleadoRol relacion) {
        Empleado empleado = relacion.getIdEmpleado();
        Rol rol = relacion.getIdRol();
        when(em.find(Empleado.class, empleado.getIdEmpleado())).thenReturn(empleado);
        when(em.find(Rol.class, rol.getIdRol())).thenReturn(rol);
    }

    private void configurarConteo(String consulta, String parametro,
            UUID id, long... resultados) {
        when(em.createNamedQuery(consulta, Long.class)).thenReturn(consultaConteo);
        when(consultaConteo.setParameter(parametro, id)).thenReturn(consultaConteo);
        Long[] valores = new Long[resultados.length];
        for (int i = 0; i < resultados.length; i++) {
            valores[i] = resultados[i];
        }
        when(consultaConteo.getSingleResult()).thenReturn(valores[0],
                java.util.Arrays.copyOfRange(valores, 1, valores.length));
    }

    private void configurarConteoDuplicados(EmpleadoRol relacion, long cantidad) {
        when(em.createNamedQuery("EmpleadoRol.countByEmpleadoAndRol", Long.class))
                .thenReturn(consultaConteo);
        when(consultaConteo.setParameter("idEmpleado",
                relacion.getIdEmpleado().getIdEmpleado())).thenReturn(consultaConteo);
        when(consultaConteo.setParameter("idRol",
                relacion.getIdRol().getIdRol())).thenReturn(consultaConteo);
        when(consultaConteo.getSingleResult()).thenReturn(cantidad);
    }

    private void configurarHistorial(UUID id, long ordenes, long facturas) {
        @SuppressWarnings("unchecked")
        TypedQuery<Long> queryOrdenes = mock(TypedQuery.class);
        @SuppressWarnings("unchecked")
        TypedQuery<Long> queryFacturas = mock(TypedQuery.class);

        when(em.createNamedQuery("EmpleadoRol.countOrdenes", Long.class))
                .thenReturn(queryOrdenes);
        when(em.createNamedQuery("EmpleadoRol.countFacturas", Long.class))
                .thenReturn(queryFacturas);
        when(queryOrdenes.setParameter("idEmpleadoRol", id))
                .thenReturn(queryOrdenes);
        when(queryFacturas.setParameter("idEmpleadoRol", id))
                .thenReturn(queryFacturas);
        when(queryOrdenes.getSingleResult()).thenReturn(ordenes);
        when(queryFacturas.getSingleResult()).thenReturn(facturas);
    }
}
