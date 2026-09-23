/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary;
import control.DAOInterface;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ActionEvent;
import java.io.Serializable;
import java.util.List;

/**
 *
 * @author diegoli
 */
public abstract class AbstractModel<T> implements Serializable {

    protected List<T> modelo;
    protected T registro;

    protected boolean editando;
    protected boolean nuevo;

    /*
     * Cada Model hijo debe indicar qué DAO utilizar.
     */
    protected abstract DAOInterface<T> getDAO();

    /*
     * Cada Model hijo debe saber cómo crear
     * una nueva instancia de su entidad.
     */
    protected abstract T crearNuevoRegistro();

    public void cargarDatos() {
        this.modelo = getDAO().findAll();
    }

    public void btnNuevoHandler(ActionEvent ae) {

        this.registro = crearNuevoRegistro();

        this.nuevo = true;
        this.editando = false;
    }

    public void btnGuardarHandler(ActionEvent ae) {

        try {

            if (this.registro == null) {
                return;
            }

            if (nuevo) {

                getDAO().crear(this.registro);

            } else if (editando) {

                getDAO().modificar(this.registro);
            }

            this.registro = null;
            this.nuevo = false;
            this.editando = false;

            cargarDatos();

            FacesContext.getCurrentInstance().addMessage(
                    null,
                    new FacesMessage(
                            FacesMessage.SEVERITY_INFO,
                            "Éxito",
                            "Registro guardado correctamente"
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            FacesContext.getCurrentInstance().addMessage(
                    null,
                    new FacesMessage(
                            FacesMessage.SEVERITY_ERROR,
                            "Error",
                            "No se pudo guardar el registro"
                    )
            );
        }
    }

    public void btnModificarHandler(ActionEvent ae) {

        if (this.registro != null) {

            this.editando = true;
            this.nuevo = false;
        }
    }

    public void btnEliminarHandler(ActionEvent ae) {

        try {

            if (this.registro != null) {

                getDAO().eliminar(this.registro);

                this.registro = null;
                this.nuevo = false;
                this.editando = false;

                cargarDatos();

                FacesContext.getCurrentInstance().addMessage(
                        null,
                        new FacesMessage(
                                FacesMessage.SEVERITY_INFO,
                                "Éxito",
                                "Registro eliminado correctamente"
                        )
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

            FacesContext.getCurrentInstance().addMessage(
                    null,
                    new FacesMessage(
                            FacesMessage.SEVERITY_ERROR,
                            "Error",
                            "No se pudo eliminar el registro"
                    )
            );
        }
    }

    public void btnCancelarHandler(ActionEvent ae) {

        this.registro = null;
        this.nuevo = false;
        this.editando = false;
    }

    public void onRowSelect() {
        this.editando = false;
        this.nuevo = false;
    }

    public void onRowUnselect() {
        this.registro = null;
        this.editando = false;
        this.nuevo = false;
    }

    public List<T> getModelo() {
        return modelo;
    }

    public void setModelo(List<T> modelo) {
        this.modelo = modelo;
    }

    public T getRegistro() {
        return registro;
    }

    public void setRegistro(T registro) {
        this.registro = registro;
    }

    public boolean isEditando() {
        return editando;
    }

    public boolean isNuevo() {
        return nuevo;
    }
}
