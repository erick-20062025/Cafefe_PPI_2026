package boundary;

import control.EmpleadoDAO;
import entity.Empleado;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.component.tabview.Tab;
import org.primefaces.event.TabChangeEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pruebas del formulario Empleado y recuperación ante errores. */
@ExtendWith(MockitoExtension.class)
class EmpleadoModelTest {

    @Mock private EmpleadoDAO empleadoDAO;
    @Mock private EmpleadoRolModel rolesModel;
    private ModeloPrueba model;

    private static class ModeloPrueba extends EmpleadoModel {
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
        inyectar("empleadoDAO", empleadoDAO);
        inyectar("empleadoRolModel", rolesModel);
    }

    @Test
    void inicializarDebeCargarEmpleados() {
        List<Empleado> empleados = List.of(empleadoValido());
        when(empleadoDAO.findAll()).thenReturn(empleados);

        model.init();

        assertSame(empleados, model.getModelo());
        verify(empleadoDAO).findAll();
    }

    @Test
    void nuevoDebeCrearEmpleadoActivoYDeshabilitarRoles() {
        model.setIndiceTab(1);
        model.editando = true;
        model.consultando = true;

        model.btnNuevoHandler(null);

        assertNotNull(model.getRegistro());
        assertNotNull(model.getRegistro().getIdEmpleado());
        assertTrue(model.getRegistro().getActivo());
        assertTrue(model.isNuevo());
        assertFalse(model.isEditando());
        assertFalse(model.isConsultando());
        assertEquals(0, model.getIndiceTab());
        assertFalse(model.isPuedeGestionarRelaciones());
        verify(rolesModel).cargarEmpleado(null);
        verifyNoInteractions(empleadoDAO);
    }

    @Test
    void seleccionarEmpleadoDebeCargarRoles() {
        Empleado empleado = empleadoValido();
        model.setRegistro(empleado);
        model.setIndiceTab(1);
        model.nuevo = true;
        model.editando = true;
        model.consultando = true;

        model.onRowSelect();

        assertSame(empleado, model.getRegistro());
        assertTrue(model.isPuedeGestionarRelaciones());
        assertFalse(model.isNuevo());
        assertFalse(model.isEditando());
        assertFalse(model.isConsultando());
        assertEquals(0, model.getIndiceTab());
        verify(rolesModel).cargarEmpleado(same(empleado));
    }

    @Test
    void noDebeGestionarRolesSinEmpleadoGuardado() {
        assertFalse(model.isPuedeGestionarRelaciones());
        model.setRegistro(new Empleado());
        assertFalse(model.isPuedeGestionarRelaciones());
        model.setRegistro(empleadoValido());
        model.nuevo = true;
        assertFalse(model.isPuedeGestionarRelaciones());
    }

    @Test
    void deseleccionarDebeLimpiarEstadoYRoles() {
        prepararEstadoSeleccionado();

        model.onRowUnselect();

        verificarEstadoLimpio();
        verify(rolesModel).cargarEmpleado(null);
    }

    @Test
    void cancelarEdicionDebeLimpiarYRecargarDatos() {
        prepararEstadoSeleccionado();

        model.btnCancelarHandler(null);

        verificarEstadoLimpio();
        verify(rolesModel).cargarEmpleado(null);
        verify(empleadoDAO).findAll();
    }

    @Test
    void guardarNuevoDebePersistirYLimpiarRelaciones() {
        model.btnNuevoHandler(null);
        Empleado empleado = model.getRegistro();
        empleado.setNombre(" Ana ");
        empleado.setApellido(" López ");
        clearInvocations(rolesModel);

        model.btnGuardarHandler(null);

        verify(empleadoDAO).crear(same(empleado));
        verify(empleadoDAO).findAll();
        verify(rolesModel).cargarEmpleado(null);
        assertEquals("Ana", empleado.getNombre());
        assertEquals("López", empleado.getApellido());
        verificarEstadoLimpio();
        assertEquals("Empleado guardado correctamente", model.exito);
    }

    @Test
    void modificarEmpleadoDebeGuardarYLimpiarEstado() {
        Empleado empleado = empleadoValido();
        model.setRegistro(empleado);
        model.btnModificarHandler(null);
        empleado.setApellido("Actualizado");

        model.btnGuardarHandler(null);

        verify(empleadoDAO).modificar(same(empleado));
        verify(empleadoDAO, never()).crear(any());
        verify(rolesModel).cargarEmpleado(null);
        verificarEstadoLimpio();
        assertEquals("Empleado guardado correctamente", model.exito);
    }

    @Test
    void errorInesperadoAlGuardarDebeConservarFormulario() {
        Empleado empleado = empleadoValido();
        model.setRegistro(empleado);
        model.btnModificarHandler(null);
        model.setIndiceTab(1);
        IllegalStateException fallo = new IllegalStateException("Fallo simulado");
        doThrow(fallo).when(empleadoDAO).modificar(same(empleado));

        // Capturamos el log real para verificar que se registre la excepción.
        Logger logger = Logger.getLogger(EmpleadoModel.class.getName());
        List<LogRecord> registros = new ArrayList<>();
        Handler handler = capturador(registros);
        logger.addHandler(handler);
        try {
            model.btnGuardarHandler(null);
        } finally {
            logger.removeHandler(handler);
        }

        assertSame(empleado, model.getRegistro());
        assertTrue(model.isEditando());
        assertEquals(1, model.getIndiceTab());
        assertEquals("No se pudo guardar el empleado", model.error);
        assertTrue(registros.stream().anyMatch(r -> r.getLevel() == Level.SEVERE
                && r.getThrown() == fallo && r.getMessage().contains("guardar el empleado")));
        verifyNoInteractions(rolesModel);
        verify(empleadoDAO, never()).findAll();
    }

