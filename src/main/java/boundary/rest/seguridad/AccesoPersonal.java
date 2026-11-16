package boundary.rest.seguridad;

import control.EmpleadoDAO;
import control.EmpleadoRolDAO;
import entity.Empleado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Identidad exclusivamente de servidor; los permisos se revalidan en los DAO. */
@ApplicationScoped
public class AccesoPersonal {
    public static final String ATRIBUTO = AccesoPersonal.class.getName() + ".empleado";
    private static final Set<String> PERMITIDOS = Set.of("barista", "cocinero", "cajero", "camarero", "administrador");
    @Inject EmpleadoDAO empleados;
    @Inject EmpleadoRolDAO asignaciones;

    public Identidad consultar(UUID id) {
        Empleado empleado = empleados.findParaSesion(id);
        if (empleado == null || !Boolean.TRUE.equals(empleado.getActivo())) return null;
        List<String> nombres = asignaciones.findRolesActivosParaSesion(id);
        List<String> roles = nombres == null ? List.of() : nombres.stream()
                .filter(java.util.Objects::nonNull).map(nombre -> nombre.trim().toLowerCase(Locale.ROOT))
                .filter(PERMITIDOS::contains).distinct().sorted().toList();
        return new Identidad(empleado.getNombre(), roles);
    }

    public Identidad recuperar(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion == null) return null;
        Object id = sesion.getAttribute(ATRIBUTO);
        if (!(id instanceof UUID uuid)) return null;
        Identidad identidad = consultar(uuid);
        if (identidad == null || identidad.getRoles().isEmpty()) {
            sesion.invalidate();
            return null;
        }
        return identidad;
    }

    public static final class Identidad {
        private final String nombre;
        private final List<String> roles;
        public Identidad(String nombre, List<String> roles) { this.nombre = nombre; this.roles = List.copyOf(roles); }
        public String getNombre() { return nombre; }
        public boolean getActivo() { return true; }
        public List<String> getRoles() { return roles; }
    }
}
