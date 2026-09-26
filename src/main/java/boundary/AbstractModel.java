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

    private static final long serialVersionUID = 1L;

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


    /* =========================================================
       CARGA DE DATOS
       ========================================================= */

    public void cargarDatos() {
        this.modelo = getDAO().findAll();
    }


    /* =========================================================
       CRUD DE LA VISTA
       ========================================================= */

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

            limpiarEstado();

            cargarDatos();

            mostrarExito(
                    "Registro guardado correctamente"
            );

        } catch (Exception e) {

            e.printStackTrace();

            mostrarError(
                    "No se pudo guardar el registro"
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

                limpiarEstado();

                cargarDatos();

                mostrarExito(
                        "Registro eliminado correctamente"
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

            mostrarError(
                    "No se pudo eliminar el registro"
            );
        }
    }


    public void btnCancelarHandler(ActionEvent ae) {

        limpiarEstado();
    }


    /* =========================================================
       SELECCIÓN DE FILAS
       ========================================================= */

    public void onRowSelect() {

        this.editando = false;
        this.nuevo = false;
    }


    public void onRowUnselect() {

        limpiarEstado();
    }


    /* =========================================================
       MÉTODOS REUTILIZABLES
       ========================================================= */

    /**
     * Limpia el registro seleccionado y devuelve
     * el Model a su estado normal.
     */
    protected void limpiarEstado() {

        this.registro = null;
        this.nuevo = false;
        this.editando = false;
    }


    /**
     * Muestra un mensaje de éxito.
     */
    protected void mostrarExito(String detalle) {

        mostrarMensaje(
                FacesMessage.SEVERITY_INFO,
                "Éxito",
                detalle
        );
    }


    /**
     * Muestra un mensaje de advertencia.
     */
    protected void mostrarAdvertencia(String detalle) {

        mostrarMensaje(
                FacesMessage.SEVERITY_WARN,
                "Advertencia",
                detalle
        );
    }


    /**
     * Muestra un mensaje de error.
     */
    protected void mostrarError(String detalle) {

        mostrarMensaje(
                FacesMessage.SEVERITY_ERROR,
                "Error",
                detalle
        );
    }


    /**
     * Método común utilizado por todos los mensajes.
     */
    private void mostrarMensaje(
            FacesMessage.Severity severity,
            String titulo,
            String detalle) {

        FacesContext.getCurrentInstance()
                .addMessage(
                        null,
                        new FacesMessage(
                                severity,
                                titulo,
                                detalle
                        )
                );
    }


    /* =========================================================
       GETTERS Y SETTERS
       ========================================================= */

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