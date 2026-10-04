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
import org.primefaces.event.TabChangeEvent;

@Named("empleadoModel")
@ViewScoped
public class EmpleadoModel extends AbstractModel<Empleado> {

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

    @Override
    protected Empleado crearNuevoRegistro() {

        Empleado empleado = new Empleado();

        empleado.setIdEmpleado(UUID.randomUUID());
        empleado.setActivo(true);

        return empleado;
    }

    /*
     * Cuando se crea un empleado nuevo todavía no permitimos
     * administrar roles hasta que el empleado sea guardado.
     */
    @Override
    public void btnNuevoHandler(ActionEvent ae) {

        super.btnNuevoHandler(ae);
        this.indiceTab = 0;
        limpiarRelaciones();
    }

    /*
     * Cuando seleccionamos un empleado,
     * cargamos sus roles.
     */
    @Override
    public void onRowSelect() {

        super.onRowSelect();
        this.indiceTab = 0;
        cargarRelacionesEmpleado();
    }

    /*
     * Si se quita la selección,
     * limpiamos también el modelo de roles.
     */
    @Override
    public void onRowUnselect() {

        super.onRowUnselect();
        this.indiceTab = 0;
        limpiarRelaciones();
    }

    /*
     * Al cancelar limpiamos Empleado
     * y sus relaciones.
     */
    @Override
    public void btnCancelarHandler(ActionEvent ae) {

        super.btnCancelarHandler(ae);
        this.indiceTab = 0;
        limpiarRelaciones();
    }

    @Override
    public void btnGuardarHandler(ActionEvent ae) {

        super.btnGuardarHandler(ae);

        this.indiceTab = 0;

        if (this.registro == null) {
            limpiarRelaciones();
        }
    }

    /*
     * Carga las relaciones del empleado seleccionado.
     */
    private void cargarRelacionesEmpleado() {

        empleadoRolModel
                .cargarEmpleado(this.registro);
    }

    /*
     * Limpia las relaciones cuando
     * no existe un empleado seleccionado.
     */
    private void limpiarRelaciones() {

        empleadoRolModel
                .cargarEmpleado(null);
    }

    /*
     * Indica si ya podemos administrar
     * las relaciones del Empleado.
     */
    public boolean isPuedeGestionarRelaciones() {

        return this.registro != null
                && this.registro.getIdEmpleado() != null
                && !this.nuevo;
    }

    public int getIndiceTab() {
        return indiceTab;
    }

    public void setIndiceTab(int indiceTab) {
        this.indiceTab = indiceTab;
    }
    public void onTabChange(TabChangeEvent event) {

if ("Asignar roles".equals(event.getTab().getTitle())) {
    this.indiceTab = 1;
} else {
    this.indiceTab = 0;
}
}
}
