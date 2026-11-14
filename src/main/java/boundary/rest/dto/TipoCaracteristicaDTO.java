package boundary.rest.dto;

import entity.TipoCaracteristica;
import java.util.UUID;

public final class TipoCaracteristicaDTO {
    private final String expresionRegular;
    private final UUID idTipoCaracteristica;
    private final String nombre;
    private final Boolean activo;
    private final String observaciones;

    public TipoCaracteristicaDTO(TipoCaracteristica entidad) {
        expresionRegular = entidad.getExpresionRegular();
        idTipoCaracteristica = entidad.getIdTipoCaracteristica();
        nombre = entidad.getNombre();
        activo = entidad.getActivo();
        observaciones = entidad.getObservaciones();
    }

    public String getExpresionRegular() { return expresionRegular; }
    public UUID getIdTipoCaracteristica() { return idTipoCaracteristica; }
    public String getNombre() { return nombre; }
    public Boolean getActivo() { return activo; }
    public String getObservaciones() { return observaciones; }
}
