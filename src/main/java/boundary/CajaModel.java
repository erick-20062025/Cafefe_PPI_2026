
package boundary;

import control.CajaDAO;
import control.DAOInterface;
import entity.Caja;
import jakarta.annotation.PostConstruct;
import jakarta.faces.event.ActionEvent;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador JSF para administrar las cajas.
 *
 * Gestiona validaciones, operaciones del formulario
 * y mensajes para el usuario.
 *
 * @author erick
 */
@Named("cajaModel")
@ViewScoped
public class CajaModel extends AbstractModel<Caja> {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER =
            Logger.getLogger(CajaModel.class.getName());

    @Inject
    private CajaDAO cajaDAO;

    /**
     * Carga los datos al inicializar la vista.
     */
    @PostConstruct
    public void init() {
        cargarDatos();
    }

    @Override
    protected DAOInterface<Caja> getDAO() {
        return cajaDAO;
    }

    /**
     * Construye una caja nueva con UUID y estado activo.
     *
     * @return caja inicializada
     */
    @Override
    protected Caja crearNuevoRegistro() {

        Caja caja = new Caja();
        caja.setIdCaja(UUID.randomUUID());
        caja.setActivo(true);

        return caja;
    }

    /**
     * Valida los datos recibidos del formulario.
     *
     * @return mensaje de validación o null si es válido
     */
    private String validarFormulario() {

        if (registro == null) {
            return "Debe seleccionar o crear una caja";
        }

        if (registro.getIdCaja() == null) {
            return "El identificador es obligatorio";
        }

        String nombre = registro.getNombre();

        if (nombre == null || nombre.isBlank()) {
            return "El nombre de la caja es obligatorio";
        }

        nombre = nombre.trim();

        if (nombre.length() < 2 || nombre.length() > 155) {
            return "El nombre debe tener entre 2 y 155 caracteres";
        }

        if (registro.getActivo() == null) {
            return "Debe indicar el estado de la caja";
        }

        registro.setNombre(nombre);
        return null;
    }

    /**
     * Crea o modifica una caja.
     *
     * Conserva el formulario cuando ocurre un error.
     */
    @Override
    public void btnGuardarHandler(ActionEvent ae) {

        if (!nuevo && !editando) {
            mostrarAdvertencia(
                    "Primero debe crear o editar una caja");
            return;
        }

        String error = validarFormulario();

        if (error != null) {
            mostrarAdvertencia(error);
            return;
        }

        try {

            if (nuevo) {
                cajaDAO.crear(registro);
            } else {
                cajaDAO.modificar(registro);
            }

        } catch (IllegalArgumentException e) {

            mostrarAdvertencia(e.getMessage());
            return;

        } catch (RuntimeException e) {

            LOGGER.log(
                    Level.SEVERE,
                    "Error al guardar la caja",
                    e
            );

            mostrarError("No se pudo guardar la caja");
            return;
        }

        limpiarEstado();

        try {
            cargarDatos();
        } catch (RuntimeException e) {

            LOGGER.log(
                    Level.WARNING,
                    "Caja guardada, pero falló la recarga",
                    e
            );

            mostrarAdvertencia(
                    "La caja se guardó, pero no se pudo "
                    + "actualizar la lista. Recargue la página");
            return;
        }

        mostrarExito("Caja guardada correctamente");
    }

    /**
     * Elimina una caja sin facturas asociadas.
     */
    @Override
    public void btnEliminarHandler(ActionEvent ae) {

        if (registro == null) {
            mostrarAdvertencia(
                    "Debe seleccionar una caja");
            return;
        }

        try {

            cajaDAO.eliminar(registro);

        } catch (IllegalArgumentException e) {

            mostrarAdvertencia(e.getMessage());
            return;

        } catch (RuntimeException e) {

            LOGGER.log(
                    Level.SEVERE,
                    "Error al eliminar la caja",
                    e
            );

            mostrarError(
                    "No se pudo eliminar la caja. "
                    + "Verifique si tiene facturas asociadas");
            return;
        }

        limpiarEstado();

        try {
            cargarDatos();
        } catch (RuntimeException e) {

            LOGGER.log(
                    Level.WARNING,
                    "Caja eliminada, pero falló la recarga",
                    e
            );

            mostrarAdvertencia(
                    "La caja se eliminó, pero no se pudo "
                    + "actualizar la lista. Recargue la página");
            return;
        }

        mostrarExito("Caja eliminada correctamente");
    }

    /**
     * Cancela la edición y recarga la lista si
     * pudieron modificarse objetos en memoria.
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
                        "No se pudo recargar la lista de cajas",
                        e
                );

                mostrarAdvertencia(
                        "No se pudo actualizar la lista de cajas");
            }
        }
    }
}
