package boundary;

import control.DAOInterface;
import control.DescuentoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.TipoDescuento;
import jakarta.annotation.PostConstruct;
import jakarta.faces.event.ActionEvent;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;
import java.util.UUID;

@Named("descuentoModel")
@ViewScoped
public class DescuentoModel extends AbstractModel<Descuento> {

    private String idTipoDescuentoSeleccionado;

    @Inject
    private DescuentoDAO descuentoDAO;

    @Inject
    private TipoDescuentoDAO tipoDescuentoDAO;

    @Inject
    private DescuentoProductoModel descuentoProductoModel;

    private List<TipoDescuento> tiposDescuento;

    @PostConstruct
    public void init() {

        cargarDatos();

        tiposDescuento
                = tipoDescuentoDAO.findAll();
    }

    @Override
    protected DAOInterface<Descuento> getDAO() {
        return descuentoDAO;
    }

    @Override
    protected Descuento crearNuevoRegistro() {

        Descuento descuento
                = new Descuento();

        descuento.setIdDescuento(
                UUID.randomUUID()
        );

        return descuento;
    }

    /*
     * Un descuento nuevo todavía no puede administrar
     * productos hasta que haya sido guardado.
     */
    @Override
    public void btnNuevoHandler(ActionEvent ae) {

        super.btnNuevoHandler(ae);

        idTipoDescuentoSeleccionado = null;

        limpiarProductos();
    }

    /*
     * Al seleccionar un descuento:
     *
     * - sincronizamos el tipo de descuento del combo;
     * - cargamos sus productos relacionados.
     */
    @Override
    public void onRowSelect() {

        super.onRowSelect();

        if (this.registro != null
                && this.registro.getIdTipoDescuento() != null) {

            idTipoDescuentoSeleccionado
                    = this.registro
                            .getIdTipoDescuento()
                            .toString();

        } else {

            idTipoDescuentoSeleccionado = null;
        }

        descuentoProductoModel
                .cargarDescuento(this.registro);
    }

    /*
     * Si se elimina la selección también limpiamos:
     *
     * - el tipo seleccionado;
     * - los productos relacionados.
     */
    @Override
    public void onRowUnselect() {

        super.onRowUnselect();

        idTipoDescuentoSeleccionado = null;

        limpiarProductos();
    }

    /*
     * Al cancelar limpiamos también:
     *
     * - el tipo seleccionado;
     * - las relaciones DescuentoProducto.
     */
    @Override
    public void btnCancelarHandler(ActionEvent ae) {

        super.btnCancelarHandler(ae);

        idTipoDescuentoSeleccionado = null;

        limpiarProductos();
    }

    /*
     * Limpia el descuento principal utilizado
     * por DescuentoProductoModel.
     */
    private void limpiarProductos() {

        descuentoProductoModel
                .cargarDescuento(null);
    }

    /*
     * Indica si ya se pueden administrar
     * productos asociados al descuento.
     *
     * Un descuento nuevo todavía no puede
     * tener productos hasta ser guardado.
     */
    public boolean isPuedeGestionarProductos() {

        return this.registro != null
                && this.registro.getIdDescuento() != null
                && !this.nuevo;
    }

    public List<TipoDescuento> getTiposDescuento() {
        return tiposDescuento;
    }

    public String getIdTipoDescuentoSeleccionado() {

        /*
         * Mantiene compatibilidad cuando el valor
         * todavía no ha sido sincronizado explícitamente.
         */
        if (idTipoDescuentoSeleccionado == null
                && registro != null
                && registro.getIdTipoDescuento() != null) {

            idTipoDescuentoSeleccionado
                    = registro
                            .getIdTipoDescuento()
                            .toString();
        }

        return idTipoDescuentoSeleccionado;
    }

    public void setIdTipoDescuentoSeleccionado(
            String idTipoDescuentoSeleccionado) {

        this.idTipoDescuentoSeleccionado
                = idTipoDescuentoSeleccionado;

        if (registro != null) {

            if (idTipoDescuentoSeleccionado == null
                    || idTipoDescuentoSeleccionado.isBlank()) {

                registro.setIdTipoDescuento(null);

            } else {

                registro.setIdTipoDescuento(
                        UUID.fromString(
                                idTipoDescuentoSeleccionado
                        )
                );
            }
        }
    }

    /*
     * Cuando se selecciona la fecha inicial,
     * utilizamos esa misma fecha inicialmente
     * como fecha final.
     */
    public void actualizarFechaHasta() {

        if (registro != null
                && registro.getFechaDesde() != null) {

            registro.setFechaHasta(
                    registro.getFechaDesde()
            );
        }
    }
}