    @Test
    void validacionDeNegocioDelDAODebeMostrarAdvertencia() {
        Empleado empleado = empleadoValido();
        model.setRegistro(empleado);
        model.btnModificarHandler(null);
        doThrow(new IllegalArgumentException("Empleado inexistente"))
                .when(empleadoDAO).modificar(same(empleado));

        model.btnGuardarHandler(null);

        assertEquals("Empleado inexistente", model.advertencia);
        assertSame(empleado, model.getRegistro());
        assertTrue(model.isEditando());
        verify(empleadoDAO, never()).findAll();
    }

    @Test
    void guardarSinSeleccionNiModoNoDebePersistir() {
        model.setIndiceTab(1);

        model.btnGuardarHandler(null);

        assertNull(model.getRegistro());
        assertEquals(1, model.getIndiceTab());
        assertEquals("Primero debe crear o editar un empleado", model.advertencia);
        verifyNoInteractions(empleadoDAO, rolesModel);
    }

    @Test
    void nombreBlancoDebeRechazarse() {
        model.btnNuevoHandler(null);
        Empleado empleado = model.getRegistro();
        empleado.setNombre("   ");
        empleado.setApellido("Prueba");

        model.btnGuardarHandler(null);

        assertEquals("El nombre es obligatorio", model.advertencia);
        assertSame(empleado, model.getRegistro());
        assertTrue(model.isNuevo());
        verifyNoInteractions(empleadoDAO);
    }

    @Test
    void apellidoMuyCortoDebeRechazarse() {
        model.btnNuevoHandler(null);
        model.getRegistro().setNombre("Carlos");
        model.getRegistro().setApellido("X");

        model.btnGuardarHandler(null);

        assertEquals("El apellido debe tener entre 2 y 255 caracteres",
                model.advertencia);
        verifyNoInteractions(empleadoDAO);
    }

    @Test
    void noDebeEliminarSinSeleccion() {
        model.btnEliminarHandler(null);

        assertEquals("Debe seleccionar un empleado", model.advertencia);
        verifyNoInteractions(empleadoDAO, rolesModel);
    }

    @Test
    void eliminarEmpleadoValidoDebeLimpiarEstado() {
        Empleado empleado = empleadoValido();
        model.setRegistro(empleado);

        model.btnEliminarHandler(null);

        verify(empleadoDAO).eliminar(same(empleado));
        verify(empleadoDAO).findAll();
        verify(rolesModel).cargarEmpleado(null);
        verificarEstadoLimpio();
        assertEquals("Empleado eliminado correctamente", model.exito);
    }

    @Test
    void rechazoDeEliminacionPorRelacionesDebeConservarSeleccion() {
        Empleado empleado = empleadoValido();
        model.setRegistro(empleado);
        doThrow(new IllegalArgumentException("El empleado tiene roles asociados"))
                .when(empleadoDAO).eliminar(same(empleado));

        model.btnEliminarHandler(null);

        assertEquals("El empleado tiene roles asociados", model.advertencia);
        assertSame(empleado, model.getRegistro());
        verify(empleadoDAO, never()).findAll();
        verifyNoInteractions(rolesModel);
    }

    @Test
    void errorInesperadoAlEliminarDebeConservarSeleccion() {
        Empleado empleado = empleadoValido();
        model.setRegistro(empleado);
        doThrow(new IllegalStateException("BD fuera de servicio"))
                .when(empleadoDAO).eliminar(same(empleado));

        model.btnEliminarHandler(null);

        assertSame(empleado, model.getRegistro());
        assertTrue(model.error.startsWith("No se pudo eliminar el empleado"));
        verify(empleadoDAO, never()).findAll();
        verifyNoInteractions(rolesModel);
    }

    @Test
    void debeCambiarIndiceSegunPestana() {
        TabChangeEvent evento = mock(TabChangeEvent.class);
        Tab tab = mock(Tab.class);
        when(evento.getTab()).thenReturn(tab);
        when(tab.getTitle()).thenReturn("Asignar roles", "Datos del empleado");

        model.onTabChange(evento);
        assertEquals(1, model.getIndiceTab());
        model.onTabChange(evento);
        assertEquals(0, model.getIndiceTab());
    }

    private Handler capturador(List<LogRecord> registros) {
        return new Handler() {
            @Override public void publish(LogRecord registro) { registros.add(registro); }
            @Override public void flush() { }
            @Override public void close() { }
        };
    }

    private Empleado empleadoValido() {
        Empleado empleado = new Empleado();
        empleado.setIdEmpleado(UUID.randomUUID());
        empleado.setNombre("Carlos");
        empleado.setApellido("Prueba");
        empleado.setActivo(true);
        return empleado;
    }

    private void prepararEstadoSeleccionado() {
        model.setRegistro(empleadoValido());
        model.setIndiceTab(1);
        model.nuevo = true;
        model.editando = true;
        model.consultando = true;
    }

    private void verificarEstadoLimpio() {
        assertNull(model.getRegistro());
        assertFalse(model.isNuevo());
        assertFalse(model.isEditando());
        assertFalse(model.isConsultando());
        assertEquals(0, model.getIndiceTab());
    }

    private void inyectar(String nombre, Object valor) throws Exception {
        Field campo = EmpleadoModel.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(model, valor);
    }
}
