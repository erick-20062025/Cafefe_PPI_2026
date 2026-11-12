package boundary;

import control.PagoDAO;
import control.PagoDetalleDAO;
import entity.Factura;
import entity.FacturaOrdenProducto;
import entity.PagoDetalle;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pantalla especializada del cajero. No hereda AbstractModel porque el cobro
 * no es un CRUD genérico: cada abono pasa por PagoDAO.registrarAbono().
 */
@Named("pagoModel")
@ViewScoped
public class PagoModel implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(PagoModel.class.getName());

    @Inject
    private PagoDAO pagoDAO;

    @Inject
    private PagoDetalleDAO pagoDetalleDAO;

    private List<Factura> facturasPendientes = new ArrayList<>();
    private Factura facturaSeleccionada;
    private List<FacturaOrdenProducto> productosFactura = new ArrayList<>();
    private List<PagoDetalle> abonos = new ArrayList<>();
    private PagoDAO.ResumenPago resumen;

    // 0: facturas pendientes; 1: cobro de factura seleccionada.
    private int indicePestana;

    private String tipoPago;
    private BigDecimal monto;
    private String referenciaExterna;
    private String observaciones;

    @PostConstruct
    public void init() {
        actualizarFacturas();
    }

    /** Recarga las facturas pendientes sin seleccionar ninguna automáticamente. */
    public void actualizarFacturas() {
        try {
            List<Factura> resultados = pagoDAO.listarFacturasPendientes();
            facturasPendientes = resultados != null ? resultados : new ArrayList<>();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudieron consultar las facturas pendientes", e);
            mostrarError("No se pudieron cargar las facturas pendientes");
        }
    }

    /** Muestra una factura ya emitida, sus productos y sus abonos registrados. */
    public void seleccionarFactura(Factura factura) {
        if (factura == null || factura.getIdFactura() == null) {
            mostrarAdvertencia("Debe seleccionar una factura válida");
            return;
        }
        try {
            UUID idFactura = factura.getIdFactura();
            PagoDAO.ResumenPago nuevoResumen = pagoDAO.consultarResumen(idFactura);
            List<FacturaOrdenProducto> nuevosProductos = pagoDAO.obtenerDetallesFactura(idFactura);
            List<PagoDetalle> nuevosAbonos = pagoDetalleDAO.findByFactura(idFactura);
            facturaSeleccionada = factura;
            resumen = nuevoResumen;
            productosFactura = nuevosProductos != null ? nuevosProductos : new ArrayList<>();
            abonos = nuevosAbonos != null ? nuevosAbonos : new ArrayList<>();
            limpiarCamposPago();
            indicePestana = 1;
        } catch (IllegalArgumentException | IllegalStateException e) {
            mostrarAdvertencia(e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Fallo al seleccionar una factura", e);
            mostrarError("No se pudo consultar el detalle de la factura");
        }
    }

    /** Confirma un abono. Nunca cambia estado directamente desde la vista. */
    public void registrarPago() {
        if (facturaSeleccionada == null || facturaSeleccionada.getIdFactura() == null) {
            mostrarAdvertencia("Debe seleccionar una factura pendiente");
            return;
        }
        if (isFacturaPagada()) {
            mostrarAdvertencia("La factura ya está completamente pagada");
            return;
        }

        PagoDAO.ResumenPago resultado;
        try {
            resultado = pagoDAO.registrarAbono(facturaSeleccionada.getIdFactura(),
                    monto, tipoPago, referenciaExterna, observaciones);
        } catch (IllegalArgumentException | IllegalStateException e) {
            mostrarAdvertencia(e.getMessage());
            return;
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Falló el registro del abono", e);
            mostrarError("No se pudo registrar el abono; compruebe su estado antes de reintentar");
            return;
        }

        // A partir de aquí el abono SÍ fue guardado: un error de recarga no
        // se presentará al usuario como error de persistencia.
        resumen = resultado;
        if (resultado.isCompletado()) {
            facturaSeleccionada.setEstado("PAGADA");
        }
        limpiarCamposPago();
        mostrarExito(resultado.isCompletado()
                ? "Factura pagada completamente; puede imprimir su comprobante"
                : "Abono registrado correctamente");

        try {
            UUID idFactura = facturaSeleccionada.getIdFactura();
            List<PagoDetalle> nuevosAbonos = pagoDetalleDAO.findByFactura(idFactura);
            abonos = nuevosAbonos != null ? nuevosAbonos : new ArrayList<>();
            List<Factura> facturas = pagoDAO.listarFacturasPendientes();
            facturasPendientes = facturas != null ? facturas : new ArrayList<>();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Abono guardado, pero falló la recarga de pantalla", e);
            mostrarAdvertencia("El abono se guardó; actualice la pantalla para consultar el historial");
        }
    }

    /** Cierra el detalle sin revertir abonos ya registrados. */
    public void cancelarSeleccion() {
        facturaSeleccionada = null;
        productosFactura = new ArrayList<>();
        abonos = new ArrayList<>();
        resumen = null;
        limpiarCamposPago();
        indicePestana = 0;
    }

    /** Regresa al listado sin borrar abonos previamente persistidos. */
    public void volverAlListado() {
        indicePestana = 0;
        actualizarFacturas();
    }

    /** Indica si el método elegido requiere una referencia externa. */
    public boolean isRequiereReferencia() {
        return tipoPago != null && !tipoPago.isBlank()
                && !"EFECTIVO".equalsIgnoreCase(tipoPago.trim());
    }

    private void limpiarCamposPago() {
        tipoPago = null;
        monto = null;
        referenciaExterna = null;
        observaciones = null;
    }

    protected void mostrarExito(String mensaje) {
        agregarMensaje(FacesMessage.SEVERITY_INFO, "Correcto", mensaje);
    }

    protected void mostrarAdvertencia(String mensaje) {
        agregarMensaje(FacesMessage.SEVERITY_WARN, "Atención", mensaje);
    }

    protected void mostrarError(String mensaje) {
        agregarMensaje(FacesMessage.SEVERITY_ERROR, "Error", mensaje);
    }

    private void agregarMensaje(FacesMessage.Severity severidad,
            String titulo, String detalle) {
        FacesContext contexto = FacesContext.getCurrentInstance();
        if (contexto != null) {
            contexto.addMessage(null, new FacesMessage(severidad, titulo, detalle));
        }
    }

    public boolean isFacturaPagada() {
        return resumen != null && resumen.isCompletado();
    }

    public int getIndicePestana() { return indicePestana; }

    public void setIndicePestana(int indicePestana) {
        this.indicePestana = indicePestana == 1 ? 1 : 0;
    }

    public List<Factura> getFacturasPendientes() { return facturasPendientes; }
    public Factura getFacturaSeleccionada() { return facturaSeleccionada; }
    public List<FacturaOrdenProducto> getProductosFactura() { return productosFactura; }
    public List<PagoDetalle> getAbonos() { return abonos; }
    public PagoDAO.ResumenPago getResumen() { return resumen; }
    public String getTipoPago() { return tipoPago; }
    public void setTipoPago(String tipoPago) { this.tipoPago = tipoPago; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public void setReferenciaExterna(String referenciaExterna) {
        this.referenciaExterna = referenciaExterna;
    }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
