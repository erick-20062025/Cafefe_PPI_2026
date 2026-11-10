
package boundary;

import control.DAOInterface;
import control.EmpleadoRolDAO;
import control.RolDAO;
import entity.Empleado;
import entity.EmpleadoRol;
import entity.Rol;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modelo JSF para asignar y quitar roles de empleados.
 *
 * Reutiliza AbstractRelacionModel y comunica los
 * errores de negocio mediante mensajes de la vista.
 */
@Named("empleadoRolModel")
@ViewScoped
public class EmpleadoRolModel
        extends AbstractRelacionModel<EmpleadoRol, Empleado, Rol> {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER =
            Logger.getLogger(EmpleadoRolModel.class.getName());

    @Inject
    private EmpleadoRolDAO empleadoRolDAO;

    @Inject
    private RolDAO rolDAO;

    @PostConstruct
    public void init() {
        cargarOpciones();
    }

    @Override
    protected DAOInterface<EmpleadoRol> getDAO() {
        return empleadoRolDAO;
    }

    @Override
    protected List<Rol> consultarOpcionesDisponibles() {
        return rolDAO.findAll();
    }

    @Override
    protected List<EmpleadoRol> consultarRelaciones(
            Empleado empleado) {

        return empleadoRolDAO.findByIdEmpleado(
                empleado.getIdEmpleado());
    }

    @Override
    protected boolean principalValido(Empleado empleado) {
        return empleado != null
                && empleado.getIdEmpleado() != null;
    }

    /**
     * Busca un rol mediante el UUID recibido desde JSF.
     */
    @Override
    protected Rol buscarOpcion(String idOpcion) {

        if (idOpcion == null || idOpcion.isBlank()) {
            return null;
        }

        try {
            UUID id = UUID.fromString(idOpcion);
            return rolDAO.findById(id);

        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    protected boolean existeRelacion(Empleado empleado, Rol rol) {
        return empleadoRolDAO.existeRelacion(
                empleado.getIdEmpleado(),
                rol.getIdRol());
    }

    @Override
    protected EmpleadoRol construirRelacion(
            Empleado empleado, Rol rol) {

        EmpleadoRol relacion = new EmpleadoRol();
        relacion.setIdEmpleadoRol(UUID.randomUUID());
        relacion.setIdEmpleado(empleado);
        relacion.setIdRol(rol);
        relacion.setActivo(true);

        return relacion;
    }

    /**
     * Comprueba que el rol sea asignable.
     */
    protected boolean opcionValida(Rol rol) {

        if (rol == null) {
            mostrarAdvertencia("Debe seleccionar un rol");
            return false;
        }

        if (!Boolean.TRUE.equals(rol.getActivo())) {
            mostrarAdvertencia(
                    "No se puede asignar un rol inactivo");
            return false;
        }

        return true;
    }

    public void cargarRoles() {
        cargarOpciones();
    }

    public void cargarEmpleado(Empleado empleado) {
        cargarPrincipal(empleado);
    }

/**
 * Asigna un rol al empleado seleccionado.
 *
 * Valida la selección, informa errores de negocio
 * y registra los fallos inesperados.
 */
public void agregarRol() {

    Empleado empleado = getPrincipal();

    if (!principalValido(empleado)) {
        mostrarAdvertencia("Debe seleccionar un empleado");
        return;
    }

    String idSeleccionado = getIdOpcionSeleccionada();

    if (idSeleccionado == null
            || idSeleccionado.isBlank()) {
        mostrarAdvertencia("Debe seleccionar un rol");
        return;
    }

    try {
        Rol rol = buscarOpcion(idSeleccionado);

        if (rol == null) {
            mostrarError("El rol seleccionado no existe");
            return;
        }

        if (!opcionValida(rol)) {
            return;
        }

        EmpleadoRol relacion =
                construirRelacion(empleado, rol);

        empleadoRolDAO.crear(relacion);

    } catch (IllegalArgumentException e) {
        mostrarAdvertencia(e.getMessage());
        return;

    } catch (RuntimeException e) {
        LOGGER.log(
                Level.SEVERE,
                "Error al asignar rol",
                e
        );

        mostrarError("No se pudo asignar el rol");
        return;
    }

    try {
        cargarEmpleado(empleado);

    } catch (RuntimeException e) {
        LOGGER.log(
                Level.WARNING,
                "Rol asignado; falló la recarga",
                e
        );

        mostrarAdvertencia(
                "El rol se asignó, pero no se pudo "
                + "actualizar la lista");
        return;
    }

    mostrarExito("Rol asignado correctamente");
}


    /**
     * Quita una relación únicamente cuando pertenece
     * al empleado seleccionado.
     */
    public void quitarRol(EmpleadoRol relacion) {

        Empleado empleado = getPrincipal();

        if (!principalValido(empleado)
                || relacion == null
                || relacion.getIdEmpleado() == null
                || relacion.getIdEmpleado().getIdEmpleado() == null
                || !empleado.getIdEmpleado().equals(
                        relacion.getIdEmpleado().getIdEmpleado())) {

            mostrarAdvertencia(
                    "La asignación no pertenece al empleado seleccionado");
            return;
        }

        try {

            empleadoRolDAO.eliminar(relacion);

        } catch (IllegalArgumentException e) {

            mostrarAdvertencia(e.getMessage());
            return;

        } catch (RuntimeException e) {

            LOGGER.log(Level.SEVERE,
                    "Error al quitar rol", e);
            mostrarError("No se pudo quitar el rol");
            return;
        }

        try {
            cargarEmpleado(empleado);
        } catch (RuntimeException e) {

            LOGGER.log(Level.WARNING,
                    "Rol quitado; falló la recarga", e);
            mostrarAdvertencia(
                    "La asignación se eliminó, pero no se pudo "
                    + "actualizar la lista");
            return;
        }

        mostrarExito("Rol eliminado del empleado");
    }

    public Empleado getEmpleado() {
        return getPrincipal();
    }

    public void setEmpleado(Empleado empleado) {
        setPrincipal(empleado);
    }

    public List<Rol> getRolList() {
        return getOpcionesDisponibles();
    }

    public void setRolList(List<Rol> lista) {
        setOpcionesDisponibles(lista);
    }

    public String getIdRolSeleccionado() {
        return getIdOpcionSeleccionada();
    }

    public void setIdRolSeleccionado(String idRol) {
        setIdOpcionSeleccionada(idRol);
    }
}
