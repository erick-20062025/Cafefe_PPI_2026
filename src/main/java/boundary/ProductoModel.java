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

    @PostConstruct
    public void init() {
        cargarDatos();
    }

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
     * administrar sus tipos hasta que el producto se guarde.
     */
    @Override
    public void btnNuevoHandler(ActionEvent ae) {

        super.btnNuevoHandler(ae);

        productoTipoProductoModel.cargarProducto(null);
    }

    /*
     * Cuando seleccionamos un producto de la tabla,
     * cargamos también sus tipos asociados.
     */
    @Override
    public void onRowSelect() {

        super.onRowSelect();

        productoTipoProductoModel.cargarProducto(this.registro);
    }

    /*
     * Si se quita la selección, limpiamos también
     * el modelo de relaciones.
     */
    @Override
    public void onRowUnselect() {

        super.onRowUnselect();

        productoTipoProductoModel.cargarProducto(null);
    }

    /*
     * Al cancelar limpiamos tanto Producto como
     * ProductoTipoProducto.
     */
    @Override
    public void btnCancelarHandler(ActionEvent ae) {

        super.btnCancelarHandler(ae);

        productoTipoProductoModel.cargarProducto(null);
    }

    /*
     * Sirve para la vista.
     *
     * Solo permitimos administrar tipos cuando estamos
     * trabajando con un Producto que ya existe.
     */
    public boolean isPuedeGestionarTipos() {

        return this.registro != null
                && this.registro.getIdProducto() != null
                && !this.nuevo;
    }
}
