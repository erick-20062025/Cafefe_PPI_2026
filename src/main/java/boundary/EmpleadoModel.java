package boundary;

import entity.Empleado;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Named("empleadoModel")
@ViewScoped
public class EmpleadoModel implements Serializable {

    private List<Empleado> modelo;
    private Empleado registro;

    @PostConstruct
    public void init() {
        this.modelo = new ArrayList<>();
        btnNuevoHandler();
    }

    public void btnNuevoHandler() {
        this.registro = new Empleado();
        this.registro.setIdEmpleado(UUID.randomUUID());
    }

    public void btnGuardarHandler() {
        if (registro != null && !modelo.contains(registro)) {
            modelo.add(registro);
        }
        btnNuevoHandler();
    }

    public void btnModificarHandler() {
        // Lógica de actualización
    }

    public void btnEliminarHandler() {
        if (registro != null) {
            modelo.remove(registro);
            btnNuevoHandler();
        }
    }

    public void btnCancelarHandler() {
        btnNuevoHandler();
    }

    public void seleccionarRegistro() {
        // Selección desde la tabla
    }

    // Getters y Setters
    public List<Empleado> getModelo() {
        return modelo;
    }

    public void setModelo(List<Empleado> modelo) {
        this.modelo = modelo;
    }

    public Empleado getRegistro() {
        return registro;
    }

    public void setRegistro(Empleado registro) {
        this.registro = registro;
    }
}