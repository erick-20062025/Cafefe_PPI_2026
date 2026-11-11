package boundary;

import control.EmpleadoRolDAO;
import control.RolDAO;
import entity.Empleado;
import entity.EmpleadoRol;
import entity.Rol;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pruebas de la interfaz de asignación de roles: éxitos y errores. */
@ExtendWith(MockitoExtension.class)
class EmpleadoRolModelTest {

    @Mock private EmpleadoRolDAO empleadoRolDAO;
    @Mock private RolDAO rolDAO;
    private ModeloPrueba model;

    private static class ModeloPrueba extends EmpleadoRolModel {
        private String exito;
        private String advertencia;
        private String error;

        @Override protected void mostrarExito(String detalle) { exito = detalle; }
        @Override protected void mostrarAdvertencia(String detalle) { advertencia = detalle; }
        @Override protected void mostrarError(String detalle) { error = detalle; }
    }

    @BeforeEach
    void setUp() throws Exception {
        model = new ModeloPrueba();
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
    void cargarEmpleadoDebeConsultarRelacionesYLimpiarSeleccion() {
        Empleado empleado = empleado();
        List<EmpleadoRol> relaciones = List.of(relacion(empleado));
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
    void cargarEmpleadoNuloOSinIdentificadorDebeLimpiarRelaciones() {
        model.setModelo(List.of(new EmpleadoRol()));
        model.setIdRolSeleccionado("seleccion anterior");

        model.cargarEmpleado(new Empleado());
        assertTrue(model.getModelo().isEmpty());
        assertNull(model.getIdRolSeleccionado());

        model.cargarEmpleado(null);
        assertNull(model.getEmpleado());
        assertTrue(model.getModelo().isEmpty());
        verifyNoInteractions(empleadoRolDAO);
    }

    @Test
    void noDebeAgregarSinEmpleadoValido() {
        model.setIdRolSeleccionado(UUID.randomUUID().toString());
        model.agregarRol();
        assertEquals("Debe seleccionar un empleado", model.advertencia);

        model.setEmpleado(new Empleado());
        model.agregarRol();
        assertEquals("Debe seleccionar un empleado", model.advertencia);
        verifyNoInteractions(rolDAO, empleadoRolDAO);
    }

    @Test
    void noDebeAgregarSinSeleccionDeRol() {
        model.setEmpleado(empleado());
        model.agregarRol();
        assertEquals("Debe seleccionar un rol", model.advertencia);

        model.setIdRolSeleccionado("   ");
        model.agregarRol();
        assertEquals("Debe seleccionar un rol", model.advertencia);
        verifyNoInteractions(rolDAO, empleadoRolDAO);
    }

    @Test
    void identificadorDeRolInvalidoDebeMostrarError() {
        model.setEmpleado(empleado());
        model.setIdRolSeleccionado("esto-no-es-uuid");

        model.agregarRol();

        assertEquals("El rol seleccionado no existe", model.error);
        verifyNoInteractions(rolDAO, empleadoRolDAO);
    }

    @Test
    void rolInexistenteDebeMostrarError() {
        model.setEmpleado(empleado());
        UUID id = UUID.randomUUID();
        model.setIdRolSeleccionado(id.toString());

        model.agregarRol();

        assertEquals("El rol seleccionado no existe", model.error);
        verify(rolDAO).findById(id);
        verifyNoInteractions(empleadoRolDAO);
    }

    @Test
    void noDebeAsignarRolInactivoONulo() {
        model.setEmpleado(empleado());
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
    void debeInformarAsignacionDuplicadaSinRecargar() {
        Empleado empleado = empleado();
        model.setEmpleado(empleado);
        Rol rol = seleccionarRolActivo();
        doThrow(new IllegalArgumentException("El empleado ya tiene ese rol asignado"))
                .when(empleadoRolDAO).crear(any(EmpleadoRol.class));

        model.agregarRol();

        assertEquals("El empleado ya tiene ese rol asignado", model.advertencia);
        assertEquals(rol.getIdRol().toString(), model.getIdRolSeleccionado());
        verify(empleadoRolDAO).crear(any(EmpleadoRol.class));
        verify(empleadoRolDAO, never()).findByIdEmpleado(any(UUID.class));
    }

    @Test
    void debeAsignarRolConUuidYRecargarRelaciones() {
        Empleado empleado = empleado();
        model.setEmpleado(empleado);
        Rol rol = seleccionarRolActivo();
        List<EmpleadoRol> recargadas = List.of(relacion(empleado));
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
        assertEquals("Rol asignado correctamente", model.exito);
    }

    @Test
    void falloInesperadoAlAsignarDebeConservarSeleccion() {
        Empleado empleado = empleado();
        model.setEmpleado(empleado);
        Rol rol = seleccionarRolActivo();
        IllegalStateException fallo = new IllegalStateException("Fallo simulado en BD");
        doThrow(fallo).when(empleadoRolDAO).crear(any(EmpleadoRol.class));

        Logger logger = Logger.getLogger(EmpleadoRolModel.class.getName());
        List<LogRecord> registros = new ArrayList<>();
        Handler handler = capturador(registros);
        logger.addHandler(handler);
        try {
            model.agregarRol();
        } finally {
            logger.removeHandler(handler);
        }

        assertEquals("No se pudo asignar el rol", model.error);
        assertSame(empleado, model.getEmpleado());
        assertEquals(rol.getIdRol().toString(), model.getIdRolSeleccionado());
        assertTrue(registros.stream().anyMatch(r -> r.getLevel() == Level.SEVERE
                && r.getThrown() == fallo && r.getMessage().contains("asignar rol")));
        verify(empleadoRolDAO, never()).findByIdEmpleado(any(UUID.class));
    }

    @Test
    void noDebeQuitarRelacionDeOtroEmpleado() {
        model.setEmpleado(empleado());
        model.quitarRol(relacion(empleado()));

        assertEquals("La asignación no pertenece al empleado seleccionado",
                model.advertencia);
        verifyNoInteractions(empleadoRolDAO, rolDAO);
    }

    @Test
    void debeQuitarRelacionPropiaYRecargar() {
        Empleado empleado = empleado();
        model.setEmpleado(empleado);
        EmpleadoRol relacion = relacion(empleado);
        when(empleadoRolDAO.findByIdEmpleado(empleado.getIdEmpleado()))
                .thenReturn(List.of());
        model.setIdRolSeleccionado(UUID.randomUUID().toString());

        model.quitarRol(relacion);

        verify(empleadoRolDAO).eliminar(same(relacion));
        verify(empleadoRolDAO).findByIdEmpleado(empleado.getIdEmpleado());
        assertSame(empleado, model.getEmpleado());
        assertTrue(model.getModelo().isEmpty());
        assertNull(model.getIdRolSeleccionado());
        assertEquals("Rol eliminado del empleado", model.exito);
    }

    @Test
    void noDebeQuitarRelacionConHistorial() {
        Empleado empleado = empleado();
        model.setEmpleado(empleado);
        EmpleadoRol relacion = relacion(empleado);
        String mensaje = "No se puede quitar este rol porque tiene órdenes o facturas asociadas";
        doThrow(new IllegalArgumentException(mensaje))
                .when(empleadoRolDAO).eliminar(same(relacion));

        model.quitarRol(relacion);

        assertEquals(mensaje, model.advertencia);
        assertSame(empleado, model.getEmpleado());
        verify(empleadoRolDAO, never()).findByIdEmpleado(any(UUID.class));
    }

    @Test
    void falloInesperadoAlQuitarDebeConservarEmpleado() {
        Empleado empleado = empleado();
        model.setEmpleado(empleado);
        EmpleadoRol relacion = relacion(empleado);
        doThrow(new IllegalStateException("BD desconectada"))
                .when(empleadoRolDAO).eliminar(same(relacion));

        model.quitarRol(relacion);

        assertEquals("No se pudo quitar el rol", model.error);
        assertSame(empleado, model.getEmpleado());
        verify(empleadoRolDAO, never()).findByIdEmpleado(any(UUID.class));
    }

    @Test
    void buscarOpcionVaciaNoDebeConsultarDAO() {
        assertNull(model.buscarOpcion(null));
        assertNull(model.buscarOpcion("   "));
        verifyNoInteractions(rolDAO);
    }

    @Test
    void opcionValidaDebeRechazarRolAusenteOInactivo() {
        assertFalse(model.opcionValida(null));
        assertEquals("Debe seleccionar un rol", model.advertencia);
        assertFalse(model.opcionValida(rol(false)));
        assertEquals("No se puede asignar un rol inactivo", model.advertencia);
        assertFalse(model.opcionValida(rol(null)));
        assertTrue(model.opcionValida(rol(true)));
    }

    private Handler capturador(List<LogRecord> registros) {
        return new Handler() {
            @Override public void publish(LogRecord registro) { registros.add(registro); }
            @Override public void flush() { }
            @Override public void close() { }
        };
    }

    private Empleado empleado() {
        Empleado empleado = new Empleado();
        empleado.setIdEmpleado(UUID.randomUUID());
        empleado.setActivo(true);
        return empleado;
    }

    private Rol rol(Boolean activo) {
        Rol rol = new Rol();
        rol.setIdRol(UUID.randomUUID());
        rol.setActivo(activo);
        return rol;
    }

    private EmpleadoRol relacion(Empleado empleado) {
        EmpleadoRol relacion = new EmpleadoRol();
        relacion.setIdEmpleadoRol(UUID.randomUUID());
        relacion.setIdEmpleado(empleado);
        relacion.setActivo(true);
        return relacion;
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
