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

@Named("empleadoRolModel")
@ViewScoped
public class EmpleadoRolModel
        extends AbstractRelacionModel<
                EmpleadoRol,
                Empleado,
                Rol> {

    private static final long serialVersionUID = 1L;

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
                empleado.getIdEmpleado()
        );
    }

    @Override
    protected boolean principalValido(
            Empleado empleado) {

        return empleado != null
                && empleado.getIdEmpleado() != null;
    }

    @Override
    protected Rol buscarOpcion(String idOpcion) {

        if (idOpcion == null
                || idOpcion.isBlank()) {
            return null;
        }

        try {

            UUID idRol = UUID.fromString(idOpcion);

            return rolDAO.findById(idRol);

        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    protected boolean existeRelacion(
            Empleado empleado,
            Rol rol) {

        return empleadoRolDAO.existeRelacion(
                empleado.getIdEmpleado(),
                rol.getIdRol()
        );
    }

    @Override
    protected EmpleadoRol construirRelacion(
            Empleado empleado,
            Rol rol) {

        EmpleadoRol relacion = new EmpleadoRol();

        relacion.setIdEmpleadoRol(UUID.randomUUID());
        relacion.setIdEmpleado(empleado);
        relacion.setIdEmpleado(empleado);
        relacion.setIdRol(rol);
        relacion.setActivo(true);

        return relacion;
    }

    /*
     * Métodos utilizados desde Empleado.xhtml
     */

    public void cargarRoles() {
        cargarOpciones();
    }

    public void cargarEmpleado(Empleado empleado) {
        cargarPrincipal(empleado);
    }

    public void agregarRol() {
        agregarRelacion();
    }

    public void quitarRol(EmpleadoRol relacion) {
        quitarRelacion(relacion);
    }

    /*
     * Propiedades utilizadas desde la vista
     */

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

    public void setIdRolSeleccionado(
            String idRolSeleccionado) {

        setIdOpcionSeleccionada(idRolSeleccionado);
    }
}