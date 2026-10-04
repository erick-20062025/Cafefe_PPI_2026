package boundary;

import control.DAOInterface;
import control.OrdenDAO;
import control.ProductoDAO;
import entity.DescuentoProducto;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

@Named("ordenModel")
@ViewScoped
public class OrdenModel extends AbstractModel<Orden> {

    private static final long serialVersionUID = 1L;

    private static final String ZONA_HORARIA
            = "America/El_Salvador";

    @Inject
    private OrdenDAO ordenDAO;

    @Inject
    private ProductoDAO productoDAO;

    /*
     * =========================================================
     * DATOS GENERALES
     * =========================================================
     */
    private List<EmpleadoRol> empleadosRol;
    private List<Producto> productosDisponibles;
    private List<OrdenProducto> productosOrden;

    private String idEmpleadoRolSeleccionado;
    private String idProductoSeleccionado;

    private BigDecimal precioProducto;
    private String observacionesProducto;

    /*
     * =========================================================
     * DESCUENTOS
     * =========================================================
     */
    private OrdenProducto productoOrdenSeleccionado;

    private List<DescuentoProducto> descuentosAplicables;

    private String idDescuentoProductoSeleccionado;

    /*
     * =========================================================
     * CONSULTA
     * =========================================================
     */
    private Orden ordenSeleccionadaConsulta;

    private List<OrdenProducto> productosConsulta;

    /*
     * =========================================================
     * INICIALIZACIÓN
     * =========================================================
     */
    @PostConstruct
    public void init() {

        cargarDatos();
        cargarEmpleadosRol();
        cargarProductosDisponibles();

        productosOrden = new ArrayList<>();
        productosConsulta = new ArrayList<>();
        descuentosAplicables = new ArrayList<>();
    }

    @Override
    protected DAOInterface<Orden> getDAO() {
        return ordenDAO;
    }

    /*
     * =========================================================
     * NUEVA ORDEN
     * =========================================================
     */
    @Override
    protected Orden crearNuevoRegistro() {

        Orden orden = new Orden();

        orden.setIdOrden(
                UUID.randomUUID()
        );

        /*
         * Hoy aparece inicialmente seleccionado.
         * El usuario puede escoger hoy o una fecha futura.
         */
        orden.setFechaCreacion(
                obtenerFechaActual()
        );

        return orden;
    }

    @Override
    public void btnNuevoHandler(ActionEvent ae) {

        super.btnNuevoHandler(ae);

        cargarEmpleadosRol();
        cargarProductosDisponibles();

        limpiarDatosFormulario();

        productosOrden = new ArrayList<>();
    }

    /*
     * =========================================================
     * SELECCIÓN
     * =========================================================
     */
    @Override
    public void onRowSelect() {

        super.onRowSelect();

        prepararOrdenSeleccionada();
    }

    @Override
    public void onRowUnselect() {

        super.onRowUnselect();

        ordenSeleccionadaConsulta = null;

        limpiarDatosFormulario();

        productosOrden = new ArrayList<>();
        productosConsulta = new ArrayList<>();
    }

    private void prepararOrdenSeleccionada() {

        if (registro == null) {

            idEmpleadoRolSeleccionado = null;
            return;
        }

        ordenSeleccionadaConsulta = registro;

        if (registro.getIdEmpleadoRol() != null
                && registro.getIdEmpleadoRol()
                        .getIdEmpleadoRol() != null) {

            idEmpleadoRolSeleccionado
                    = registro.getIdEmpleadoRol()
                            .getIdEmpleadoRol()
                            .toString();

        } else {

            idEmpleadoRolSeleccionado = null;
        }
    }

    /*
     * =========================================================
     * MODIFICAR
     * =========================================================
     */
    @Override
    public void btnModificarHandler(ActionEvent ae) {

        if (registro == null
                || registro.getIdOrden() == null) {

            mostrarAdvertencia(
                    "Debe seleccionar una orden"
            );

            return;
        }

        super.btnModificarHandler(ae);

        productosOrden
                = ordenDAO.findProductosByOrden(
                        registro.getIdOrden()
                );

        cargarProductosDisponibles();

        limpiarSeleccionProducto();
        limpiarSeleccionDescuento();
    }

