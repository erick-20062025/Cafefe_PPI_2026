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
import java.util.Calendar;
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
     * No se permite repetir la misma combinación:
     *
     * Descuento + Producto
     */
    @Override
    protected boolean permiteRelacionDuplicada() {
        return false;
    }

    @Override
    protected boolean existeRelacion(
            Descuento descuento,
            Producto producto) {

        if (descuento == null
                || producto == null
                || producto.getIdProducto() == null
                || modelo == null) {

            return false;
        }

        return modelo.stream()
                .anyMatch(relacion
                        -> relacion != null
                        && relacion.getIdProducto() != null
                        && producto.getIdProducto().equals(
                                relacion.getIdProducto()
                                        .getIdProducto()
                        )
                );
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
     * =========================================================
     * CARGAR DESCUENTO
     * =========================================================
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

    private void cargarDescuentoMaximo(
            Descuento descuento) {

        if (descuento == null
                || descuento.getIdTipoDescuento() == null) {

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

    /*
     * =========================================================
     * MÉTODOS PARA LA VISTA
     * =========================================================
     */

    public void cargarDescuento(
            Descuento descuento) {

        cargarPrincipal(descuento);
    }

    public void cargarProductos() {
        cargarOpciones();
    }

    /*
     * Muestra claramente cuáles productos
     * están inactivos.
     */
    public String obtenerEtiquetaProducto(
            Producto producto) {

        if (producto == null) {
            return "";
        }

        String nombre = producto.getNombre();

        if (!Boolean.TRUE.equals(
                producto.getActivo())) {

            return nombre + " (INACTIVO)";
        }

        return nombre;
    }

    /*
     * =========================================================
     * AGREGAR PRODUCTO
     * =========================================================
     */
    public void agregarProducto() {

        if (!principalValido(principal)) {

            mostrarAdvertencia(
                    "Debe seleccionar un descuento"
            );

            return;
        }

        if (idOpcionSeleccionada == null
                || idOpcionSeleccionada.isBlank()) {

            mostrarAdvertencia(
                    "Debe seleccionar un producto"
            );

            return;
        }

        Producto producto
                = buscarOpcion(
                        idOpcionSeleccionada
                );

        if (producto == null) {

            mostrarAdvertencia(
                    "El producto seleccionado no existe"
            );

            return;
        }

        /*
         * Un producto inactivo no puede recibir
         * nuevas asociaciones de descuento.
         */
        if (!Boolean.TRUE.equals(
                producto.getActivo())) {

            mostrarAdvertencia(
                    "El producto seleccionado está inactivo"
            );

            return;
        }

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

        if (descuentoMaximo == null) {

            mostrarAdvertencia(
                    "No se pudo determinar el límite del tipo de descuento"
            );

            return;
        }

        if (valor > descuentoMaximo) {

            mostrarAdvertencia(
                    "El descuento aplicado no puede superar "
                    + descuentoMaximo + " %"
            );

            return;
        }

        if (fechaDesde == null) {

            mostrarAdvertencia(
                    "Debe indicar la fecha desde"
            );

            return;
        }

        /*
         * No puede iniciar antes del descuento principal.
         */
        if (principal.getFechaDesde() != null
                && normalizarFecha(fechaDesde)
                        .before(
                                normalizarFecha(
                                        principal.getFechaDesde()
                                )
                        )) {

            mostrarAdvertencia(
                    "La fecha desde no puede ser anterior "
                    + "a la fecha inicial del descuento"
            );

            return;
        }

        /*
         * Si hay fecha final, debe ser igual
         * o posterior a fechaDesde.
         */
        if (fechaHasta != null
                && normalizarFecha(fechaHasta)
                        .before(
                                normalizarFecha(fechaDesde)
                        )) {

            mostrarAdvertencia(
                    "La fecha hasta no puede ser anterior a la fecha desde"
            );

            return;
        }

        /*
         * DESCUENTO CERRADO
         */
        if (principal.getFechaHasta() != null) {

            if (fechaHasta == null) {

                mostrarAdvertencia(
                        "Este descuento tiene fecha de finalización; "
                        + "debe indicar una fecha hasta"
                );

                return;
            }

            if (normalizarFecha(fechaHasta)
                    .after(
                            normalizarFecha(
                                    principal.getFechaHasta()
                            )
                    )) {

                mostrarAdvertencia(
                        "La fecha hasta no puede superar "
                        + "la fecha final del descuento"
                );

                return;
            }

            if (normalizarFecha(fechaDesde)
                    .after(
                            normalizarFecha(
                                    principal.getFechaHasta()
                            )
                    )) {

                mostrarAdvertencia(
                        "La fecha desde está fuera "
                        + "del rango del descuento"
                );

                return;
            }
        }

        /*
         * Si el descuento principal es abierto,
         * fechaHasta puede quedar null.
         */

        agregarRelacion();
    }

    /*
     * Elimina solamente la asociación.
     */
    public void quitarProducto(
            DescuentoProducto relacion) {

        quitarRelacion(relacion);
    }

    /*
     * =========================================================
     * FECHAS PARA LA VISTA
     * =========================================================
     */

    public Date getFechaMinimaPermitida() {

        if (principal == null) {
            return null;
        }

        return principal.getFechaDesde();
    }

    public Date getFechaMaximaPermitida() {

        if (principal == null) {
            return null;
        }

        return principal.getFechaHasta();
    }

    public boolean isRangoAbierto() {

        return principal != null
                && principal.getFechaHasta() == null;
    }

    private Date normalizarFecha(
            Date fecha) {

        Calendar calendario
                = Calendar.getInstance();

        calendario.setTime(fecha);

        calendario.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        calendario.set(
                Calendar.MINUTE,
                0
        );

        calendario.set(
                Calendar.SECOND,
                0
        );

        calendario.set(
                Calendar.MILLISECOND,
                0
        );

        return calendario.getTime();
    }

    /*
     * =========================================================
     * GETTERS Y SETTERS
     * =========================================================
     */

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