package boundary.rest.dto;

import entity.EmpleadoRol;
import java.util.UUID;

/**
 * Una asignacion resumida; nunca expone las colecciones Orden ni Factura.
 * La lista de roles de un empleado se obtiene desde su propio endpoint.
 */
public final class EmpleadoRolDTO {
    private final UUID idEmpleadoRol;
    private final UUID idEmpleado;
    private final UUID idRol;
    private final Boolean activo;
    private final String observaciones;

    public EmpleadoRolDTO(EmpleadoRol relacion) {
        this.idEmpleadoRol = relacion.getIdEmpleadoRol();
        this.idEmpleado = relacion.getIdEmpleado() == null ? null
                : relacion.getIdEmpleado().getIdEmpleado();
        this.idRol = relacion.getIdRol() == null ? null
                : relacion.getIdRol().getIdRol();
        this.activo = relacion.getActivo();
        this.observaciones = relacion.getObservaciones();
    }

    public UUID getIdEmpleadoRol() { return idEmpleadoRol; }
    public UUID getIdEmpleado() { return idEmpleado; }
    public UUID getIdRol() { return idRol; }
    public Boolean getActivo() { return activo; }
    public String getObservaciones() { return observaciones; }
}
