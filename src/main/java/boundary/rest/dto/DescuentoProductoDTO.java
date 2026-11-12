package boundary.rest.dto;

import entity.DescuentoProducto;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

/**
 * Vista plana de la asociacion descuento-producto para JSON.
 * Evita serializar Producto, Descuento y sus relaciones JPA.
 */
public final class DescuentoProductoDTO {

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    private final UUID idDescuentoProducto;
    private final UUID idDescuento;
    private final UUID idProducto;
    private final Integer valor;
    private final String fechaDesde;
    private final String fechaHasta;
    private final String observaciones;

    public DescuentoProductoDTO(DescuentoProducto relacion) {
        this.idDescuentoProducto = relacion.getIdDescuentoProducto();
        this.idDescuento = relacion.getIdDescuento() == null ? null
                : relacion.getIdDescuento().getIdDescuento();
        this.idProducto = relacion.getIdProducto() == null ? null
                : relacion.getIdProducto().getIdProducto();
        this.valor = relacion.getValor();
        this.fechaDesde = fecha(relacion.getFechaDesde());
        this.fechaHasta = fecha(relacion.getFechaHasta());
        this.observaciones = relacion.getObservaciones();
    }

    private static String fecha(Date valor) {
        return valor == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(valor.toInstant().atZone(ZONA));
    }

    public UUID getIdDescuentoProducto() { return idDescuentoProducto; }
    public UUID getIdDescuento() { return idDescuento; }
    public UUID getIdProducto() { return idProducto; }
    public Integer getValor() { return valor; }
    public String getFechaDesde() { return fechaDesde; }
    public String getFechaHasta() { return fechaHasta; }
    public String getObservaciones() { return observaciones; }
}