    /*
     * =========================================================
     * GUARDAR
     * =========================================================
     */
    @Override
    public void btnGuardarHandler(ActionEvent ae) {

        try {

            if (registro == null) {
                return;
            }

            if (registro.getFechaCreacion() == null) {

                mostrarAdvertencia(
                        "Debe indicar la fecha de la orden"
                );

                return;
            }

            /*
             * La restricción de fecha se valida únicamente
             * durante la creación.
             *
             * Una orden ya guardada conserva su fecha original.
             */
            if (nuevo
                    && esFechaAnteriorAHoy(
                            registro.getFechaCreacion()
                    )) {

                mostrarAdvertencia(
                        "La fecha de la orden no puede ser anterior a hoy"
                );

                return;
            }

            /*
             * CREACIÓN
             */
            if (nuevo) {

                if (registro.getIdEmpleadoRol() == null
                        || registro.getIdEmpleadoRol()
                                .getIdEmpleadoRol() == null) {

                    mostrarAdvertencia(
                            "Debe seleccionar un empleado con rol camarero"
                    );

                    return;
                }

                EmpleadoRol camarero
                        = ordenDAO.findCamareroActivoById(
                                registro.getIdEmpleadoRol()
                                        .getIdEmpleadoRol()
                        );

                if (camarero == null) {

                    mostrarAdvertencia(
                            "El empleado seleccionado no posee "
                            + "un rol camarero activo"
                    );

                    return;
                }

                registro.setIdEmpleadoRol(
                        camarero
                );

                /*
                 * Solo se crea la cabecera.
                 * Los productos se agregan posteriormente
                 * desde Editar.
                 */
                ordenDAO.crear(
                        registro
                );

                mostrarExito(
                        "Orden creada correctamente"
                );

            } else {

                /*
                 * Empleado y fecha están bloqueados
                 * durante edición.
                 */
                ordenDAO.modificar(
                        registro
                );

                mostrarExito(
                        "Orden modificada correctamente"
                );
            }

            limpiarEstado();

            limpiarDatosFormulario();

            productosOrden = new ArrayList<>();

            cargarDatos();

        } catch (Exception e) {

            e.printStackTrace();

            mostrarError(
                    nuevo
                            ? "No se pudo crear la orden"
                            : "No se pudo modificar la orden"
            );
        }
    }

    /*
     * =========================================================
     * CANCELAR
     * =========================================================
     */
    @Override
    public void btnCancelarHandler(ActionEvent ae) {

        super.btnCancelarHandler(ae);

        limpiarDatosFormulario();

        productosOrden = new ArrayList<>();
    }

