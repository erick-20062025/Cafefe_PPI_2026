package boundary.rest.dto;

import entity.Caja;
import java.util.UUID;

/**
 * Representacion de Caja para respuestas REST.
 * No expone la coleccion de facturas ni sus relaciones JPA.
 */
public final class CajaDTO {

    private final UUID idCaja;
    private final String nombre;
    private final Boolean activo;
    private final String observaciones;

    public CajaDTO(Caja caja) {
        this.idCaja = caja.getIdCaja();
        this.nombre = caja.getNombre();
        this.activo = caja.getActivo();
        this.observaciones = caja.getObservaciones();
    }

    public UUID getIdCaja() { return idCaja; }
    public String getNombre() { return nombre; }
    public Boolean getActivo() { return activo; }
    public String getObservaciones() { return observaciones; }
}
