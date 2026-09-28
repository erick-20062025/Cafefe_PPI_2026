package boundary;

import control.DAOInterface;
import control.FacturaDAO;
import control.FacturaOrdenProductoDAO;
import entity.Caja;
import entity.EmpleadoRol;
import entity.Factura;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import control.OrdenDAO;
import entity.FacturaOrdenProducto;
import entity.Orden;
import entity.OrdenProducto;
import jakarta.faces.event.ActionEvent;
import java.math.BigDecimal;
import java.util.ArrayList;

@Named("facturaModel")
@ViewScoped
public class FacturaModel extends AbstractModel<Factura> {

    @Inject
    private FacturaOrdenProductoDAO facturaOrdenProductoDAO;
    @Inject

    private FacturaDAO facturaDAO;

    @Inject
    private OrdenDAO ordenDAO;
    private List<Orden> ordenesDisponibles;
    private List<OrdenProducto> productosOrden;
    private List<FacturaOrdenProducto> productosFactura;

    private String idOrdenSeleccionada;
    private String idOrdenProductoSeleccionado;

    private BigDecimal precioProducto;
    private String observacionesProducto;

    private List<Caja> cajas;
    private List<EmpleadoRol> empleadosRol;

    private String idCajaSeleccionada;
    private String idEmpleadoRolSeleccionado;

    @PostConstruct
    public void init() {

        cargarDatos();
        cargarCajas();
        cargarEmpleadosRol();
        cargarOrdenesDisponibles();

        productosOrden = new ArrayList<>();
        productosFactura = new ArrayList<>();
    }

    public void verFactura() {

        if (registro == null) {
            mostrarAdvertencia("Debe seleccionar una factura");
            return;
        }

        this.consultando = true;
        this.editando = false;
        this.nuevo = false;
    }

    @Override
    public void onRowSelect() {

        super.onRowSelect();

        if (registro == null) {
            return;
        }

        // Recuperar caja seleccionada
        if (registro.getIdCaja() != null
                && registro.getIdCaja().getIdCaja() != null) {

            idCajaSeleccionada
                    = registro.getIdCaja()
                            .getIdCaja()
                            .toString();

        } else {
            idCajaSeleccionada = null;
        }

        // Recuperar empleado/rol seleccionado
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

        // Recuperar productos guardados de la factura
        productosFactura
                = facturaOrdenProductoDAO
                        .findByIdFactura(
                                registro.getIdFactura()
                        );

        // Limpiar los selectores usados para agregar productos
        idOrdenSeleccionada = null;
        idOrdenProductoSeleccionado = null;
        productosOrden = new ArrayList<>();
        precioProducto = null;
        observacionesProducto = null;
    }

    @Override
    public void onRowUnselect() {

        super.onRowUnselect();

        idCajaSeleccionada = null;
        idEmpleadoRolSeleccionado = null;

        idOrdenSeleccionada = null;
        idOrdenProductoSeleccionado = null;

        productosOrden = new ArrayList<>();
        productosFactura = new ArrayList<>();

        precioProducto = null;
        observacionesProducto = null;
    }

    @Override
    protected DAOInterface<Factura> getDAO() {
        return facturaDAO;
    }

    @Override
    protected Factura crearNuevoRegistro() {

        Factura factura = new Factura();

        factura.setIdFactura(UUID.randomUUID());
        factura.setFechaFacturacion(new Date());
        factura.setEstado("PENDIENTE");

        return factura;
    }

    @Override
    public void btnGuardarHandler(ActionEvent ae) {

        try {

            if (registro == null) {
                return;
            }

            if (registro.getIdCaja() == null) {
                mostrarAdvertencia(
                        "Debe seleccionar una caja"
                );
                return;
            }

            if (registro.getIdEmpleadoRol() == null) {
                mostrarAdvertencia(
                        "Debe seleccionar el empleado y su rol"
                );
                return;
            }

            if (nuevo) {

                if (productosFactura == null
                        || productosFactura.isEmpty()) {

                    mostrarAdvertencia(
                            "Debe agregar al menos un producto a la factura"
                    );
                    return;
                }

                facturaDAO.crearFacturaCompleta(
                        registro,
                        productosFactura
                );

                mostrarExito(
                        "Factura guardada correctamente"
                );

            } else {

                facturaDAO.modificar(registro);

                mostrarExito(
                        "Factura modificada correctamente"
                );
            }

            limpiarEstado();

            productosFactura = new ArrayList<>();
            productosOrden = new ArrayList<>();

            idCajaSeleccionada = null;
            idEmpleadoRolSeleccionado = null;
            idOrdenSeleccionada = null;
            idOrdenProductoSeleccionado = null;

            precioProducto = null;
            observacionesProducto = null;

            cargarDatos();

        } catch (Exception e) {

            e.printStackTrace();

            mostrarError(
                    "No se pudo guardar la factura"
            );
        }
    }

