package boundary;

import control.EmpleadoDAO;
import entity.Empleado;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.component.tabview.Tab;
import org.primefaces.event.TabChangeEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpleadoModelTest {

    @Mock private EmpleadoDAO empleadoDAO;
    @Mock private EmpleadoRolModel rolesModel;
    private EmpleadoModelPrueba model;

    private static class EmpleadoModelPrueba extends EmpleadoModel {
        private String error;

        @Override protected void mostrarExito(String detalle) { }
        @Override protected void mostrarAdvertencia(String detalle) { }
        @Override protected void mostrarError(String detalle) { error = detalle; }
    }

    @BeforeEach
    void setUp() throws Exception {
        model = new EmpleadoModelPrueba();
        inyectar("empleadoDAO", empleadoDAO);
        inyectar("empleadoRolModel", rolesModel);
    }

    @Test
    void debeInicializarListaDeEmpleados() {
        List<Empleado> empleados = List.of(empleadoGuardado());
        when(empleadoDAO.findAll()).thenReturn(empleados);

        model.init();

        assertSame(empleados, model.getModelo());
        verify(empleadoDAO).findAll();
    }

    @Test
    void nuevoDebeCrearEmpleadoActivoYDeshabilitarAsignaciones() {
        model.setIndiceTab(1);
        model.editando = true;
        model.consultando = true;

        model.btnNuevoHandler(null);

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
    void seleccionarDebeCargarRolesYHabilitarAsignaciones() {
        Empleado empleado = empleadoGuardado();
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
    void noDebeGestionarRolesSinEmpleadoPersistido() {
        assertFalse(model.isPuedeGestionarRelaciones());
        model.setRegistro(new Empleado());
        assertFalse(model.isPuedeGestionarRelaciones());
    }

    @Test
    void deseleccionarDebeLimpiarRolesYEstado() {
        prepararEstadoSeleccionado();

        model.onRowUnselect();

        verificarEstadoLimpio();
        verify(rolesModel).cargarEmpleado(null);
    }

    @Test
    void cancelarDebeLimpiarRolesYEstado() {
        prepararEstadoSeleccionado();

        model.btnCancelarHandler(null);

        verificarEstadoLimpio();
        verify(rolesModel).cargarEmpleado(null);
        verifyNoInteractions(empleadoDAO);
    }

    @Test
    void guardarDebePersistirYLimpiarLasAsignaciones() {
        model.btnNuevoHandler(null);
        Empleado empleado = model.getRegistro();
        clearInvocations(rolesModel);
        model.setIndiceTab(1);

        model.btnGuardarHandler(null);

        verify(empleadoDAO).crear(same(empleado));
        verify(empleadoDAO).findAll();
        verify(rolesModel).cargarEmpleado(null);
        verificarEstadoLimpio();
    }

    @Test
    void falloAlGuardarDebeConservarEmpleadoYRoles() {
        Empleado empleado = empleadoGuardado();
        model.setRegistro(empleado);
        model.btnModificarHandler(null);
        model.setIndiceTab(1);
        doThrow(new IllegalStateException("Fallo simulado"))
                .when(empleadoDAO).modificar(same(empleado));

        model.btnGuardarHandler(null);

        assertSame(empleado, model.getRegistro());
        assertTrue(model.isEditando());
        assertEquals(0, model.getIndiceTab());
        assertEquals("No se pudo guardar el registro", model.error);
        verifyNoInteractions(rolesModel);
        verify(empleadoDAO, never()).findAll();
    }

    @Test
    void guardarSinEmpleadoDebeLimpiarRelacionesSinPersistir() {
        model.setIndiceTab(1);

        model.btnGuardarHandler(null);

        verificarEstadoLimpio();
        verify(rolesModel).cargarEmpleado(null);
        verifyNoInteractions(empleadoDAO);
    }

    @Test
    void debeCambiarIndiceSegunLaPestanaDeRoles() {
        TabChangeEvent evento = mock(TabChangeEvent.class);
        Tab tab = mock(Tab.class);
        when(evento.getTab()).thenReturn(tab);
        when(tab.getTitle()).thenReturn("Asignar roles", "Empleado");

        model.onTabChange(evento);
        assertEquals(1, model.getIndiceTab());
        model.onTabChange(evento);
        assertEquals(0, model.getIndiceTab());
    }

    private Empleado empleadoGuardado() {
        Empleado empleado = new Empleado();
        empleado.setIdEmpleado(UUID.randomUUID());
        return empleado;
    }

    private void prepararEstadoSeleccionado() {
        model.setRegistro(empleadoGuardado());
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
