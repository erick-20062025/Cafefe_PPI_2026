package boundary.rest.dto;

import entity.ProductoTipoProducto;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

/**
 * Vista plana de la asociacion producto-tipo para JSON.
 * Evita serializar Producto, TipoProducto y sus relaciones JPA.
 */
public final class ProductoTipoProductoDTO {

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    private final UUID idProductoTipoProducto;
    private final UUID idTipoProducto;
    private final UUID idProducto;
    private final String fechaCreacion;
    private final String observaciones;

    public ProductoTipoProductoDTO(ProductoTipoProducto relacion) {
        this.idProductoTipoProducto = relacion.getIdProductoTipoProducto();
        this.idTipoProducto = relacion.getIdTipoProducto() == null ? null
                : relacion.getIdTipoProducto().getIdTipoProducto();
        this.idProducto = relacion.getProducto() == null ? null
                : relacion.getProducto().getIdProducto();
        this.fechaCreacion = fecha(relacion.getFechaCreacion());
        this.observaciones = relacion.getObservaciones();
    }

    private static String fecha(Date valor) {
        return valor == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(valor.toInstant().atZone(ZONA));
    }

    public UUID getIdProductoTipoProducto() { return idProductoTipoProducto; }
    public UUID getIdTipoProducto() { return idTipoProducto; }
    public UUID getIdProducto() { return idProducto; }
    public String getFechaCreacion() { return fechaCreacion; }
    public String getObservaciones() { return observaciones; }
}
