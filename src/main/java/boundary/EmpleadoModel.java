
package boundary;

import control.DAOInterface;
import control.EmpleadoDAO;
import entity.Empleado;
import jakarta.annotation.PostConstruct;
import jakarta.faces.event.ActionEvent;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.primefaces.event.TabChangeEvent;

/**
 * Modelo JSF para la administración de empleados.
 *
 * Coordina los datos del empleado y la gestión
 * de roles mediante EmpleadoRolModel.
 */
@Named("empleadoModel")
@ViewScoped
public class EmpleadoModel extends AbstractModel<Empleado> {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER =
            Logger.getLogger(EmpleadoModel.class.getName());

    @Inject
    private EmpleadoDAO empleadoDAO;

    @Inject
    private EmpleadoRolModel empleadoRolModel;

    private int indiceTab;

    @PostConstruct
    public void init() {
        cargarDatos();
    }

    @Override
    protected DAOInterface<Empleado> getDAO() {
        return empleadoDAO;
    }

    /**
     * Prepara un empleado nuevo con UUID y estado activo.
     */
    @Override
    protected Empleado crearNuevoRegistro() {

        Empleado empleado = new Empleado();
        empleado.setIdEmpleado(UUID.randomUUID());
        empleado.setActivo(true);

        return empleado;
    }

    /**
     * Valida los campos recibidos del formulario.
     *
     * @return descripción del error o null si es válido
     */
    private String validarFormulario() {

        if (registro == null
                || registro.getIdEmpleado() == null) {
            return "Debe seleccionar un empleado válido";
        }

        String nombre = registro.getNombre();
        String apellido = registro.getApellido();

        if (nombre == null || nombre.isBlank()) {
            return "El nombre es obligatorio";
        }

        if (apellido == null || apellido.isBlank()) {
            return "El apellido es obligatorio";
        }

        nombre = nombre.trim();
        apellido = apellido.trim();

        if (nombre.length() < 2 || nombre.length() > 255) {
            return "El nombre debe tener entre 2 y 255 caracteres";
        }

        if (apellido.length() < 2 || apellido.length() > 255) {
            return "El apellido debe tener entre 2 y 255 caracteres";
        }

        if (registro.getActivo() == null) {
            return "Debe indicar el estado del empleado";
        }

        registro.setNombre(nombre);
        registro.setApellido(apellido);

        return null;
    }

    @Override
    public void btnNuevoHandler(ActionEvent ae) {

        super.btnNuevoHandler(ae);
        indiceTab = 0;
        limpiarRelaciones();
    }

    @Override
    public void onRowSelect() {

        super.onRowSelect();
        indiceTab = 0;

        try {
            cargarRelacionesEmpleado();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE,
                    "Error al consultar roles del empleado", e);
            limpiarRelaciones();
            mostrarError(
                    "No se pudieron consultar los roles del empleado");
        }
    }

    @Override
    public void onRowUnselect() {

        super.onRowUnselect();
        indiceTab = 0;
        limpiarRelaciones();
    }

    /**
     * Guarda el empleado y notifica el resultado.
     *
     * Conserva los datos del formulario si falla
     * la operación de persistencia.
     */
    @Override
    public void btnGuardarHandler(ActionEvent ae) {

        if (!nuevo && !editando) {
            mostrarAdvertencia(
                    "Primero debe crear o editar un empleado");
            return;
        }

        String error = validarFormulario();

        if (error != null) {
            mostrarAdvertencia(error);
            return;
        }

        try {

            if (nuevo) {
                empleadoDAO.crear(registro);
            } else {
                empleadoDAO.modificar(registro);
            }

        } catch (IllegalArgumentException e) {

            mostrarAdvertencia(e.getMessage());
            return;

        } catch (RuntimeException e) {

            LOGGER.log(Level.SEVERE,
                    "Error al guardar el empleado", e);
            mostrarError("No se pudo guardar el empleado");
            return;
        }

        limpiarEstado();
        indiceTab = 0;
        limpiarRelaciones();

        try {
            cargarDatos();
        } catch (RuntimeException e) {

            LOGGER.log(Level.WARNING,
                    "Empleado guardado; falló la recarga", e);
            mostrarAdvertencia(
                    "El empleado se guardó, pero no se pudo "
                    + "actualizar la lista. Recargue la página");
            return;
        }

        mostrarExito("Empleado guardado correctamente");
    }

    /**
     * Elimina un empleado cuando no tiene relaciones.
     */
    @Override
    public void btnEliminarHandler(ActionEvent ae) {

        if (registro == null) {
            mostrarAdvertencia(
                    "Debe seleccionar un empleado");
            return;
        }

        try {
            empleadoDAO.eliminar(registro);

        } catch (IllegalArgumentException e) {

            mostrarAdvertencia(e.getMessage());
            return;

        } catch (RuntimeException e) {

            LOGGER.log(Level.SEVERE,
                    "Error al eliminar el empleado", e);
            mostrarError(
                    "No se pudo eliminar el empleado. "
                    + "Verifique sus relaciones");
            return;
        }

        limpiarEstado();
        indiceTab = 0;
        limpiarRelaciones();

        try {
            cargarDatos();
        } catch (RuntimeException e) {

            LOGGER.log(Level.WARNING,
                    "Empleado eliminado; falló la recarga", e);
            mostrarAdvertencia(
                    "El empleado se eliminó, pero no se pudo "
                    + "actualizar la lista. Recargue la página");
            return;
        }

        mostrarExito("Empleado eliminado correctamente");
    }

    /**
     * Cancela la edición sin guardar cambios.
     * Recarga los registros si se estaba editando.
     */
    @Override
    public void btnCancelarHandler(ActionEvent ae) {

        boolean estabaEditando = editando;

        super.btnCancelarHandler(ae);
        indiceTab = 0;
        limpiarRelaciones();

        if (estabaEditando) {
            try {
                cargarDatos();
            } catch (RuntimeException e) {

                LOGGER.log(Level.WARNING,
                        "Error al recargar empleados", e);
                mostrarAdvertencia(
                        "No se pudo actualizar la lista de empleados");
            }
        }
    }

    private void cargarRelacionesEmpleado() {
        empleadoRolModel.cargarEmpleado(registro);
    }

    private void limpiarRelaciones() {
        empleadoRolModel.cargarEmpleado(null);
    }

    /**
     * Solo permite gestionar roles de empleados
     * que ya están registrados.
     */
    public boolean isPuedeGestionarRelaciones() {
        return registro != null
                && registro.getIdEmpleado() != null
                && !nuevo;
    }

    public int getIndiceTab() {
        return indiceTab;
    }

    public void setIndiceTab(int indiceTab) {
        this.indiceTab = indiceTab;
    }

    /**
     * Mantiene sincronizada la pestaña seleccionada.
     */
    public void onTabChange(TabChangeEvent event) {

        if (event == null || event.getTab() == null) {
            return;
        }

        indiceTab = "Asignar roles".equals(
                event.getTab().getTitle()) ? 1 : 0;
    }
}
