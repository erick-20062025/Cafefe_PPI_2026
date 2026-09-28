package boundary;

import control.DAOInterface;
import control.DescuentoProductoDAO;
import control.ProductoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.DescuentoProducto;
import entity.Producto;
import entity.TipoDescuento;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Named("descuentoProductoModel")
@ViewScoped
public class DescuentoProductoModel
        extends AbstractRelacionModel<DescuentoProducto, Descuento, Producto> {

    private static final long serialVersionUID = 1L;

    @Inject
    private DescuentoProductoDAO descuentoProductoDAO;

    @Inject
    private ProductoDAO productoDAO;

    @Inject
    private TipoDescuentoDAO tipoDescuentoDAO;

    private Date fechaDesde;
    private Date fechaHasta;
    private Integer valor;
    private String observaciones;

    /*
     * Máximo permitido según el TipoDescuento
     * seleccionado en el descuento principal.
     */
    private Integer descuentoMaximo;

    @PostConstruct
    public void init() {
        cargarOpciones();
    }

    @Override
    protected DAOInterface<DescuentoProducto> getDAO() {
        return descuentoProductoDAO;
    }

    @Override
    protected List<Producto> consultarOpcionesDisponibles() {
        return productoDAO.findAll();
    }

    @Override
    protected List<DescuentoProducto> consultarRelaciones(
            Descuento descuento) {

        return descuentoProductoDAO.findByIdDescuento(
                descuento.getIdDescuento()
        );
    }

    @Override
    protected boolean principalValido(
            Descuento descuento) {

        return descuento != null
                && descuento.getIdDescuento() != null;
    }

    @Override
    protected Producto buscarOpcion(
            String idOpcion) {

        if (idOpcion == null
                || idOpcion.isBlank()) {

            return null;
        }

        try {

            UUID idProducto
                    = UUID.fromString(idOpcion);

            return productoDAO.findById(
                    idProducto
            );

        } catch (IllegalArgumentException e) {

            return null;
        }
    }

    /*
     * En DescuentoProducto permitimos repetir
     * un mismo producto porque cada asociación
     * puede tener distintas fechas y porcentaje.
     */
    @Override
    protected boolean permiteRelacionDuplicada() {
        return true;
    }

    /*
     * No se utiliza cuando permiteRelacionDuplicada()
     * devuelve true.
     */
    @Override
    protected boolean existeRelacion(
            Descuento descuento,
            Producto producto) {

        return false;
    }

    @Override
    protected DescuentoProducto construirRelacion(
            Descuento descuento,
            Producto producto) {

        DescuentoProducto relacion
                = new DescuentoProducto();

        relacion.setIdDescuentoProducto(
                UUID.randomUUID()
        );

        relacion.setIdDescuento(descuento);
        relacion.setIdProducto(producto);
        relacion.setFechaDesde(fechaDesde);
        relacion.setFechaHasta(fechaHasta);
        relacion.setValor(valor);
        relacion.setObservaciones(observaciones);

        return relacion;
    }

    /*
     * Cuando cambia el descuento principal:
     *
     * - carga sus productos asociados;
     * - copia las fechas generales como valores iniciales;
     * - obtiene el descuento máximo permitido
     *   desde TipoDescuento.
     */
    @Override
    public void cargarPrincipal(
            Descuento descuento) {

        super.cargarPrincipal(descuento);

        this.valor = null;
        this.observaciones = null;
        this.descuentoMaximo = null;

        if (descuento != null) {

            this.fechaDesde
                    = descuento.getFechaDesde();

            this.fechaHasta
                    = descuento.getFechaHasta();

            cargarDescuentoMaximo(
                    descuento
            );

        } else {

            this.fechaDesde = null;
            this.fechaHasta = null;
        }
    }

    /*
     * Obtiene el máximo permitido del TipoDescuento
     * asociado al descuento actual.
     */
    private void cargarDescuentoMaximo(
            Descuento descuento) {

        if (descuento.getIdTipoDescuento()
                == null) {

            descuentoMaximo = null;
            return;
        }

        TipoDescuento tipoDescuento
                = tipoDescuentoDAO.findById(
                        descuento.getIdTipoDescuento()
                );

        if (tipoDescuento != null) {

            descuentoMaximo
                    = tipoDescuento.getDescuentoMaximo();

        } else {

            descuentoMaximo = null;
        }
    }

    /* =========================================================
       MÉTODOS PARA LA VISTA
       ========================================================= */

    public void cargarDescuento(
            Descuento descuento) {

        cargarPrincipal(descuento);
    }

    public void cargarProductos() {
        cargarOpciones();
    }

    /*
     * Antes de utilizar agregarRelacion()
     * validamos los datos particulares
     * de DescuentoProducto.
     */
    public void agregarProducto() {

        if (valor == null) {

            mostrarAdvertencia(
                    "Debe indicar el porcentaje de descuento"
            );

            return;
        }

        if (valor <= 0) {

            mostrarAdvertencia(
                    "El descuento debe ser mayor que 0 %"
            );

            return;
        }

        if (descuentoMaximo != null
                && valor > descuentoMaximo) {

            mostrarAdvertencia(
                    "El descuento aplicado no puede superar "
                    + descuentoMaximo + " %"
            );

            return;
        }

        if (fechaDesde == null
                || fechaHasta == null) {

            mostrarAdvertencia(
                    "Debe indicar las fechas del descuento"
            );

            return;
        }

        if (fechaHasta.before(fechaDesde)) {

            mostrarAdvertencia(
                    "La fecha hasta no puede ser anterior a la fecha desde"
            );

            return;
        }

        agregarRelacion();
    }

    public void quitarProducto(
            DescuentoProducto relacion) {

        quitarRelacion(relacion);
    }

    public List<Producto> getProductoList() {
        return getOpcionesDisponibles();
    }

    public String getIdProductoSeleccionado() {
        return getIdOpcionSeleccionada();
    }

    public void setIdProductoSeleccionado(
            String idProductoSeleccionado) {

        setIdOpcionSeleccionada(
                idProductoSeleccionado
        );
    }


    /* =========================================================
       GETTERS Y SETTERS
       ========================================================= */

    public Date getFechaDesde() {
        return fechaDesde;
    }

    public void setFechaDesde(
            Date fechaDesde) {

        this.fechaDesde = fechaDesde;
    }

    public Date getFechaHasta() {
        return fechaHasta;
    }

    public void setFechaHasta(
            Date fechaHasta) {

        this.fechaHasta = fechaHasta;
    }

    public Integer getValor() {
        return valor;
    }

    public void setValor(
            Integer valor) {

        this.valor = valor;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(
            String observaciones) {

        this.observaciones
                = observaciones;
    }

    public Integer getDescuentoMaximo() {
        return descuentoMaximo;
    }
}