package boundary;

import control.DAOInterface;
import control.OrdenDAO;
import control.ProductoDAO;
import entity.EmpleadoRol;
import entity.Orden;
import entity.OrdenProducto;
import entity.Producto;
import jakarta.annotation.PostConstruct;
import jakarta.faces.event.ActionEvent;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Named("ordenModel")
@ViewScoped
public class OrdenModel extends AbstractModel<Orden> {

    @Inject
    private OrdenDAO ordenDAO;

    @Inject
    private ProductoDAO productoDAO;

    /*
     * Datos necesarios para crear una orden.
     */
    private List<EmpleadoRol> empleadosRol;
    private List<Producto> productosDisponibles;

    /*
     * Productos agregados temporalmente a la nueva orden.
     * No se guardan en PostgreSQL hasta guardar la orden completa.
     */
    private List<OrdenProducto> productosOrden;

    /*
     * Selecciones del formulario.
     */
    private String idEmpleadoRolSeleccionado;
    private String idProductoSeleccionado;
    private BigDecimal precioProducto;
    private String observacionesProducto;

    /*
     * Se utiliza únicamente para consultar una orden ya guardada.
     * No utiliza "registro", para evitar que la plantilla muestre
     * Editar y Eliminar.
     */
    private Orden ordenSeleccionadaConsulta;
    private List<OrdenProducto> productosConsulta;

    @PostConstruct
    public void init() {

        cargarDatos();
        cargarEmpleadosRol();
        cargarProductosDisponibles();

        productosOrden = new ArrayList<>();
        productosConsulta = new ArrayList<>();
    }

    @Override
    protected DAOInterface<Orden> getDAO() {
        return ordenDAO;
    }

    @Override
    protected Orden crearNuevoRegistro() {

        Orden orden = new Orden();

        orden.setIdOrden(UUID.randomUUID());
        orden.setFechaCreacion(new Date());

        return orden;
    }

    /*
     * =========================================================
     * NUEVA ORDEN
     * =========================================================
     */
    @Override
    public void btnNuevoHandler(ActionEvent ae) {

        super.btnNuevoHandler(ae);

        productosOrden = new ArrayList<>();

        idEmpleadoRolSeleccionado = null;
        idProductoSeleccionado = null;
        precioProducto = null;
        observacionesProducto = null;

        ordenSeleccionadaConsulta = null;
        productosConsulta = new ArrayList<>();
    }

    @Override
    public void btnCancelarHandler(ActionEvent ae) {

        super.btnCancelarHandler(ae);

        productosOrden = new ArrayList<>();

        idEmpleadoRolSeleccionado = null;
        idProductoSeleccionado = null;
        precioProducto = null;
        observacionesProducto = null;
    }

    /*
     * =========================================================
     * GUARDAR ORDEN COMPLETA
     * =========================================================
     */
    @Override
    public void btnGuardarHandler(ActionEvent ae) {

        try {

            if (registro == null) {
                return;
            }

            if (registro.getIdEmpleadoRol() == null) {

                mostrarAdvertencia(
                        "Debe seleccionar el empleado y su rol"
                );

                return;
            }

            if (productosOrden == null
                    || productosOrden.isEmpty()) {

                mostrarAdvertencia(
                        "Debe agregar al menos un producto a la orden"
                );

                return;
            }

            ordenDAO.crearOrdenCompleta(
                    registro,
                    productosOrden
            );

            limpiarEstado();

            productosOrden = new ArrayList<>();

            idEmpleadoRolSeleccionado = null;
            idProductoSeleccionado = null;
            precioProducto = null;
            observacionesProducto = null;

            cargarDatos();

            mostrarExito(
                    "Orden guardada correctamente"
            );

        } catch (Exception e) {

            e.printStackTrace();

            mostrarError(
                    "No se pudo guardar la orden"
            );
        }
    }

    /*
     * =========================================================
     * EMPLEADO / ROL
     * =========================================================
     */
    private void cargarEmpleadosRol() {

        empleadosRol
                = ordenDAO.findEmpleadosRolActivos();
    }

    public void setIdEmpleadoRolSeleccionado(
            String idEmpleadoRolSeleccionado) {

        this.idEmpleadoRolSeleccionado
                = idEmpleadoRolSeleccionado;

        if (registro == null) {
            return;
        }

        if (idEmpleadoRolSeleccionado == null
                || idEmpleadoRolSeleccionado.isBlank()) {

            registro.setIdEmpleadoRol(null);
            return;
        }

        try {

            UUID id = UUID.fromString(
                    idEmpleadoRolSeleccionado
            );

            EmpleadoRol empleadoRol
                    = ordenDAO.findEmpleadoRolById(id);

            registro.setIdEmpleadoRol(empleadoRol);

        } catch (IllegalArgumentException e) {

            registro.setIdEmpleadoRol(null);
        }
    }

    /*
     * =========================================================
     * PRODUCTOS DISPONIBLES
     * =========================================================
     */
    private void cargarProductosDisponibles() {

        productosDisponibles
                = productoDAO.findAll()
                        .stream()
                        .filter(producto ->
                                Boolean.TRUE.equals(
                                        producto.getActivo()
                                ))
                        .toList();
    }

