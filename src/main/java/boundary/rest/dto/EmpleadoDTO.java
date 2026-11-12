package boundary.rest.dto;

import entity.Empleado;
import java.util.UUID;

/**
 * Datos de un empleado que puede recibir el frontend.
 * No incluye asignaciones de roles, ordenes ni otras relaciones JPA.
 */
public final class EmpleadoDTO {

    private final UUID idEmpleado;
    private final String nombre;
    private final String apellido;
    private final Boolean activo;
    private final String comentarios;

    public EmpleadoDTO(Empleado empleado) {
        this.idEmpleado = empleado.getIdEmpleado();
        this.nombre = empleado.getNombre();
        this.apellido = empleado.getApellido();
        this.activo = empleado.getActivo();
        this.comentarios = empleado.getComentarios();
    }

    public UUID getIdEmpleado() { return idEmpleado; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public Boolean getActivo() { return activo; }
    public String getComentarios() { return comentarios; }
}