    @Override
    public void btnCancelarHandler(ActionEvent ae) {

        super.btnCancelarHandler(ae);

        this.consultando = false;

        productosFactura = new ArrayList<>();
        productosOrden = new ArrayList<>();

        idCajaSeleccionada = null;
        idEmpleadoRolSeleccionado = null;
        idOrdenSeleccionada = null;
        idOrdenProductoSeleccionado = null;
        precioProducto = null;
        observacionesProducto = null;
    }
    @Override
public void btnEliminarHandler(ActionEvent ae) {

    try {

        if (registro == null) {
            return;
        }

        if (isFacturaPagada()) {

            mostrarAdvertencia(
                    "No se puede eliminar una factura pagada"
            );

            return;
        }

        facturaDAO.eliminarFacturaCompleta(registro);

        limpiarEstado();

        productosFactura.clear();
        productosOrden.clear();

        cargarDatos();

        mostrarExito(
                "Factura eliminada correctamente"
        );

    } catch (Exception e) {

        e.printStackTrace();

        mostrarError(
                "No se pudo eliminar la factura"
        );
    }
}

    @Override
    public boolean isPuedeEditar() {
        return registro != null && !isFacturaPagada();
    }

    @Override
    public boolean isPuedeEliminar() {
        return registro != null && !isFacturaPagada();
    }

    private void cargarCajas() {
        cajas = facturaDAO.findCajasActivas();
    }

    public boolean isFacturaPendiente() {
        return registro != null
                && "PENDIENTE".equalsIgnoreCase(registro.getEstado());
    }

    public boolean isFacturaPagada() {
        return registro != null
                && "PAGADA".equalsIgnoreCase(registro.getEstado());
    }

    private void cargarOrdenesDisponibles() {
        ordenesDisponibles = ordenDAO.findAll();
    }

    public void confirmarPagada() {

        if (registro == null || registro.getIdFactura() == null) {
            mostrarAdvertencia("Debe seleccionar una factura");
            return;
        }

        if (isFacturaPagada()) {
            mostrarAdvertencia("La factura ya se encuentra pagada");
            return;
        }

        List<FacturaOrdenProducto> detalles
                = facturaOrdenProductoDAO.findByIdFactura(
                        registro.getIdFactura()
                );

        if (detalles == null || detalles.isEmpty()) {
            mostrarAdvertencia(
                    "No se puede confirmar una factura sin productos"
            );
            return;
        }

        registro.setEstado("PAGADA");
        facturaDAO.modificar(registro);

        cargarDatos();

        mostrarExito("Factura confirmada como pagada");
    }

    public void setIdOrdenSeleccionada(String idOrdenSeleccionada) {

        this.idOrdenSeleccionada = idOrdenSeleccionada;

        productosOrden = new ArrayList<>();
        idOrdenProductoSeleccionado = null;
        precioProducto = null;
        observacionesProducto = null;

        if (idOrdenSeleccionada == null
                || idOrdenSeleccionada.isBlank()) {
            return;
        }

        try {

            UUID idOrden = UUID.fromString(idOrdenSeleccionada);

            productosOrden
                    = ordenDAO.findProductosByOrden(idOrden);

        } catch (IllegalArgumentException e) {

            productosOrden = new ArrayList<>();
        }
    }

    public void setIdOrdenProductoSeleccionado(
            String idOrdenProductoSeleccionado) {

        this.idOrdenProductoSeleccionado
                = idOrdenProductoSeleccionado;

        precioProducto = null;
        observacionesProducto = null;

        if (idOrdenProductoSeleccionado == null
                || idOrdenProductoSeleccionado.isBlank()) {
            return;
        }

        try {

            UUID id = UUID.fromString(
                    idOrdenProductoSeleccionado);

            OrdenProducto ordenProducto
                    = productosOrden.stream()
                            .filter(op -> id.equals(
                                    op.getIdOrdenProducto()))
                            .findFirst()
                            .orElse(null);

            if (ordenProducto != null) {

                precioProducto = ordenProducto.getPrecio();
                observacionesProducto
                        = ordenProducto.getObservaciones();
            }

        } catch (IllegalArgumentException e) {

            precioProducto = null;
            observacionesProducto = null;
        }
    }