    /*
     * Al seleccionar un producto se toma inicialmente
     * su precio sugerido.
     */
    public void setIdProductoSeleccionado(
            String idProductoSeleccionado) {

        this.idProductoSeleccionado
                = idProductoSeleccionado;

        precioProducto = null;

        if (idProductoSeleccionado == null
                || idProductoSeleccionado.isBlank()) {

            return;
        }

        try {

            UUID id = UUID.fromString(
                    idProductoSeleccionado
            );

            Producto producto
                    = productoDAO.findById(id);

            if (producto != null) {

                precioProducto
                        = producto.getPrecioSugerido();
            }

        } catch (IllegalArgumentException e) {

            precioProducto = null;
        }
    }

    /*
     * =========================================================
     * AGREGAR PRODUCTO A LA ORDEN TEMPORAL
     * =========================================================
     */
    public void agregarProducto() {

        if (registro == null || !nuevo) {

            mostrarAdvertencia(
                    "Primero debe crear una nueva orden"
            );

            return;
        }

        if (idProductoSeleccionado == null
                || idProductoSeleccionado.isBlank()) {

            mostrarAdvertencia(
                    "Debe seleccionar un producto"
            );

            return;
        }

        try {

            UUID idProducto
                    = UUID.fromString(
                            idProductoSeleccionado
                    );

            Producto producto
                    = productoDAO.findById(idProducto);

            if (producto == null) {

                mostrarAdvertencia(
                        "El producto seleccionado no existe"
                );

                return;
            }

            if (!Boolean.TRUE.equals(
                    producto.getActivo())) {

                mostrarAdvertencia(
                        "El producto seleccionado está inactivo"
                );

                return;
            }

            if (precioProducto == null
                    || precioProducto.compareTo(
                            BigDecimal.ZERO) < 0) {

                mostrarAdvertencia(
                        "El producto no tiene un precio válido"
                );

                return;
            }

            OrdenProducto ordenProducto
                    = new OrdenProducto();

            ordenProducto.setIdOrdenProducto(
                    UUID.randomUUID()
            );

            ordenProducto.setIdOrden(registro);
            ordenProducto.setIdProducto(producto);
            ordenProducto.setPrecio(precioProducto);
            ordenProducto.setObservaciones(
                    observacionesProducto
            );

            productosOrden.add(ordenProducto);

            /*
             * Se limpia únicamente el formulario
             * para agregar el siguiente producto.
             */
            idProductoSeleccionado = null;
            precioProducto = null;
            observacionesProducto = null;

        } catch (IllegalArgumentException e) {

            mostrarAdvertencia(
                    "El producto seleccionado no es válido"
            );
        }
    }

    /*
     * =========================================================
     * QUITAR PRODUCTO DEL BORRADOR
     * =========================================================
     */
    public void quitarProducto(
            OrdenProducto ordenProducto) {

        if (ordenProducto == null
                || productosOrden == null) {

            return;
        }

        productosOrden.remove(ordenProducto);
    }

    /*
     * =========================================================
     * CONSULTAR ORDEN GUARDADA
     * =========================================================
     */
    public void verOrden() {

        if (ordenSeleccionadaConsulta == null
                || ordenSeleccionadaConsulta
                        .getIdOrden() == null) {

            mostrarAdvertencia(
                    "Debe seleccionar una orden"
            );

            return;
        }

        productosConsulta
                = ordenDAO.findProductosByOrden(
                        ordenSeleccionadaConsulta
                                .getIdOrden()
                );
    }

    /*
     * =========================================================
     * TOTALES
     * =========================================================
     */
    public BigDecimal getTotalOrden() {

        return calcularTotal(productosOrden);
    }

    public BigDecimal getTotalConsulta() {

        return calcularTotal(productosConsulta);
    }

    private BigDecimal calcularTotal(
            List<OrdenProducto> productos) {

        if (productos == null
                || productos.isEmpty()) {

            return BigDecimal.ZERO;
        }

        return productos.stream()
                .map(OrdenProducto::getPrecio)
                .filter(precio -> precio != null)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }

    /*
     * =========================================================
     * GETTERS Y SETTERS
     * =========================================================
     */
    public List<EmpleadoRol> getEmpleadosRol() {
        return empleadosRol;
    }

    public List<Producto> getProductosDisponibles() {
        return productosDisponibles;
    }

    public List<OrdenProducto> getProductosOrden() {
        return productosOrden;
    }

    public String getIdEmpleadoRolSeleccionado() {
        return idEmpleadoRolSeleccionado;
    }

    public String getIdProductoSeleccionado() {
        return idProductoSeleccionado;
    }

    public BigDecimal getPrecioProducto() {
        return precioProducto;
    }

    public void setPrecioProducto(
            BigDecimal precioProducto) {

        this.precioProducto = precioProducto;
    }

    public String getObservacionesProducto() {
        return observacionesProducto;
    }

    public void setObservacionesProducto(
            String observacionesProducto) {

        this.observacionesProducto
                = observacionesProducto;
    }

    public Orden getOrdenSeleccionadaConsulta() {
        return ordenSeleccionadaConsulta;
    }

    public void setOrdenSeleccionadaConsulta(
            Orden ordenSeleccionadaConsulta) {

        this.ordenSeleccionadaConsulta
                = ordenSeleccionadaConsulta;
    }

    public List<OrdenProducto> getProductosConsulta() {
        return productosConsulta;
    }
}
