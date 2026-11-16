package boundary.rest.dto;

import control.PagoDAO;
import entity.Factura;
import entity.FacturaOrdenProducto;
import entity.OrdenProducto;
import entity.PagoDetalle;
import entity.Producto;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Respuestas planas de Pago: impiden serializar asociaciones JPA recursivas.
 * No tienen lógica de persistencia.
 */
public final class PagoDTO {
    private PagoDTO() { }

    /** Cabecera mínima para los listados del cajero. */
    public static final class FacturaItem {
        private final UUID idFactura;
        private final String estado;
        public FacturaItem(Factura factura) {
            this.idFactura = factura.getIdFactura();
            this.estado = factura.getEstado();
        }
        public UUID getIdFactura() { return idFactura; }
        public String getEstado() { return estado; }
    }

    /** Total, abonos y saldo de una sola factura. */
    public static final class Resumen {
        private final UUID idFactura;
        private final BigDecimal total;
        private final BigDecimal abonado;
        private final BigDecimal saldo;
        private final boolean completado;
        public Resumen(UUID idFactura, PagoDAO.ResumenPago resumen) {
            this.idFactura = idFactura;
            this.total = resumen.getTotal();
            this.abonado = resumen.getAbonado();
            this.saldo = resumen.getSaldo();
            this.completado = resumen.isCompletado();
        }
        public UUID getIdFactura() { return idFactura; }
        public BigDecimal getTotal() { return total; }
        public BigDecimal getAbonado() { return abonado; }
        public BigDecimal getSaldo() { return saldo; }
        public boolean isCompletado() { return completado; }
    }

    /** Precio propio de la factura, no el precio actual del catálogo. */
    public static final class ProductoFactura {
        private final UUID idOrdenProducto;
        private final UUID idProducto;
        private final String nombreProducto;
        private final BigDecimal precio;
        public ProductoFactura(FacturaOrdenProducto detalle) {
            OrdenProducto fila = detalle.getIdOrdenProducto();
            Producto producto = fila == null ? null : fila.getIdProducto();
            this.idOrdenProducto = fila == null ? null : fila.getIdOrdenProducto();
            this.idProducto = producto == null ? null : producto.getIdProducto();
            this.nombreProducto = producto == null ? null : producto.getNombre();
            this.precio = detalle.getPrecio();
        }
        public UUID getIdOrdenProducto() { return idOrdenProducto; }
        public UUID getIdProducto() { return idProducto; }
        public String getNombreProducto() { return nombreProducto; }
        public BigDecimal getPrecio() { return precio; }
    }

    /** Un abono individual, sin devolver Pago ni Factura completos. */
    public static final class Abono {
        private final UUID idPagoDetalle;
        private final UUID idPago;
        private final BigDecimal efectivoRecibido;
        private final BigDecimal cambioEntregado;
        public BigDecimal getEfectivoRecibido() { return efectivoRecibido; }
        public BigDecimal getCambioEntregado() { return cambioEntregado; }
        private final BigDecimal monto;
        private final String tipoPago;
        private final String referenciaExterna;
        private final String observaciones;
        public Abono(PagoDetalle detalle) {
            this.idPagoDetalle = detalle.getIdPagoDetalle();
            this.idPago = detalle.getIdPago() == null ? null : detalle.getIdPago().getIdPago();
            this.efectivoRecibido = detalle.getEfectivoRecibido();
            this.cambioEntregado = detalle.getCambioEntregado();
            this.monto = detalle.getMonto();
            this.tipoPago = detalle.getTipoPago();
            this.referenciaExterna = detalle.getReferenciaExterna();
            this.observaciones = detalle.getObservaciones();
        }
        public UUID getIdPagoDetalle() { return idPagoDetalle; }
        public UUID getIdPago() { return idPago; }
        public BigDecimal getMonto() { return monto; }
        public String getTipoPago() { return tipoPago; }
        public String getReferenciaExterna() { return referenciaExterna; }
        public String getObservaciones() { return observaciones; }
    }
}
