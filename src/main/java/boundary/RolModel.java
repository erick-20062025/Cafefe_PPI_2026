
package boundary;

import control.DAOInterface;
import control.RolDAO;
import entity.Rol;
import jakarta.annotation.PostConstruct;
import jakarta.faces.event.ActionEvent;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modelo JSF para administrar los roles del sistema.
 *
 * Gestiona las operaciones de la interfaz,
 * validaciones básicas y notificaciones al usuario.
 */
@Named("rolModel")
@ViewScoped
public class RolModel extends AbstractModel<Rol> {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER =
            Logger.getLogger(RolModel.class.getName());

    @Inject
    private RolDAO rolDAO;

    /**
     * Carga los roles al iniciar la vista.
     */
    @PostConstruct
    public void init() {
        cargarDatos();
    }

    @Override
    protected DAOInterface<Rol> getDAO() {
        return rolDAO;
    }

    /**
     * Prepara un rol nuevo con UUID y estado activo.
     *
     * @return nuevo rol
     */
    @Override
    protected Rol crearNuevoRegistro() {

        Rol rol = new Rol();
        rol.setIdRol(UUID.randomUUID());
        rol.setActivo(true);

        return rol;
    }

    /**
     * Valida los datos ingresados en el formulario.
     *
     * @return mensaje de error o null cuando es válido
     */
    private String validarFormulario() {

        if (registro == null) {
            return "Debe seleccionar o crear un rol";
        }

        if (registro.getIdRol() == null) {
            return "El identificador del rol es obligatorio";
        }

        String nombre = registro.getNombre();

        if (nombre == null || nombre.isBlank()) {
            return "El nombre del rol es obligatorio";
        }

        nombre = nombre.trim();

        if (nombre.length() < 2 || nombre.length() > 155) {
            return "El nombre debe tener entre 2 y 155 caracteres";
        }

        if (registro.getActivo() == null) {
            return "Debe seleccionar el estado del rol";
        }

        registro.setNombre(nombre);

        return null;
    }

    /**
     * Guarda un rol nuevo o modifica uno existente.
     *
     * Conserva la edición si la operación falla.
     */
    @Override
    public void btnGuardarHandler(ActionEvent ae) {

        if (!nuevo && !editando) {
            mostrarAdvertencia(
                    "Primero debe crear o editar un rol");
            return;
        }

        String error = validarFormulario();

        if (error != null) {
            mostrarAdvertencia(error);
            return;
        }

        try {

            if (nuevo) {
                rolDAO.crear(registro);
            } else {
                rolDAO.modificar(registro);
            }

        } catch (IllegalArgumentException e) {

            mostrarAdvertencia(e.getMessage());
            return;

        } catch (RuntimeException e) {

            LOGGER.log(
                    Level.SEVERE,
                    "Error al guardar el rol",
                    e
            );

            mostrarError(
                    "No se pudo guardar el rol");
            return;
        }

        limpiarEstado();

        try {
            cargarDatos();
        } catch (RuntimeException e) {

            LOGGER.log(
                    Level.WARNING,
                    "Rol guardado, pero falló la recarga",
                    e
            );

            mostrarAdvertencia(
                    "El rol se guardó, pero no se pudo "
                    + "actualizar la lista. Recargue la página");
            return;
        }

        mostrarExito("Rol guardado correctamente");
    }

    /**
     * Elimina el rol seleccionado.
     *
     * No modifica directamente las relaciones
     * existentes con empleados.
     */
    @Override
    public void btnEliminarHandler(ActionEvent ae) {

        if (registro == null) {
            mostrarAdvertencia(
                    "Debe seleccionar un rol");
            return;
        }

        try {

            rolDAO.eliminar(registro);

        } catch (IllegalArgumentException e) {

            mostrarAdvertencia(e.getMessage());
            return;

        } catch (RuntimeException e) {

            LOGGER.log(
                    Level.SEVERE,
                    "Error al eliminar el rol",
                    e
            );

            mostrarError(
                    "No se pudo eliminar el rol. "
                    + "Verifique si está asociado "
                    + "a algún empleado");
            return;
        }

        limpiarEstado();

        try {
            cargarDatos();
        } catch (RuntimeException e) {

            LOGGER.log(
                    Level.WARNING,
                    "Rol eliminado, pero falló la recarga",
                    e
            );

            mostrarAdvertencia(
                    "El rol se eliminó, pero no se pudo "
                    + "actualizar la lista. Recargue la página");
            return;
        }

        mostrarExito("Rol eliminado correctamente");
    }

    /**
     * Cancela la edición y recarga los datos
     * cuando podían existir cambios sin guardar.
     */
    @Override
    public void btnCancelarHandler(ActionEvent ae) {

        boolean estabaEditando = editando;

        limpiarEstado();

        if (estabaEditando) {
            try {
                cargarDatos();
            } catch (RuntimeException e) {

                LOGGER.log(
                        Level.WARNING,
                        "No se pudo recargar la lista de roles",
                        e
                );

                mostrarAdvertencia(
                        "No se pudo actualizar la lista de roles");
            }
        }
    }
}
