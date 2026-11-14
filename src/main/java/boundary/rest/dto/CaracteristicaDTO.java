package boundary.rest.dto;

import entity.Caracteristica;
import java.util.UUID;

public final class CaracteristicaDTO {
    private final UUID idCaracteristica;
    private final UUID idTipoCaracteristica;
    private final String nombre;
    private final Boolean activo;
    private final String observaciones;

    public CaracteristicaDTO(Caracteristica entidad) {
        idCaracteristica = entidad.getIdCaracteristica();
        idTipoCaracteristica = entidad.getIdTipoCaracteristica() == null ? null : entidad.getIdTipoCaracteristica().getIdTipoCaracteristica();
        nombre = entidad.getNombre();
        activo = entidad.getActivo();
        observaciones = entidad.getObservaciones();
    }

    public UUID getIdCaracteristica() { return idCaracteristica; }
    public UUID getIdTipoCaracteristica() { return idTipoCaracteristica; }
    public String getNombre() { return nombre; }
    public Boolean getActivo() { return activo; }
    public String getObservaciones() { return observaciones; }
}
