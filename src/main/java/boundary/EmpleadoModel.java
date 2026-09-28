package boundary;

import control.DAOInterface;
import control.EmpleadoDAO;
import entity.Empleado;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;

@Named("empleadoModel")
@ViewScoped
public class EmpleadoModel extends AbstractModel<Empleado> {

    @Inject
    private EmpleadoDAO empleadoDAO;

    @PostConstruct
    public void init() {
        cargarDatos();
    }

    @Override
    protected DAOInterface<Empleado> getDAO() {
        return empleadoDAO;
    }

    @Override
    protected Empleado crearNuevoRegistro() {
        Empleado empleado = new Empleado();
        empleado.setIdEmpleado(UUID.randomUUID());
        empleado.setActivo(true);
        return empleado;
    }
}