package boundary;

import control.DAOInterface;
import control.OrdenDAO;
import control.ProductoDAO;
import entity.Descuento;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de órdenes: selección de productos y promoción GENERAL.
 */
@Named("ordenModel")
@ViewScoped
public class OrdenModel extends AbstractModel<Orden> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(OrdenModel.class.getName());
    private static final TimeZone ZONA = TimeZone.getTimeZone("America/El_Salvador");

    @Inject
    private OrdenDAO ordenDAO;
    @Inject
    private ProductoDAO productoDAO;

    private List<EmpleadoRol> empleadosRol = new ArrayList<>();
    private List<Producto> productosDisponibles = new ArrayList<>();
    private List<OrdenProducto> productosOrden = new ArrayList<>();
    private List<Descuento> promocionesDisponibles = new ArrayList<>();
    private List<OrdenProducto> productosConsulta = new ArrayList<>();

    private String idEmpleadoRolSeleccionado;
    private String idProductoSeleccionado;
    private String idPromocionSeleccionada;
    private BigDecimal precioProducto;
    private String observacionesProducto;
    private Orden ordenSeleccionadaConsulta;

    @PostConstruct
    public void init() {
        cargarDatos();
        cargarEmpleadosRol();
        cargarProductosDisponibles();
    }

    @Override
    protected DAOInterface<Orden> getDAO() {
        return ordenDAO;
    }

    @Override
    protected Orden crearNuevoRegistro() {
        Orden orden = new Orden();
        orden.setIdOrden(UUID.randomUUID());
        orden.setFechaCreacion(inicioDiaActual());
        return orden;
    }

    @Override
    public void btnNuevoHandler(ActionEvent ae) {
        super.btnNuevoHandler(ae);
        limpiarFormulario();
        productosOrden = new ArrayList<>();
        cargarEmpleadosRol();
        cargarProductosDisponibles();
    }

    @Override
    public void onRowSelect() {
        super.onRowSelect();
        ordenSeleccionadaConsulta = registro;
        idEmpleadoRolSeleccionado = registro != null && registro.getIdEmpleadoRol() != null
                && registro.getIdEmpleadoRol().getIdEmpleadoRol() != null
                ? registro.getIdEmpleadoRol().getIdEmpleadoRol().toString() : null;
        idPromocionSeleccionada = null;
    }

    @Override
    public void onRowUnselect() {
        super.onRowUnselect();
        limpiarFormulario();
        productosOrden = new ArrayList<>();
    }

    @Override
    public boolean isPuedeEditar() {
        if (registro == null || registro.getIdOrden() == null) {
            return false;
        }

        try {
            return !ordenDAO.tieneProductosFacturados(registro.getIdOrden());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE,
                    "No se pudo verificar si la orden permite edición", e);
            return false;
        }
    }

    @Override
    public boolean isPuedeEliminar() {
        if (registro == null || registro.getIdOrden() == null) {
            return false;
        }

        try {
            return !ordenDAO.tieneProductosFacturados(registro.getIdOrden());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE,
                    "No se pudo verificar si la orden permite eliminación", e);
            return false;
        }
    }

    @Override
    public void btnModificarHandler(ActionEvent ae) {
        if (registro == null || registro.getIdOrden() == null) {
            mostrarAdvertencia("Debe seleccionar una orden");
            return;
        }

        try {
            if (ordenDAO.tieneProductosFacturados(registro.getIdOrden())) {
                mostrarAdvertencia(
                        "Esta orden ya tiene productos facturados. "
                        + "Solo puede consultarla mediante Ver orden");
                return;
            }

            super.btnModificarHandler(ae);
            productosOrden = ordenDAO.findProductosByOrden(registro.getIdOrden());
            cargarPromociones();
            cargarProductosDisponibles();
            limpiarSeleccionProducto();

        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudo cargar la orden para edición", e);
            mostrarError("No se pudo cargar el detalle de la orden");
        }
    }

    /**
     * La cabecera solo se crea. La fecha y el camarero se conservan en edición;
     * los productos y descuentos se guardan en operaciones independientes.
     */
    @Override
    public void btnGuardarHandler(ActionEvent ae) {
        if (registro == null) {
            mostrarAdvertencia("Primero cree o seleccione una orden");
            return;
        }
        if (!nuevo) {
            cerrarEdicionYRefrescar();
            mostrarExito("Edición de la orden finalizada");
            return;
        }
        if (registro.getFechaCreacion() == null) {
            mostrarAdvertencia("Debe indicar la fecha de la orden");
            return;
        }
        if (registro.getIdEmpleadoRol() == null
                || registro.getIdEmpleadoRol().getIdEmpleadoRol() == null) {
            mostrarAdvertencia("Debe seleccionar un camarero activo");
            return;
        }
        try {
            ordenDAO.crear(registro);
        } catch (IllegalArgumentException e) {
            mostrarAdvertencia(e.getMessage());
            return;
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al crear la orden", e);
            mostrarError("No se pudo crear la orden");
            return;
        }
        mostrarExito("Orden creada correctamente. Ahora puede editarla y agregar productos");
        cerrarEdicionYRefrescar();
    }

    @Override
    public void btnCancelarHandler(ActionEvent ae) {
        super.btnCancelarHandler(ae);
        limpiarFormulario();
        productosOrden = new ArrayList<>();
    }

    @Override
    public void btnEliminarHandler(ActionEvent ae) {
        if (registro == null || registro.getIdOrden() == null) {
            mostrarAdvertencia("Debe seleccionar una orden");
            return;
        }
        try {
            ordenDAO.eliminar(registro);
        } catch (IllegalArgumentException e) {
            mostrarAdvertencia(e.getMessage());
            return;
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al eliminar la orden", e);
            mostrarError("No se pudo eliminar la orden");
            return;
        }
        mostrarExito("Orden eliminada correctamente");
        cerrarEdicionYRefrescar();
    }

    private void cerrarEdicionYRefrescar() {
        limpiarEstado();
        limpiarFormulario();
        productosOrden = new ArrayList<>();
        try {
            cargarDatos();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Operación guardada pero falló la recarga de órdenes", e);
            mostrarAdvertencia("Operación realizada, pero no se pudo actualizar la lista");
        }
    }

    private void cargarEmpleadosRol() {
        empleadosRol = ordenDAO.findCamarerosActivos();
    }

    private void cargarProductosDisponibles() {
        productosDisponibles = productoDAO.findAll();
    }

    /**
     * Se ejecuta al seleccionar el camarero, sin permitir cambios al editar.
     */
    public void setIdEmpleadoRolSeleccionado(String valor) {
        if (editando && !nuevo) {
            return;
        }
        idEmpleadoRolSeleccionado = valor;
        if (registro == null) {
            return;
        }
        if (valor == null || valor.isBlank()) {
            registro.setIdEmpleadoRol(null);
            return;
        }
        try {
            EmpleadoRol seleccionado = ordenDAO.findCamareroActivoById(UUID.fromString(valor));
            registro.setIdEmpleadoRol(seleccionado);
            if (seleccionado == null) {
                mostrarAdvertencia("El camarero no está activo");
            }
        } catch (IllegalArgumentException e) {
            registro.setIdEmpleadoRol(null);
            mostrarAdvertencia("Seleccione un camarero válido");
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al verificar camarero", e);
            registro.setIdEmpleadoRol(null);
            mostrarError("No se pudo consultar el camarero");
        }
    }

    /**
     * Al elegir un producto, previsualiza su precio sugerido.
     */
    public void setIdProductoSeleccionado(String valor) {
        idProductoSeleccionado = valor;
        precioProducto = null;
        if (valor == null || valor.isBlank()) {
            return;
        }
        try {
            Producto producto = productoDAO.findById(UUID.fromString(valor));
            if (producto != null && Boolean.TRUE.equals(producto.getActivo())) {
                precioProducto = producto.getPrecioSugerido();
            }
        } catch (IllegalArgumentException e) {
            mostrarAdvertencia("Seleccione un producto válido");
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al consultar producto", e);
            mostrarError("No se pudo consultar el producto");
        }
    }

    /**
     * Agrega una fila; permite repetir productos en una orden.
     */
    public void agregarProducto() {
        if (!isPuedeGestionarProductos()) {
            mostrarAdvertencia("Debe editar una orden guardada");
            return;
        }
        if (idProductoSeleccionado == null || idProductoSeleccionado.isBlank()) {
            mostrarAdvertencia("Debe seleccionar un producto");
            return;
        }
        try {
            Producto producto = productoDAO.findById(UUID.fromString(idProductoSeleccionado));
            if (producto == null || !Boolean.TRUE.equals(producto.getActivo())) {
                mostrarAdvertencia("El producto no existe o está inactivo");
                return;
            }
            OrdenProducto fila = new OrdenProducto(UUID.randomUUID());
            fila.setIdOrden(registro);
            fila.setIdProducto(producto);
            fila.setPrecio(producto.getPrecioSugerido());
            fila.setObservaciones(observacionesProducto);
            ordenDAO.agregarProducto(fila);
        } catch (IllegalArgumentException e) {
            mostrarAdvertencia(e.getMessage());
            return;
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al agregar producto", e);
            mostrarError("No se pudo agregar el producto");
            return;
        }
        mostrarExito("Producto agregado a la orden");
        limpiarSeleccionProducto();
        recargarProductosYPromociones();
    }

    /**
     * Quita una fila si no está facturada.
     */
    public void quitarProducto(OrdenProducto detalle) {
        if (!isPuedeGestionarProductos() || detalle == null) {
            mostrarAdvertencia("Debe seleccionar una fila de una orden en edición");
            return;
        }
        if (detalle.getIdOrden() == null || registro.getIdOrden() == null
                || !registro.getIdOrden().equals(detalle.getIdOrden().getIdOrden())) {
            mostrarAdvertencia("El producto no pertenece a la orden seleccionada");
            return;
        }
        try {
            ordenDAO.eliminarProducto(detalle);
        } catch (IllegalArgumentException e) {
            mostrarAdvertencia(e.getMessage());
            return;
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al eliminar fila de orden", e);
            mostrarError("No se pudo quitar el producto");
            return;
        }
        mostrarExito("Producto eliminado de la orden");
        recargarProductosYPromociones();
    }

    /**
     * Aplica una promoción seleccionada a todos los productos asociados a ella.
     * Las reglas críticas se validan de nuevo dentro de la transacción del DAO.
     */
    public void aplicarPromocion() {
        if (!isPuedeGestionarProductos()) {
            mostrarAdvertencia("Debe editar una orden guardada");
            return;
        }
        if (idPromocionSeleccionada == null || idPromocionSeleccionada.isBlank()) {
            mostrarAdvertencia("Debe seleccionar una promoción");
            return;
        }
        try {
            UUID id = UUID.fromString(idPromocionSeleccionada);
            boolean existe = promocionesDisponibles.stream().anyMatch(p -> p != null
                    && id.equals(p.getIdDescuento()));
            if (!existe) {
                mostrarAdvertencia("La promoción seleccionada no está disponible");
                return;
            }
            OrdenDAO.ResultadoPromocion resultado = ordenDAO.aplicarPromocion(registro.getIdOrden(), id);
            mostrarExito("Promoción aplicada a " + resultado.getProductosAfectados()
                    + " producto(s). Rebaja total: $" + resultado.getDescuentoTotal().toPlainString());
        } catch (IllegalArgumentException e) {
            mostrarAdvertencia(e.getMessage());
            return;
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al aplicar promoción general", e);
            mostrarError("No se pudo aplicar la promoción");
            return;
        }
        // Volvemos a consultar los precios para que otra aplicación parta del precio rebajado.
        idPromocionSeleccionada = null;
        recargarProductosYPromociones();
    }

    private void cargarPromociones() {
        if (registro == null || registro.getIdOrden() == null
                || registro.getFechaCreacion() == null) {
            promocionesDisponibles = new ArrayList<>();
            return;
        }
        promocionesDisponibles = ordenDAO.findPromocionesAplicables(
                registro.getIdOrden(), registro.getFechaCreacion());
        if (promocionesDisponibles == null) {
            promocionesDisponibles = new ArrayList<>();
        }
    }

    /**
     * Reconsulta ambas listas sin anunciar erróneamente que falló el guardado.
     */
    private void recargarProductosYPromociones() {
        try {
            productosOrden = ordenDAO.findProductosByOrden(registro.getIdOrden());
            cargarPromociones();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Operación guardada; error al recargar la orden", e);
            mostrarAdvertencia("Operación realizada, pero no se pudo actualizar la lista");
        }
    }

    public void verOrden() {
        if (registro != null) {
            ordenSeleccionadaConsulta = registro;
        }
        if (ordenSeleccionadaConsulta == null || ordenSeleccionadaConsulta.getIdOrden() == null) {
            mostrarAdvertencia("Debe seleccionar una orden");
            return;
        }
        try {
            productosConsulta = ordenDAO.findProductosByOrden(ordenSeleccionadaConsulta.getIdOrden());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al consultar el detalle de la orden", e);
            mostrarError("No se pudo consultar la orden");
        }
    }

    private static Date inicioDiaActual() {
        Calendar c = Calendar.getInstance(ZONA);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private void limpiarSeleccionProducto() {
        idProductoSeleccionado = null;
        precioProducto = null;
        observacionesProducto = null;
    }

    private void limpiarFormulario() {
        idEmpleadoRolSeleccionado = null;
        idPromocionSeleccionada = null;
        ordenSeleccionadaConsulta = null;
        productosConsulta = new ArrayList<>();
        promocionesDisponibles = new ArrayList<>();
        limpiarSeleccionProducto();
    }

    private static BigDecimal sumar(List<OrdenProducto> productos) {
        BigDecimal total = BigDecimal.ZERO;
        if (productos != null) {
            for (OrdenProducto detalle : productos) {
                if (detalle != null && detalle.getPrecio() != null) {
                    total = total.add(detalle.getPrecio());
                }
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isPuedeGestionarProductos() {
        if (registro == null || registro.getIdOrden() == null
                || !editando || nuevo) {
            return false;
        }

        return !ordenDAO.tieneProductosFacturados(registro.getIdOrden());
    }

    public List<EmpleadoRol> getEmpleadosRol() {
        return empleadosRol;
    }

    public List<Producto> getProductosDisponibles() {
        return productosDisponibles;
    }

    public List<OrdenProducto> getProductosOrden() {
        return productosOrden;
    }

    public List<Descuento> getPromocionesDisponibles() {
        return promocionesDisponibles;
    }

    public List<OrdenProducto> getProductosConsulta() {
        return productosConsulta;
    }

    public String getIdEmpleadoRolSeleccionado() {
        return idEmpleadoRolSeleccionado;
    }

    public String getIdProductoSeleccionado() {
        return idProductoSeleccionado;
    }

    public String getIdPromocionSeleccionada() {
        return idPromocionSeleccionada;
    }

    public void setIdPromocionSeleccionada(String valor) {
        idPromocionSeleccionada = valor;
    }

    public BigDecimal getPrecioProducto() {
        return precioProducto;
    }

    public void setPrecioProducto(BigDecimal valor) {
        precioProducto = valor;
    }

    public String getObservacionesProducto() {
        return observacionesProducto;
    }

    public void setObservacionesProducto(String v) {
        observacionesProducto = v;
    }

    public Orden getOrdenSeleccionadaConsulta() {
        return ordenSeleccionadaConsulta;
    }

    public void setOrdenSeleccionadaConsulta(Orden valor) {
        ordenSeleccionadaConsulta = valor;
        registro = valor;
        if (valor != null) {
            nuevo = false;
            editando = false;
        }
    }

    public BigDecimal getTotalOrden() {
        return sumar(productosOrden);
    }

    public BigDecimal getTotalConsulta() {
        return sumar(productosConsulta);
    }
}
