package boundary.rest.dto;

import entity.TipoProducto;
import java.util.UUID;

/**
 * Representacion de TipoProducto para respuestas REST.
 * No expone las colecciones ni las relaciones JPA.
 */
public final class TipoProductoDTO {

    private final UUID idTipoProducto;
    private final String nombre;
    private final Boolean activo;
    private final String observaciones;

    public TipoProductoDTO(TipoProducto tipoProducto) {
        this.idTipoProducto = tipoProducto.getIdTipoProducto();
        this.nombre = tipoProducto.getNombre();
        this.activo = tipoProducto.getActivo();
        this.observaciones = tipoProducto.getObservaciones();
    }

    public UUID getIdTipoProducto() { return idTipoProducto; }
    public String getNombre() { return nombre; }
    public Boolean getActivo() { return activo; }
    public String getObservaciones() { return observaciones; }
}
