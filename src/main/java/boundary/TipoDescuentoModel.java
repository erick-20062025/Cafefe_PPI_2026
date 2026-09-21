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
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Named("tipoDescuentoModel")
@ViewScoped
public class TipoDescuentoModel implements Serializable {

    @Inject
    private TipoDescuentoDAO tipoDescuentoDAO;

    private List<TipoDescuento> modelo;
    private TipoDescuento registro;

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
    
    // ASIGNAR COMO OBJETO UUID (Sin .toString())
    this.registro.setIdTipoDescuento(java.util.UUID.randomUUID());
    
    this.registro.setActivo(true);
}

public void btnGuardarHandler(ActionEvent ae) {
    try {
        // Generar UUID si no existe
        if (this.registro != null && this.registro.getIdTipoDescuento() == null) {
            this.registro.setIdTipoDescuento(java.util.UUID.randomUUID());
        }

        tipoDescuentoDAO.crear(this.registro);
        
        // Limpiar registro para ocultar el formulario y volver a la tabla
        this.registro = null;
        this.modelo = tipoDescuentoDAO.findAll();

        FacesContext.getCurrentInstance().addMessage(null, 
            new FacesMessage(FacesMessage.SEVERITY_INFO, "Éxito", "Registro guardado correctamente"));

    } catch (Exception e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ConstraintViolationException) {
                ConstraintViolationException cve = (ConstraintViolationException) cause;
                for (ConstraintViolation<?> v : cve.getConstraintViolations()) {
                    System.err.println("ERROR DE VALIDACIÓN EN: " + v.getPropertyPath() + " -> " + v.getMessage());
                    FacesContext.getCurrentInstance().addMessage(null, 
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error en " + v.getPropertyPath(), v.getMessage()));
                }
                break;
            }
            cause = cause.getCause();
        }
    }
}

    public void btnCancelarHandler(ActionEvent ae) {
        this.registro = null; // Regresa a la tabla sin cambios
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
}
