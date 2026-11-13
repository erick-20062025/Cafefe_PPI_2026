package boundary.rest.dto;

import entity.Rol;
import java.util.UUID;

/**
 * Representacion de Rol para respuestas REST.
 * No expone la coleccion de empleados ni sus relaciones JPA.
 */
public final class RolDTO {

    private final UUID idRol;
    private final String nombre;
    private final Boolean activo;
    private final String observaciones;

    public RolDTO(Rol rol) {
        this.idRol = rol.getIdRol();
        this.nombre = rol.getNombre();
        this.activo = rol.getActivo();
        this.observaciones = rol.getObservaciones();
    }

    public UUID getIdRol() { return idRol; }
    public String getNombre() { return nombre; }
    public Boolean getActivo() { return activo; }
    public String getObservaciones() { return observaciones; }
}