    public void agregarProducto() {

        if (registro == null) {

            mostrarAdvertencia(
                    "Debe seleccionar o crear una factura"
            );

            return;
        }
        if (!isFacturaPendiente()) {

            mostrarAdvertencia(
                    "No se pueden agregar productos a una factura pagada"
            );

            return;
        }

        if (idOrdenProductoSeleccionado == null
                || idOrdenProductoSeleccionado.isBlank()) {

            mostrarAdvertencia(
                    "Debe seleccionar un producto de la orden"
            );

            return;
        }

        try {

            UUID idOrdenProducto = UUID.fromString(
                    idOrdenProductoSeleccionado
            );

            OrdenProducto ordenProducto
                    = productosOrden.stream()
                            .filter(op -> idOrdenProducto.equals(
                                    op.getIdOrdenProducto()))
                            .findFirst()
                            .orElse(null);

            if (ordenProducto == null) {

                mostrarAdvertencia(
                        "El producto seleccionado no existe"
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

            boolean yaAgregado = productosFactura.stream()
                    .anyMatch(detalle
                            -> detalle.getIdOrdenProducto() != null
                            && idOrdenProducto.equals(
                                    detalle.getIdOrdenProducto()
                                            .getIdOrdenProducto()));

            if (yaAgregado) {

                mostrarAdvertencia(
                        "Este producto ya fue agregado a la factura"
                );

                return;
            }

            FacturaOrdenProducto detalle
                    = new FacturaOrdenProducto();

            detalle.setIdFacturaOrdenProducto(
                    UUID.randomUUID()
            );

            detalle.setIdFactura(registro);
            detalle.setIdOrdenProducto(ordenProducto);
            detalle.setPrecio(precioProducto);
            detalle.setObservaciones(observacionesProducto);

            if (nuevo) {

                // La factura todavía no existe en BD.
                // El detalle se guardará junto con la factura.
                productosFactura.add(detalle);

            } else {

                // La factura ya existe.
                // Guardamos inmediatamente el nuevo detalle.
                facturaOrdenProductoDAO.agregarProductoAFactura(detalle);

                // Volvemos a consultar lo que realmente existe en BD.
                productosFactura
                        = facturaOrdenProductoDAO.findByIdFactura(
                                registro.getIdFactura()
                        );

                mostrarExito(
                        "Producto agregado a la factura"
                );
            }

            idOrdenProductoSeleccionado = null;
            precioProducto = null;
            observacionesProducto = null;

        } catch (IllegalArgumentException e) {

            mostrarAdvertencia(
                    "El producto seleccionado no es válido"
            );
        }
    }

    public void quitarProducto(
            FacturaOrdenProducto detalle) {

        if (detalle == null
                || productosFactura == null) {

            return;
        }

        productosFactura.remove(detalle);
    }

    private void cargarEmpleadosRol() {
        empleadosRol = facturaDAO.findEmpleadosRolActivos();
    }

    public void setIdCajaSeleccionada(String idCajaSeleccionada) {

        this.idCajaSeleccionada = idCajaSeleccionada;

        if (registro == null) {
            return;
        }

        if (idCajaSeleccionada == null
                || idCajaSeleccionada.isBlank()) {

            registro.setIdCaja(null);
            return;
        }

        try {

            UUID id = UUID.fromString(idCajaSeleccionada);

            Caja caja = facturaDAO.findCajaById(id);

            registro.setIdCaja(caja);

        } catch (IllegalArgumentException e) {

            registro.setIdCaja(null);
        }
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
                    idEmpleadoRolSeleccionado);

            EmpleadoRol empleadoRol
                    = facturaDAO.findEmpleadoRolById(id);

            registro.setIdEmpleadoRol(empleadoRol);

        } catch (IllegalArgumentException e) {

            registro.setIdEmpleadoRol(null);
        }
    }

    public List<Caja> getCajas() {
        return cajas;
    }

    public List<EmpleadoRol> getEmpleadosRol() {
        return empleadosRol;
    }

    public String getIdCajaSeleccionada() {
        return idCajaSeleccionada;
    }

    public String getIdEmpleadoRolSeleccionado() {
        return idEmpleadoRolSeleccionado;
    }

    public List<Orden> getOrdenesDisponibles() {
        return ordenesDisponibles;
    }

    public List<OrdenProducto> getProductosOrden() {
        return productosOrden;
    }

    public List<FacturaOrdenProducto> getProductosFactura() {
        return productosFactura;
    }

    public String getIdOrdenSeleccionada() {
        return idOrdenSeleccionada;
    }

    public String getIdOrdenProductoSeleccionado() {
        return idOrdenProductoSeleccionado;
    }

    public BigDecimal getPrecioProducto() {
        return precioProducto;
    }

    public void setPrecioProducto(BigDecimal precioProducto) {
        this.precioProducto = precioProducto;
    }

    public String getObservacionesProducto() {
        return observacionesProducto;
    }

    public void setObservacionesProducto(String observacionesProducto) {
        this.observacionesProducto = observacionesProducto;
    }
}
