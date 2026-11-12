package boundary.rest.dto;

import entity.Descuento;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

/**
 * Representación plana de un descuento para el frontend.
 * No expone DescuentoProducto ni objetos JPA relacionados.
 */
public final class DescuentoDTO {

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");
    private final UUID idDescuento;
    private final UUID idTipoDescuento;
    private final String nombre;
    private final String fechaDesde;
    private final String fechaHasta;
    private final String observaciones;

    public DescuentoDTO(Descuento descuento) {
        this.idDescuento = descuento.getIdDescuento();
        this.idTipoDescuento = descuento.getIdTipoDescuento();
        this.nombre = descuento.getNombre();
        this.fechaDesde = fecha(descuento.getFechaDesde());
        this.fechaHasta = fecha(descuento.getFechaHasta());
        this.observaciones = descuento.getObservaciones();
    }

    private static String fecha(Date valor) {
        return valor == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(valor.toInstant().atZone(ZONA));
    }

    public UUID getIdDescuento() { return idDescuento; }
    public UUID getIdTipoDescuento() { return idTipoDescuento; }
    public String getNombre() { return nombre; }
    public String getFechaDesde() { return fechaDesde; }
    public String getFechaHasta() { return fechaHasta; }
    public String getObservaciones() { return observaciones; }
}
