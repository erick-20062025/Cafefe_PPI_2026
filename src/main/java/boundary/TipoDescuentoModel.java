package boundary;

import control.TipoDescuentoDAO;
import entity.TipoDescuento;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ActionEvent;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Named("tipoDescuentoModel")
@ViewScoped
public class TipoDescuentoModel implements Serializable {

    @Inject
    private TipoDescuentoDAO tipoDescuentoDAO;

    private List<TipoDescuento> modelo;
    private TipoDescuento registro;
    private boolean editando;
    private boolean nuevo;

    @PostConstruct
    public void init() {
        cargarDatos();
        //btnNuevoHandler();
    }

    public void cargarDatos() {
        if (tipoDescuentoDAO != null) {
            this.modelo = tipoDescuentoDAO.findAll();
        }
    }

    public void btnNuevoHandler(ActionEvent ae) {
    this.registro = new TipoDescuento();
    this.registro.setIdTipoDescuento(UUID.randomUUID());
    this.registro.setActivo(true);

    this.nuevo = true;
    this.editando = false;
}

    public void btnGuardarHandler(ActionEvent ae) {
    try {

        if (this.registro == null) {
            return;
        }

        if (nuevo) {
            tipoDescuentoDAO.crear(this.registro);
        } else if (editando) {
            tipoDescuentoDAO.modificar(this.registro);
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

    public void onRowSelect() {
        this.editando = false;
    }

    public void onRowUnselect() {
        this.registro = null;
        this.editando = false;
    }

    public void btnCancelarHandler(ActionEvent ae) {
    this.registro = null;
    this.nuevo = false;
    this.editando = false;
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

            tipoDescuentoDAO.eliminar(this.registro);

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
    // Getters y Setters
    public List<TipoDescuento> getModelo() {
        return modelo;
    }

    public void setModelo(List<TipoDescuento> modelo) {
        this.modelo = modelo;
    }

    public TipoDescuento getRegistro() {
        return registro;
    }

    public void setRegistro(TipoDescuento registro) {
        this.registro = registro;
    }

    public boolean isEditando() {
        return editando;
    }

    public boolean isNuevo() {
        return nuevo;
    }
}
