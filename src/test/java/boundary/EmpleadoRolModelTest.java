package boundary;

import control.EmpleadoRolDAO;
import control.RolDAO;
import entity.Empleado;
import entity.EmpleadoRol;
import entity.Rol;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpleadoRolModelTest {

    @Mock private EmpleadoRolDAO empleadoRolDAO;
    @Mock private RolDAO rolDAO;
    private EmpleadoRolModelPrueba model;

    private static class EmpleadoRolModelPrueba extends EmpleadoRolModel {
        private String exito;
        private String advertencia;
        private String error;

        @Override protected void mostrarExito(String detalle) { exito = detalle; }
        @Override protected void mostrarAdvertencia(String detalle) { advertencia = detalle; }
        @Override protected void mostrarError(String detalle) { error = detalle; }
    }

    @BeforeEach
    void setUp() throws Exception {
        model = new EmpleadoRolModelPrueba();
        inyectar("empleadoRolDAO", empleadoRolDAO);
        inyectar("rolDAO", rolDAO);
    }

    @Test
    void debeCargarYActualizarLasOpcionesDeRoles() {
        List<Rol> iniciales = List.of(rol(true), rol(false));
        List<Rol> actualizados = List.of(rol(true));
        when(rolDAO.findAll()).thenReturn(iniciales, actualizados);

        model.init();
        assertSame(iniciales, model.getRolList());
        model.cargarRoles();
        assertSame(actualizados, model.getRolList());
        verify(rolDAO, times(2)).findAll();
    }

    @Test
    void cargarEmpleadoDebeConsultarAsignacionesYLimpiarSeleccion() {
        Empleado empleado = empleado();
        EmpleadoRol relacion = new EmpleadoRol();
        List<EmpleadoRol> relaciones = List.of(relacion);
        when(empleadoRolDAO.findByIdEmpleado(empleado.getIdEmpleado()))
                .thenReturn(relaciones);
        model.setIdRolSeleccionado(UUID.randomUUID().toString());

        model.cargarEmpleado(empleado);

        assertSame(empleado, model.getEmpleado());
        assertSame(relaciones, model.getModelo());
        assertNull(model.getIdRolSeleccionado());
        verify(empleadoRolDAO).findByIdEmpleado(empleado.getIdEmpleado());
    }

    @Test
    void cargarEmpleadoSinIdONullDebeVaciarAsignaciones() {
        model.setModelo(List.of(new EmpleadoRol()));
        model.setIdRolSeleccionado("seleccion previa");

        model.cargarEmpleado(new Empleado());
        assertTrue(model.getModelo().isEmpty());
        assertNull(model.getIdRolSeleccionado());
        model.cargarEmpleado(null);
        assertNull(model.getEmpleado());
        assertTrue(model.getModelo().isEmpty());
        verifyNoInteractions(empleadoRolDAO);
    }

    @Test
    void noDebeAsignarSinSeleccionDeRol() {
        model.agregarRol();
        model.setIdRolSeleccionado("   ");
        model.agregarRol();

        assertEquals("Debe seleccionar un rol", model.advertencia);
        verifyNoInteractions(rolDAO, empleadoRolDAO);
    }

    @Test
    void noDebeAsignarRolConIdentificadorInvalidoOInexistente() {
        model.setIdRolSeleccionado("no-es-uuid");
        model.agregarRol();
        assertEquals("El rol seleccionado no existe", model.error);
        verifyNoInteractions(rolDAO);

        UUID id = UUID.randomUUID();
        model.setIdRolSeleccionado(id.toString());
        model.agregarRol();

        assertEquals("El rol seleccionado no existe", model.error);
        verify(rolDAO).findById(id);
        verifyNoInteractions(empleadoRolDAO);
    }

    @Test
    void noDebeAsignarRolesInactivosONull() {
        Rol inactivo = rol(false);
        model.setIdRolSeleccionado(inactivo.getIdRol().toString());
        when(rolDAO.findById(inactivo.getIdRol())).thenReturn(inactivo);

        model.agregarRol();
        assertEquals("No se puede asignar un rol inactivo", model.advertencia);
        inactivo.setActivo(null);
        model.agregarRol();

        assertEquals("No se puede asignar un rol inactivo", model.advertencia);
        verifyNoInteractions(empleadoRolDAO);
    }

    @Test
    void noDebeAsignarRolActivoSinEmpleadoValido() {
        Rol rol = seleccionarRolActivo();

        model.agregarRol();
        assertEquals("Debe seleccionar un registro principal", model.advertencia);
        model.setEmpleado(new Empleado());
        model.agregarRol();

        assertEquals("Debe seleccionar un registro principal", model.advertencia);
        verify(rolDAO, times(2)).findById(rol.getIdRol());
        verifyNoInteractions(empleadoRolDAO);
    }

    @Test
    void noDebeCrearAsignacionDuplicada() {
        Empleado empleado = empleado();
        model.setEmpleado(empleado);
        Rol rol = seleccionarRolActivo();
        when(empleadoRolDAO.existeRelacion(empleado.getIdEmpleado(), rol.getIdRol()))
                .thenReturn(true);

        model.agregarRol();

        assertEquals("La relación ya existe", model.advertencia);
        assertEquals(rol.getIdRol().toString(), model.getIdRolSeleccionado());
        verify(empleadoRolDAO, never()).crear(any());
        verify(empleadoRolDAO, never()).findByIdEmpleado(any());
    }

    @Test
    void debeAsignarRolActivoConUuidYRecargarRelaciones() {
        Empleado empleado = empleado();
        model.setEmpleado(empleado);
        Rol rol = seleccionarRolActivo();
        List<EmpleadoRol> recargadas = List.of(new EmpleadoRol());
        when(empleadoRolDAO.findByIdEmpleado(empleado.getIdEmpleado()))
                .thenReturn(recargadas);

        model.agregarRol();

        ArgumentCaptor<EmpleadoRol> captor = ArgumentCaptor.forClass(EmpleadoRol.class);
        verify(empleadoRolDAO).crear(captor.capture());
        EmpleadoRol creada = captor.getValue();
        assertNotNull(creada.getIdEmpleadoRol());
        assertSame(empleado, creada.getIdEmpleado());
        assertSame(rol, creada.getIdRol());
        assertTrue(creada.getActivo());
        assertSame(recargadas, model.getModelo());
        assertNull(model.getIdRolSeleccionado());
        assertEquals("Registro asociado correctamente", model.exito);
        verify(empleadoRolDAO).existeRelacion(empleado.getIdEmpleado(), rol.getIdRol());
    }

    @Test
    void debeQuitarSoloLaAsignacionYRecargarRolesDelEmpleado() {
        Empleado empleado = empleado();
        model.setEmpleado(empleado);
        EmpleadoRol relacion = new EmpleadoRol();
        when(empleadoRolDAO.findByIdEmpleado(empleado.getIdEmpleado()))
                .thenReturn(List.of());
        model.setIdRolSeleccionado(UUID.randomUUID().toString());

        model.quitarRol(relacion);

        verify(empleadoRolDAO).eliminar(same(relacion));
        verify(empleadoRolDAO).findByIdEmpleado(empleado.getIdEmpleado());
        verifyNoInteractions(rolDAO);
        assertSame(empleado, model.getEmpleado());
        assertTrue(model.getModelo().isEmpty());
        assertNull(model.getIdRolSeleccionado());
        assertEquals("Relación eliminada correctamente", model.exito);
    }

    @Test
    void buscarOpcionVaciaNoDebeConsultarDAO() {
        assertNull(model.buscarOpcion(null));
        assertNull(model.buscarOpcion("  "));
        verifyNoInteractions(rolDAO);
    }

    @Test
    void validacionProtegidaDebeRechazarRolAusenteOInactivo() {
        assertFalse(model.opcionValida(null));
        assertEquals("Debe seleccionar un rol", model.advertencia);
        assertFalse(model.opcionValida(rol(false)));
        assertEquals("No se puede asignar un rol inactivo", model.advertencia);
        assertTrue(model.opcionValida(rol(true)));
    }

    private Empleado empleado() {
        Empleado empleado = new Empleado();
        empleado.setIdEmpleado(UUID.randomUUID());
        return empleado;
    }

    private Rol rol(Boolean activo) {
        Rol rol = new Rol();
        rol.setIdRol(UUID.randomUUID());
        rol.setActivo(activo);
        return rol;
    }

    private Rol seleccionarRolActivo() {
        Rol rol = rol(true);
        when(rolDAO.findById(rol.getIdRol())).thenReturn(rol);
        model.setIdRolSeleccionado(rol.getIdRol().toString());
        return rol;
    }

    private void inyectar(String nombre, Object valor) throws Exception {
        Field campo = EmpleadoRolModel.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(model, valor);
    }
}
