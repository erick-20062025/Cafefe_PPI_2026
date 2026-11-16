package boundary.rest.dto;

import entity.Factura;
import entity.FacturaOrdenProducto;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public final class FacturaDTO {
    private final Factura factura;
    private final List<Detalle> productos;

    public FacturaDTO(Factura factura, List<FacturaOrdenProducto> detalles) {
        this.factura = factura;
        this.productos = detalles == null ? null : detalles.stream().map(Detalle::new).toList();
    }

    public UUID getIdFactura() { return factura.getIdFactura(); }
    public String getCliente() { return factura.getCliente(); }
    public String getEstado() { return factura.getEstado(); }
    public String getObservaciones() { return factura.getObservaciones(); }
    public String getFechaFacturacion() {
        return factura.getFechaFacturacion() == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(factura.getFechaFacturacion().toInstant().atZone(ZoneId.of("America/El_Salvador")));
    }
    public UUID getIdCaja() { return factura.getIdCaja() == null ? null : factura.getIdCaja().getIdCaja(); }
    public UUID getIdEmpleadoRol() {
        return factura.getIdEmpleadoRol() == null ? null : factura.getIdEmpleadoRol().getIdEmpleadoRol();
    }
    public List<Detalle> getProductos() { return productos; }

    public static final class Detalle {
        private final UUID idFacturaOrdenProducto;
        private final UUID idOrdenProducto;
        private final UUID idOrden;
        private final UUID idProducto;
        private final String producto;
        private final BigDecimal precio;
        private final String observaciones;
        private final String caracteristicas;
        private final String atendidoPor;
        private final String fechaOrden;
        private final BigDecimal precioBase;
        private final BigDecimal descuento;
        public String getCaracteristicas() { return caracteristicas; }
        public String getAtendidoPor() { return atendidoPor; }
        public String getFechaOrden() { return fechaOrden; }
        public BigDecimal getPrecioBase() { return precioBase; }
        public BigDecimal getDescuento() { return descuento; }

        Detalle(FacturaOrdenProducto fila) {
            idFacturaOrdenProducto = fila.getIdFacturaOrdenProducto();
            var filaOrden = fila.getIdOrdenProducto();
            var catalogo = filaOrden == null ? null : filaOrden.getIdProducto();
            idOrdenProducto = filaOrden == null ? null : filaOrden.getIdOrdenProducto();
            idOrden = filaOrden == null || filaOrden.getIdOrden() == null
                    ? null : filaOrden.getIdOrden().getIdOrden();
            idProducto = catalogo == null ? null : catalogo.getIdProducto();
            producto = filaOrden != null && filaOrden.getProductoHistorico() != null ? filaOrden.getProductoHistorico() : catalogo == null ? null : catalogo.getNombre();
            var orden = filaOrden == null ? null : filaOrden.getIdOrden();
            caracteristicas = filaOrden == null ? "" : (filaOrden.getCaracteristicasHistoricas() == null ? entity.CaracteristicasProducto.texto(catalogo) : filaOrden.getCaracteristicasHistoricas());
            atendidoPor = orden == null ? null : orden.getAtendidoPor();
            fechaOrden = orden == null || orden.getFechaCreacion() == null ? null : orden.getFechaCreacion().toInstant().toString();
            precioBase = filaOrden == null || filaOrden.getPrecioBase() == null ? fila.getPrecio() : filaOrden.getPrecioBase();
            descuento = filaOrden == null || filaOrden.getDescuentoAplicado() == null ? BigDecimal.ZERO : filaOrden.getDescuentoAplicado();
            precio = fila.getPrecio();
            observaciones = fila.getObservaciones();
        }

        public UUID getIdFacturaOrdenProducto() { return idFacturaOrdenProducto; }
        public UUID getIdOrdenProducto() { return idOrdenProducto; }
        public UUID getIdOrden() { return idOrden; }
        public UUID getIdProducto() { return idProducto; }
        public String getProducto() { return producto; }
        public BigDecimal getPrecio() { return precio; }
        public String getObservaciones() { return observaciones; }
    }
}
