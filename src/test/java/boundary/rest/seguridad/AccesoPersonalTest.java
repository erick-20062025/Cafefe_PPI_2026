package boundary.rest.seguridad;
import control.EmpleadoDAO;
import control.EmpleadoRolDAO;
import entity.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AccesoPersonalTest {
    @Test void exigeEmpleadoActivoYRolesValidadosPorDao() {
        var servicio = new AccesoPersonal();
        servicio.empleados = mock(EmpleadoDAO.class); servicio.asignaciones = mock(EmpleadoRolDAO.class);
        UUID id = UUID.randomUUID(); Empleado empleado = new Empleado(id); empleado.setNombre("Prueba"); empleado.setActivo(true);
        Rol rol = new Rol(UUID.randomUUID()); rol.setNombre(" BARISTA "); rol.setActivo(true);
        EmpleadoRol er = new EmpleadoRol(UUID.randomUUID()); er.setActivo(true); er.setIdRol(rol); er.setIdEmpleado(empleado);
        when(servicio.empleados.findParaSesion(id)).thenReturn(empleado);
        when(servicio.asignaciones.findRolesActivosParaSesion(id)).thenReturn(List.of(" BARISTA "));
        assertEquals(List.of("barista"), servicio.consultar(id).getRoles());
        when(servicio.asignaciones.findRolesActivosParaSesion(id)).thenReturn(List.of());
        assertTrue(servicio.consultar(id).getRoles().isEmpty());
        empleado.setActivo(false); assertNull(servicio.consultar(id));
        when(servicio.empleados.findParaSesion(id)).thenReturn(null); assertNull(servicio.consultar(id));
    }
}
