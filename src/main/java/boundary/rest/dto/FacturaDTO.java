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
        this.productos = detalles == null ? null : detalles.stream().map(fila -> new Detalle(fila, System.getenv("CAFEFE_CAMARERO_VIRTUAL_ID"))).toList();
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
        private final String fechaOrden;
        private final String atendidoPor;
        public String getAtendidoPor() { return atendidoPor; }
        public String getFechaOrden() { return fechaOrden; }

        Detalle(FacturaOrdenProducto fila, String virtual) {
            idFacturaOrdenProducto = fila.getIdFacturaOrdenProducto();
            var filaOrden = fila.getIdOrdenProducto();
            var catalogo = filaOrden == null ? null : filaOrden.getIdProducto();
            idOrdenProducto = filaOrden == null ? null : filaOrden.getIdOrdenProducto();
            idOrden = filaOrden == null || filaOrden.getIdOrden() == null
                    ? null : filaOrden.getIdOrden().getIdOrden();
            idProducto = catalogo == null ? null : catalogo.getIdProducto();
            producto = catalogo == null ? null : catalogo.getNombre();
            var orden = filaOrden == null ? null : filaOrden.getIdOrden();
            atendidoPor = entity.AtencionOrden.describir(orden, virtual);
            fechaOrden = orden == null || orden.getFechaCreacion() == null ? null : orden.getFechaCreacion().toInstant().toString();
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
