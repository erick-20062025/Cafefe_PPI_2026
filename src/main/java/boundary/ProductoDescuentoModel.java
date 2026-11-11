package boundary;

import control.DescuentoDAO;
import control.DescuentoProductoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.DescuentoProducto;
import entity.Producto;
import entity.TipoDescuento;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administración de descuentos desde la pantalla Producto.
 * No duplica tablas ni asociaciones; utiliza DescuentoProductoDAO.
 * Los descuentos primero deben existir en el catálogo Descuento.
 */
@Named("productoDescuentoModel")
@ViewScoped
public class ProductoDescuentoModel implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER =
            Logger.getLogger(ProductoDescuentoModel.class.getName());

    @Inject private DescuentoProductoDAO descuentoProductoDAO;
    @Inject private DescuentoDAO descuentoDAO;
    @Inject private TipoDescuentoDAO tipoDescuentoDAO;

    private Producto producto;
    private List<DescuentoProducto> modelo = new ArrayList<>();
    private List<Descuento> descuentoList = new ArrayList<>();
    private String idDescuentoSeleccionado;
    private Descuento descuentoSeleccionado;
    private Integer descuentoMaximo;
    private Date fechaDesde;
    private Date fechaHasta;
    private Integer valor;
    private String observaciones;

    /** Inicializa el catálogo de descuentos. */
    @PostConstruct
    public void init() {
        cargarDescuentos();
    }

    /** Refresca las opciones disponibles en el selector. */
    public void cargarDescuentos() {
        List<Descuento> resultado = descuentoDAO.findAll();
        descuentoList = resultado != null ? resultado : new ArrayList<>();
    }

    /**
     * Establece el producto actual y consulta sus descuentos asociados.
     * Cuando el producto es nulo, vacía el formulario y la tabla.
     */
    public void cargarProducto(Producto seleccionado) {
        producto = seleccionado;
        limpiarFormulario();
        if (seleccionado == null || seleccionado.getIdProducto() == null) {
            modelo = new ArrayList<>();
            return;
        }
        List<DescuentoProducto> resultados = descuentoProductoDAO.findByIdProducto(
                seleccionado.getIdProducto());
        modelo = resultados != null ? resultados : new ArrayList<>();
    }

    /** Se ejecuta al seleccionar otro descuento en el formulario. */
    public void cambiarDescuento() {
        descuentoMaximo = null;
        fechaDesde = null;
        fechaHasta = null;
        valor = null;
        descuentoSeleccionado = null;
        try {
            descuentoSeleccionado = buscarDescuento();
        } catch (IllegalArgumentException e) {
            mostrarAdvertencia("El descuento seleccionado no existe");
            return;
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al consultar descuento", e);
            mostrarError("No se pudo consultar el descuento");
            return;
        }
        if (descuentoSeleccionado == null) {
            return;
        }
        fechaDesde = descuentoSeleccionado.getFechaDesde();
        fechaHasta = descuentoSeleccionado.getFechaHasta();
        if (descuentoSeleccionado.getIdTipoDescuento() != null) {
            try {
                TipoDescuento tipo = tipoDescuentoDAO.findById(
                        descuentoSeleccionado.getIdTipoDescuento());
                if (tipo != null) {
                    descuentoMaximo = tipo.getDescuentoMaximo();
                }
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Error al consultar el tipo de descuento", e);
                mostrarError("No se pudo consultar el límite del descuento");
            }
        }
    }

    /**
     * Asocia un descuento existente con el producto seleccionado.
     * Conserva la selección si falla la persistencia.
     */
    public void agregarDescuento() {
        if (producto == null || producto.getIdProducto() == null) {
            mostrarAdvertencia("Debe seleccionar un producto guardado");
            return;
        }
        if (!Boolean.TRUE.equals(producto.getActivo())) {
            mostrarAdvertencia("No se pueden asociar descuentos a un producto inactivo");
            return;
        }
        if (idDescuentoSeleccionado == null || idDescuentoSeleccionado.isBlank()) {
            mostrarAdvertencia("Debe seleccionar un descuento");
            return;
        }

        try {
            Descuento descuento;
            try {
                descuento = buscarDescuento();
            } catch (IllegalArgumentException e) {
                mostrarAdvertencia("El descuento seleccionado no existe");
                return;
            }
            if (descuento == null) {
                mostrarAdvertencia("El descuento seleccionado no existe");
                return;
            }
            if (descuentoMaximo == null) {
                mostrarAdvertencia("No se pudo determinar el límite del tipo de descuento");
                return;
            }
            if (valor == null || valor <= 0) {
                mostrarAdvertencia("El descuento debe ser mayor que 0 %");
                return;
            }
            if (valor > descuentoMaximo) {
                mostrarAdvertencia("El descuento aplicado no puede superar " + descuentoMaximo + " %");
                return;
            }
            String problemaFechas = validarFechas(descuento);
            if (problemaFechas != null) {
                mostrarAdvertencia(problemaFechas);
                return;
            }

            DescuentoProducto relacion = new DescuentoProducto();
            relacion.setIdDescuentoProducto(UUID.randomUUID());
            relacion.setIdProducto(producto);
            relacion.setIdDescuento(descuento);
            relacion.setValor(valor);
            relacion.setFechaDesde(fechaDesde);
            relacion.setFechaHasta(fechaHasta);
            relacion.setObservaciones(observaciones);

            descuentoProductoDAO.crear(relacion);
        } catch (IllegalArgumentException e) {
            mostrarAdvertencia(e.getMessage());
            return;
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al asociar descuento al producto", e);
            mostrarError("No se pudo asociar el descuento");
            return;
        }

        try {
            cargarProducto(producto);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Asociación guardada pero falló la recarga", e);
            mostrarAdvertencia("El descuento se asoció, pero no pudo actualizarse la lista");
            return;
        }
        mostrarExito("Descuento asociado correctamente");
    }

    /** Elimina solamente la asociación correspondiente al producto visible. */
    public void quitarDescuento(DescuentoProducto relacion) {
        if (producto == null || producto.getIdProducto() == null
                || relacion == null || relacion.getIdProducto() == null
                || !producto.getIdProducto().equals(
                        relacion.getIdProducto().getIdProducto())) {
            mostrarAdvertencia("La asociación no pertenece al producto seleccionado");
            return;
        }
        try {
            descuentoProductoDAO.eliminar(relacion);
        } catch (IllegalArgumentException e) {
            mostrarAdvertencia(e.getMessage());
            return;
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error al quitar descuento del producto", e);
            mostrarError("No se pudo quitar el descuento");
            return;
        }
        try {
            cargarProducto(producto);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Asociación eliminada pero falló la recarga", e);
            mostrarAdvertencia("Se quitó el descuento, pero no pudo actualizarse la lista");
            return;
        }
        mostrarExito("Descuento quitado correctamente");
    }

    private Descuento buscarDescuento() {
        if (idDescuentoSeleccionado == null || idDescuentoSeleccionado.isBlank()) {
            return null;
        }
        UUID id = UUID.fromString(idDescuentoSeleccionado);
        return descuentoDAO.findById(id);
    }

    private String validarFechas(Descuento descuento) {
        if (fechaDesde == null) {
            return "Debe indicar la fecha desde";
        }
        if (descuento.getFechaDesde() == null) {
            return "El descuento no tiene fecha inicial";
        }
        if (dia(fechaDesde).before(dia(descuento.getFechaDesde()))) {
            return "La fecha desde no puede ser anterior a la fecha inicial del descuento";
        }
        if (fechaHasta != null && dia(fechaHasta).before(dia(fechaDesde))) {
            return "La fecha hasta no puede ser anterior a la fecha desde";
        }
        if (descuento.getFechaHasta() != null) {
            if (fechaHasta == null) {
                return "Debe indicar la fecha hasta para este descuento";
            }
            if (dia(fechaDesde).after(dia(descuento.getFechaHasta()))
                    || dia(fechaHasta).after(dia(descuento.getFechaHasta()))) {
                return "Las fechas están fuera de la vigencia del descuento";
            }
        }
        return null;
    }

    private static Date dia(Date fecha) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private void limpiarFormulario() {
        idDescuentoSeleccionado = null;
        descuentoSeleccionado = null;
        descuentoMaximo = null;
        fechaDesde = null;
        fechaHasta = null;
        valor = null;
        observaciones = null;
    }

    /** Mensajes sustituibles en pruebas unitarias sin FacesContext. */
    protected void mostrarExito(String detalle) {
        notificar(FacesMessage.SEVERITY_INFO, "Éxito", detalle);
    }
    protected void mostrarAdvertencia(String detalle) {
        notificar(FacesMessage.SEVERITY_WARN, "Advertencia", detalle);
    }
    protected void mostrarError(String detalle) {
        notificar(FacesMessage.SEVERITY_ERROR, "Error", detalle);
    }
    private void notificar(FacesMessage.Severity gravedad, String titulo, String detalle) {
        FacesContext ctx = FacesContext.getCurrentInstance();
        if (ctx != null) {
            ctx.addMessage(null, new FacesMessage(gravedad, titulo, detalle));
        }
    }

    public Producto getProducto() { return producto; }
    public List<DescuentoProducto> getModelo() { return modelo; }
    public List<Descuento> getDescuentoList() { return descuentoList; }
    public String getIdDescuentoSeleccionado() { return idDescuentoSeleccionado; }
    public void setIdDescuentoSeleccionado(String id) { idDescuentoSeleccionado = id; }
    public Integer getDescuentoMaximo() { return descuentoMaximo; }
    public Date getFechaDesde() { return fechaDesde; }
    public void setFechaDesde(Date fechaDesde) { this.fechaDesde = fechaDesde; }
    public Date getFechaHasta() { return fechaHasta; }
    public void setFechaHasta(Date fechaHasta) { this.fechaHasta = fechaHasta; }
    public Integer getValor() { return valor; }
    public void setValor(Integer valor) { this.valor = valor; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public Date getFechaMinimaPermitida() {
        return descuentoSeleccionado != null ? descuentoSeleccionado.getFechaDesde() : null;
    }
    public Date getFechaMaximaPermitida() {
        return descuentoSeleccionado != null ? descuentoSeleccionado.getFechaHasta() : null;
    }
    public boolean isRangoAbierto() {
        return descuentoSeleccionado != null && descuentoSeleccionado.getFechaHasta() == null;
    }
}
