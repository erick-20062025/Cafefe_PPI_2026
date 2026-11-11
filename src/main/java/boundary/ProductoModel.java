package boundary;

import control.DAOInterface;
import control.ProductoDAO;
import entity.Producto;
import jakarta.annotation.PostConstruct;
import jakarta.faces.event.ActionEvent;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;
import org.primefaces.event.TabChangeEvent;

/**
 * Controlador de Producto. Coordina sus tipos, características y descuentos.
 * Primero se guarda el producto, después se gestionan sus asociaciones.
 */
@Named("productoModel")
@ViewScoped
public class ProductoModel extends AbstractModel<Producto> {

    @Inject private ProductoDAO productoDAO;
    @Inject private ProductoTipoProductoModel productoTipoProductoModel;
    @Inject private ProductoCaracteristicaModel productoCaracteristicaModel;
    @Inject private ProductoDescuentoModel productoDescuentoModel;

    private int indiceTab;

    @PostConstruct
    public void init() { cargarDatos(); }

    @Override
    protected DAOInterface<Producto> getDAO() { return productoDAO; }

    @Override
    protected Producto crearNuevoRegistro() {
        Producto producto = new Producto();
        producto.setIdProducto(UUID.randomUUID());
        producto.setActivo(true);
        return producto;
    }

    /** Evita mostrar relaciones de un producto todavía sin guardar. */
    @Override
    public void btnNuevoHandler(ActionEvent ae) {
        super.btnNuevoHandler(ae);
        indiceTab = 0;
        limpiarRelaciones();
    }

    /** Sincroniza las tres secciones de relaciones con el producto seleccionado. */
    @Override
    public void onRowSelect() {
        super.onRowSelect();
        indiceTab = 0;
        cargarRelacionesProducto();
    }

    @Override
    public void onRowUnselect() {
        super.onRowUnselect();
        indiceTab = 0;
        limpiarRelaciones();
    }

    @Override
    public void btnCancelarHandler(ActionEvent ae) {
        super.btnCancelarHandler(ae);
        indiceTab = 0;
        limpiarRelaciones();
    }

    private void cargarRelacionesProducto() {
        productoTipoProductoModel.cargarProducto(registro);
        productoCaracteristicaModel.cargarProducto(registro);
        // El null-check mantiene compatibles los tests antiguos que todavía
        // no inyectan el tercer modelo. En el contenedor CDI se inyecta normalmente.
        if (productoDescuentoModel != null) {
            productoDescuentoModel.cargarProducto(registro);
        }
    }

    private void limpiarRelaciones() {
        productoTipoProductoModel.cargarProducto(null);
        productoCaracteristicaModel.cargarProducto(null);
        if (productoDescuentoModel != null) {
            productoDescuentoModel.cargarProducto(null);
        }
    }

    public boolean isPuedeGestionarRelaciones() {
        return registro != null && registro.getIdProducto() != null && !nuevo;
    }

    public boolean isPuedeGestionarTipos() { return isPuedeGestionarRelaciones(); }

    public void onTabChange(TabChangeEvent event) {
        if (event != null && event.getTab() != null
                && "Relaciones".equals(event.getTab().getTitle())) {
            indiceTab = 1;
        } else {
            indiceTab = 0;
        }
    }

    public int getIndiceTab() { return indiceTab; }
    public void setIndiceTab(int indiceTab) { this.indiceTab = indiceTab; }
}
