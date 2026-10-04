/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
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
 *
 * @author erick
 */
@Named("productoModel")
@ViewScoped
public class ProductoModel extends AbstractModel<Producto> {

    @Inject
    private ProductoDAO productoDAO;

    @Inject
    private ProductoTipoProductoModel productoTipoProductoModel;

    @Inject
    private ProductoCaracteristicaModel productoCaracteristicaModel;

    @PostConstruct
    public void init() {
        cargarDatos();

    }
    private int indiceTab;

    @Override
    protected DAOInterface<Producto> getDAO() {
        return productoDAO;
    }

    @Override
    protected Producto crearNuevoRegistro() {

        Producto producto = new Producto();

        producto.setIdProducto(UUID.randomUUID());
        producto.setActivo(true);

        return producto;
    }

    /*
     * Cuando se crea un producto nuevo todavía no permitimos
     * administrar relaciones hasta que el producto sea guardado.
     */
    @Override
    public void btnNuevoHandler(ActionEvent ae) {
        super.btnNuevoHandler(ae);

        this.indiceTab = 0;
        limpiarRelaciones();
    }

    @Override
    public void onRowSelect() {
        super.onRowSelect();

        this.indiceTab = 0;
        cargarRelacionesProducto();
    }

    @Override
    public void onRowUnselect() {
        super.onRowUnselect();

        this.indiceTab = 0;
        limpiarRelaciones();
    }

    @Override
    public void btnCancelarHandler(ActionEvent ae) {
        super.btnCancelarHandler(ae);

        this.indiceTab = 0;
        limpiarRelaciones();
    }

    /*
     * Carga todas las relaciones del producto seleccionado.
     */
    private void cargarRelacionesProducto() {

        productoTipoProductoModel
                .cargarProducto(this.registro);

        productoCaracteristicaModel
                .cargarProducto(this.registro);
    }

    /*
     * Limpia todas las relaciones cuando
     * no existe un producto seleccionado.
     */
    private void limpiarRelaciones() {

        productoTipoProductoModel
                .cargarProducto(null);

        productoCaracteristicaModel
                .cargarProducto(null);
    }

    /*
     * Indica si ya podemos administrar
     * relaciones del Producto.
     */
    public boolean isPuedeGestionarRelaciones() {

        return this.registro != null
                && this.registro.getIdProducto() != null
                && !this.nuevo;
    }

    /*
     * Se conserva porque Producto.xhtml
     * actualmente utiliza este nombre.
     */
    public boolean isPuedeGestionarTipos() {

        return isPuedeGestionarRelaciones();
    }

    public void onTabChange(TabChangeEvent event) {

        if ("Relaciones".equals(event.getTab().getTitle())) {
            this.indiceTab = 1;
        } else {
            this.indiceTab = 0;
        }
    }

    public int getIndiceTab() {
        return indiceTab;
    }

    public void setIndiceTab(int indiceTab) {
        this.indiceTab = indiceTab;
    }
}
