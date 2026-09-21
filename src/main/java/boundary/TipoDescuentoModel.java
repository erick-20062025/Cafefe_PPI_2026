package boundary;

import control.TipoDescuentoDAO;
import entity.TipoDescuento;
import jakarta.annotation.PostConstruct;
import jakarta.faces.event.ActionEvent;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
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
    this.registro.setActivo(true); // valor por defecto opcional
}

public void btnGuardarHandler(ActionEvent ae) {
    // 1. Guardar o actualizar en base de datos usando tu servicio/EJB
    tipoDescuentoDAO.crear(this.registro); 
    
    // 2. Limpiar registro para ocultar el formulario y regresar a la tabla
    this.registro = null;
    
    // 3. Recargar la lista de la tabla
    this.modelo = tipoDescuentoDAO.findAll();
}

public void btnCancelarHandler(ActionEvent ae) {
    this.registro = null; // Regresa a la tabla sin cambios
}

    // Getters y Setters
    public List<TipoDescuento> getModelo() { return modelo; }
    public void setModelo(List<TipoDescuento> modelo) { this.modelo = modelo; }
    public TipoDescuento getRegistro() { return registro; }
    public void setRegistro(TipoDescuento registro) { this.registro = registro; }
}