    /*
     * =========================================================
     * ELIMINAR ORDEN
     * =========================================================
     */
    @Override
    public void btnEliminarHandler(ActionEvent ae) {

        if (registro == null
                || registro.getIdOrden() == null) {

            mostrarAdvertencia(
                    "Debe seleccionar una orden"
            );

            return;
        }

        try {

            long cantidadProductos
                    = ordenDAO.countProductosByOrden(
                            registro.getIdOrden()
                    );

            if (cantidadProductos > 0) {

                mostrarAdvertencia(
                        "No se puede eliminar la orden porque "
                        + "todavía tiene productos asociados"
                );

                return;
            }

            ordenDAO.eliminar(
                    registro
            );

            mostrarExito(
                    "Orden eliminada correctamente"
            );

            limpiarEstado();

            ordenSeleccionadaConsulta = null;

            limpiarDatosFormulario();

            productosOrden = new ArrayList<>();
            productosConsulta = new ArrayList<>();

            cargarDatos();

        } catch (Exception e) {

            e.printStackTrace();

            mostrarError(
                    "No se pudo eliminar la orden"
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
                = ordenDAO.findCamarerosActivos();
    }

    public void setIdEmpleadoRolSeleccionado(
            String idEmpleadoRolSeleccionado) {

        /*
         * Una orden existente no permite cambiar
         * el empleado que la tomó.
         */
        if (editando && !nuevo) {
            return;
        }

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

            UUID id
                    = UUID.fromString(
                            idEmpleadoRolSeleccionado
                    );

            EmpleadoRol empleadoRol
                    = ordenDAO.findCamareroActivoById(
                            id
                    );

            if (empleadoRol == null) {

                registro.setIdEmpleadoRol(null);

                mostrarAdvertencia(
                        "Solo puede seleccionar empleados "
                        + "con rol camarero activo"
                );

                return;
            }

            registro.setIdEmpleadoRol(
                    empleadoRol
            );

        } catch (IllegalArgumentException e) {

            registro.setIdEmpleadoRol(null);

            mostrarAdvertencia(
                    "El empleado seleccionado no es válido"
            );
        }
    }

    /*
     * =========================================================
     * PRODUCTOS DISPONIBLES
     * =========================================================
     */
    private void cargarProductosDisponibles() {

        productosDisponibles
                = productoDAO.findAll();
    }

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

            UUID id
                    = UUID.fromString(
                            idProductoSeleccionado
                    );

            Producto producto
                    = productoDAO.findById(id);

            if (producto != null
                    && Boolean.TRUE.equals(
                            producto.getActivo()
                    )) {

                precioProducto
                        = producto.getPrecioSugerido();
            }

        } catch (IllegalArgumentException e) {

            precioProducto = null;
        }
    }

    /*
     * =========================================================
     * AGREGAR PRODUCTO
     * =========================================================
     */
    public void agregarProducto() {

        if (registro == null
                || registro.getIdOrden() == null
                || nuevo
                || !editando) {

            mostrarAdvertencia(
                    "Debe editar una orden guardada "
                    + "para agregar productos"
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
                    = productoDAO.findById(
                            idProducto
                    );

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

            BigDecimal precio
                    = producto.getPrecioSugerido();

            if (precio == null
                    || precio.compareTo(
                            BigDecimal.ZERO
                    ) < 0) {

                mostrarAdvertencia(
                        "El producto no tiene un precio sugerido válido"
                );

                return;
            }

            OrdenProducto ordenProducto
                    = new OrdenProducto();

            ordenProducto.setIdOrdenProducto(
                    UUID.randomUUID()
            );

            ordenProducto.setIdOrden(
                    registro
            );

            ordenProducto.setIdProducto(
                    producto
            );

            ordenProducto.setPrecio(
                    precio
            );

            ordenProducto.setObservaciones(
                    observacionesProducto
            );

            ordenDAO.agregarProducto(
                    ordenProducto
            );

            recargarProductosOrden();

            limpiarSeleccionProducto();
            limpiarSeleccionDescuento();

            mostrarExito(
                    "Producto agregado a la orden"
            );

        } catch (IllegalArgumentException e) {

            mostrarAdvertencia(
                    "El producto seleccionado no es válido"
            );

        } catch (Exception e) {

            e.printStackTrace();

            mostrarError(
                    "No se pudo agregar el producto"
            );
        }
    }

    /*
     * =========================================================
     * QUITAR PRODUCTO
     * =========================================================
     */
    public void quitarProducto(
            OrdenProducto ordenProducto) {

        if (registro == null
                || !editando
                || nuevo) {

            mostrarAdvertencia(
                    "Debe editar la orden para quitar productos"
            );

            return;
        }

        if (ordenProducto == null) {
            return;
        }

        try {

            ordenDAO.eliminarProducto(
                    ordenProducto
            );

            recargarProductosOrden();

            limpiarSeleccionProducto();
            limpiarSeleccionDescuento();

            mostrarExito(
                    "Producto eliminado de la orden"
            );

        } catch (Exception e) {

            e.printStackTrace();

            mostrarError(
                    "No se pudo eliminar el producto de la orden"
            );
        }
    }

    /*
     * =========================================================
     * SELECCIONAR PRODUCTO PARA DESCUENTO
     * =========================================================
     */
    public void seleccionarProductoParaDescuento(
            OrdenProducto ordenProducto) {

        limpiarSeleccionDescuento();

        if (registro == null
                || registro.getFechaCreacion() == null
                || ordenProducto == null
                || ordenProducto.getIdProducto() == null
                || ordenProducto.getIdProducto()
                        .getIdProducto() == null) {

            return;
        }

        productoOrdenSeleccionado
                = ordenProducto;

        /*
         * Únicamente se recuperan descuentos:
         *
         * - asociados al producto;
         * - válidos para la fecha de la orden.
         */
        descuentosAplicables
                = ordenDAO.findDescuentosAplicables(
                        ordenProducto.getIdProducto()
                                .getIdProducto(),
                        registro.getFechaCreacion()
                );

        if (descuentosAplicables == null) {

            descuentosAplicables
                    = new ArrayList<>();
        }

        if (descuentosAplicables.isEmpty()) {

            mostrarAdvertencia(
                    "Este producto no tiene descuentos "
                    + "aplicables para la fecha de la orden"
            );
        }
    }

    /*
     * =========================================================
     * APLICAR DESCUENTO
     * =========================================================
     */
    public void aplicarDescuento() {

        if (registro == null
                || !editando
                || nuevo) {

            mostrarAdvertencia(
                    "Debe editar una orden para aplicar descuentos"
            );

            return;
        }

        if (productoOrdenSeleccionado == null) {

            mostrarAdvertencia(
                    "Debe seleccionar un producto de la orden"
            );

            return;
        }

        if (idDescuentoProductoSeleccionado == null
                || idDescuentoProductoSeleccionado.isBlank()) {

            mostrarAdvertencia(
                    "Debe seleccionar un descuento"
            );

            return;
        }

        try {

            UUID idDescuentoProducto
                    = UUID.fromString(
                            idDescuentoProductoSeleccionado
                    );

            /*
             * El descuento debe formar parte de la lista
             * previamente validada por producto + fecha.
             */
            DescuentoProducto descuentoProducto
                    = descuentosAplicables.stream()
                            .filter(dp
                                    -> dp.getIdDescuentoProducto() != null
                                    && dp.getIdDescuentoProducto()
                                            .equals(
                                                    idDescuentoProducto
                                            ))
                            .findFirst()
                            .orElse(null);

            if (descuentoProducto == null) {

                mostrarAdvertencia(
                        "El descuento seleccionado no es aplicable"
                );

                return;
            }

            if (descuentoProducto.getValor() == null
                    || descuentoProducto.getValor() <= 0) {

                mostrarAdvertencia(
                        "El descuento no tiene un porcentaje válido"
                );

                return;
            }

            BigDecimal precioActual
                    = productoOrdenSeleccionado
                            .getPrecio();

            if (precioActual == null) {

                mostrarAdvertencia(
                        "El producto no tiene un precio válido"
                );

                return;
            }

            /*
             * Se calcula siempre sobre el PRECIO ACTUAL.
             *
             * Ejemplo:
             *
             * 2.00 - 20 % = 1.60
             * 1.60 - 20 % = 1.28
             */
            BigDecimal porcentaje
                    = BigDecimal.valueOf(
                            descuentoProducto.getValor()
                    );

            BigDecimal montoDescuento
                    = precioActual
                            .multiply(porcentaje)
                            .divide(
                                    BigDecimal.valueOf(100),
                                    2,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal nuevoPrecio
                    = precioActual
                            .subtract(montoDescuento)
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );

            if (nuevoPrecio.compareTo(
                    BigDecimal.ZERO
            ) < 0) {

                nuevoPrecio = BigDecimal.ZERO;
            }

            String nombreDescuento
                    = descuentoProducto
                            .getIdDescuento()
                            .getNombre();

            String nuevaObservacion
                    = "Aplicado descuento "
                    + nombreDescuento
                    + " ("
                    + descuentoProducto.getValor()
                    + "%) sobre $"
                    + precioActual.setScale(
                            2,
                            RoundingMode.HALF_UP
                    ).toPlainString()
                    + " - Monto: $"
                    + montoDescuento.toPlainString()
                    + " - Nuevo precio: $"
                    + nuevoPrecio.toPlainString();

            String observacionesActuales
                    = productoOrdenSeleccionado
                            .getObservaciones();

            if (observacionesActuales == null
                    || observacionesActuales.isBlank()) {

                productoOrdenSeleccionado
                        .setObservaciones(
                                nuevaObservacion
                        );

            } else {

                productoOrdenSeleccionado
                        .setObservaciones(
                                observacionesActuales
                                + "\n"
                                + nuevaObservacion
                        );
            }

            productoOrdenSeleccionado
                    .setPrecio(
                            nuevoPrecio
                    );

            ordenDAO.actualizarProducto(
                    productoOrdenSeleccionado
            );

            UUID idProductoOrden
                    = productoOrdenSeleccionado
                            .getIdOrdenProducto();

            recargarProductosOrden();

            /*
             * Recuperamos nuevamente la fila para que,
             * si aplicamos otro descuento,
             * se use el precio ya rebajado.
             */
            productoOrdenSeleccionado
                    = productosOrden.stream()
                            .filter(op
                                    -> idProductoOrden.equals(
                                            op.getIdOrdenProducto()
                                    ))
                            .findFirst()
                            .orElse(null);

            if (productoOrdenSeleccionado != null) {

                descuentosAplicables
                        = ordenDAO.findDescuentosAplicables(
                                productoOrdenSeleccionado
                                        .getIdProducto()
                                        .getIdProducto(),
                                registro.getFechaCreacion()
                        );
            }

            idDescuentoProductoSeleccionado
                    = null;

            mostrarExito(
                    "Descuento aplicado correctamente. "
                    + "Monto descontado: $"
                    + montoDescuento.toPlainString()
            );

        } catch (IllegalArgumentException e) {

            mostrarAdvertencia(
                    "El descuento seleccionado no es válido"
            );

        } catch (Exception e) {

            e.printStackTrace();

            mostrarError(
                    "No se pudo aplicar el descuento"
            );
        }
    }

    /*
     * =========================================================
     * VER ORDEN
     * =========================================================
     */
    public void verOrden() {

        if (registro != null) {

            ordenSeleccionadaConsulta
                    = registro;
        }

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
     * FECHAS
     * =========================================================
     */
    /**
     * Fecha mínima permitida por el calendario.
     */
    public Date getFechaMinimaOrden() {
        return obtenerFechaActual();
    }

    /**
     * Verifica la misma regla del calendario también desde backend.
     */
    private boolean esFechaAnteriorAHoy(
            Date fecha) {

        if (fecha == null) {
            return false;
        }

        Date fechaOrden
                = normalizarFecha(fecha);

        Date hoy
                = obtenerFechaActual();

        return fechaOrden.before(hoy);
    }

    private Date obtenerFechaActual() {

        Calendar calendario
                = Calendar.getInstance(
                        TimeZone.getTimeZone(
                                ZONA_HORARIA
                        )
                );

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

    private Date normalizarFecha(
            Date fecha) {

        Calendar calendario
                = Calendar.getInstance(
                        TimeZone.getTimeZone(
                                ZONA_HORARIA
                        )
                );

        calendario.setTime(
                fecha
        );

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
     * LIMPIEZA
     * =========================================================
     */
    private void recargarProductosOrden() {

        if (registro == null
                || registro.getIdOrden() == null) {

            productosOrden
                    = new ArrayList<>();

            return;
        }

        productosOrden
                = ordenDAO.findProductosByOrden(
                        registro.getIdOrden()
                );
    }

    private void limpiarSeleccionProducto() {

        idProductoSeleccionado = null;
        precioProducto = null;
        observacionesProducto = null;
    }

    private void limpiarSeleccionDescuento() {

        productoOrdenSeleccionado = null;

        idDescuentoProductoSeleccionado = null;

        descuentosAplicables = new ArrayList<>();
    }

    private void limpiarDatosFormulario() {

        idEmpleadoRolSeleccionado = null;

        limpiarSeleccionProducto();
        limpiarSeleccionDescuento();

        ordenSeleccionadaConsulta = null;

        productosConsulta = new ArrayList<>();
    }

    /*
     * =========================================================
     * TOTALES
     * =========================================================
     */
    public BigDecimal getTotalOrden() {

        return calcularTotal(
                productosOrden
        );
    }

    public BigDecimal getTotalConsulta() {

        return calcularTotal(
                productosConsulta
        );
    }

    private BigDecimal calcularTotal(
            List<OrdenProducto> productos) {

        if (productos == null
                || productos.isEmpty()) {

            return BigDecimal.ZERO;
        }

        return productos.stream()
                .map(
                        OrdenProducto::getPrecio
                )
                .filter(
                        precio -> precio != null
                )
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }

    /*
     * =========================================================
     * PROPIEDADES DE ESTADO
     * =========================================================
     */
    public boolean isPuedeGestionarProductos() {

        return registro != null
                && registro.getIdOrden() != null
                && editando
                && !nuevo;
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

        this.precioProducto
                = precioProducto;
    }

    public String getObservacionesProducto() {
        return observacionesProducto;
    }

    public void setObservacionesProducto(
            String observacionesProducto) {

        this.observacionesProducto
                = observacionesProducto;
    }

    public OrdenProducto getProductoOrdenSeleccionado() {
        return productoOrdenSeleccionado;
    }

    public List<DescuentoProducto> getDescuentosAplicables() {
        return descuentosAplicables;
    }

    public String getIdDescuentoProductoSeleccionado() {
        return idDescuentoProductoSeleccionado;
    }

    public void setIdDescuentoProductoSeleccionado(
            String idDescuentoProductoSeleccionado) {

        this.idDescuentoProductoSeleccionado
                = idDescuentoProductoSeleccionado;
    }

    public Orden getOrdenSeleccionadaConsulta() {
        return ordenSeleccionadaConsulta;
    }

    public void setOrdenSeleccionadaConsulta(
            Orden ordenSeleccionadaConsulta) {

        this.ordenSeleccionadaConsulta
                = ordenSeleccionadaConsulta;

        this.registro
                = ordenSeleccionadaConsulta;

        if (ordenSeleccionadaConsulta != null) {

            this.nuevo = false;
            this.editando = false;

            prepararOrdenSeleccionada();
        }
    }

    public List<OrdenProducto> getProductosConsulta() {
        return productosConsulta;
    }
}
