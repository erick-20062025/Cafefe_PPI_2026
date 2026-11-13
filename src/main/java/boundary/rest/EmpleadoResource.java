package boundary.rest;

import boundary.rest.dto.EmpleadoDTO;
import boundary.rest.dto.EmpleadoSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.EmpleadoDAO;
import control.EmpleadoRolDAO;
import entity.Empleado;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * CRUD REST de empleados; reutiliza EmpleadoDAO y EmpleadoRolDAO.
 * El DTO no expone roles ni colecciones JPA: la consulta de los roles de un
 * empleado ya corresponde a EmpleadoRolResource.
 * API administrativa: requiere autorizacion antes de publicarse en Internet.
 */
@Path("/empleados")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EmpleadoResource {

    private static final Logger LOGGER = Logger.getLogger(EmpleadoResource.class.getName());
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Inject private EmpleadoDAO empleadoDAO;
    @Inject private EmpleadoRolDAO empleadoRolDAO;

    /** Lista empleados sin incluir asignaciones de roles. */
    @GET
    public Response listar() {
        try {
            List<EmpleadoDTO> salida = new ArrayList<>();
            List<Empleado> empleados = empleadoDAO.findAll();
            if (empleados != null) {
                for (Empleado empleado : empleados) salida.add(new EmpleadoDTO(empleado));
            }
            return Response.ok(salida).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar los empleados");
        }
    }

    /** Consulta un empleado mediante UUID canonico. */
    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") String id) {
        UUID uuid;
        try { uuid = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de empleado no es un UUID válido"); }
        try {
            Empleado empleado = empleadoDAO.findById(uuid);
            return empleado == null ? error(404, "El empleado no existe")
                    : Response.ok(new EmpleadoDTO(empleado)).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar el empleado");
        }
    }

    /** Registra un empleado; el servidor genera el identificador. */
    @POST
    public Response crear(String json) {
        EmpleadoSolicitud solicitud;
        try { solicitud = EmpleadoSolicitud.leer(json); }
        catch (EmpleadoSolicitud.DatoInvalido ex) { return error(ex.getEstadoHttp(), ex.getMessage()); }
        Empleado empleado = construir(UUID.randomUUID(), solicitud);
        try {
            empleadoDAO.crear(empleado);
            return Response.status(Response.Status.CREATED).entity(new EmpleadoDTO(empleado)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "crear el empleado");
        }
    }

    /** Reemplaza los datos editables del empleado identificado por la ruta. */
    @PUT
    @Path("/{id}")
    public Response modificar(@PathParam("id") String id, String json) {
        UUID uuid;
        try { uuid = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de empleado no es un UUID válido"); }
        EmpleadoSolicitud solicitud;
        try { solicitud = EmpleadoSolicitud.leer(json); }
        catch (EmpleadoSolicitud.DatoInvalido ex) { return error(ex.getEstadoHttp(), ex.getMessage()); }
        try {
            if (empleadoDAO.findById(uuid) == null) return error(404, "El empleado no existe");
            Empleado empleado = construir(uuid, solicitud);
            empleadoDAO.modificar(empleado);
            return Response.ok(new EmpleadoDTO(empleado)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "modificar el empleado");
        }
    }

    /**
     * Elimina solo empleados sin roles asociados. Comprobacion anticipada para
     * informar 409; EmpleadoDAO comprueba nuevamente antes de eliminar.
     */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID uuid;
        try { uuid = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de empleado no es un UUID válido"); }
        try {
            Empleado existente = empleadoDAO.findById(uuid);
            if (existente == null) return error(404, "El empleado no existe");
            if (empleadoRolDAO.countByIdEmpleado(uuid) > 0) {
                return error(409, "No se puede eliminar un empleado con roles asociados");
            }
            empleadoDAO.eliminar(existente);
            return Response.ok(new ApiResponse(ApiMensajes.CODIGO_OK,
                    ApiMensajes.MENSAJE_OK, "Empleado eliminado correctamente")).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "eliminar el empleado");
        }
    }

    private static Empleado construir(UUID id, EmpleadoSolicitud solicitud) {
        Empleado empleado = new Empleado(id);
        empleado.setNombre(solicitud.getNombre());
        empleado.setApellido(solicitud.getApellido());
        empleado.setActivo(solicitud.getActivo());
        empleado.setComentarios(solicitud.getComentarios());
        return empleado;
    }

    private static UUID uuid(String id) {
        if (id == null || !UUID_CANONICO.matcher(id).matches()) {
            throw new IllegalArgumentException("UUID inválido");
        }
        return UUID.fromString(id);
    }

    /** Utiliza el mismo catalogo de codigos y mensajes que las otras API. */
    private static Response error(int estado, String detalle) {
        String codigo;
        String mensaje;
        switch (estado) {
            case 400 -> {
                codigo = ApiMensajes.CODIGO_SOLICITUD_INVALIDA;
                mensaje = ApiMensajes.MENSAJE_SOLICITUD_INVALIDA;
            }
            case 404 -> {
                codigo = ApiMensajes.CODIGO_NO_ENCONTRADO;
                mensaje = ApiMensajes.MENSAJE_NO_ENCONTRADO;
            }
            case 409 -> {
                codigo = ApiMensajes.CODIGO_CONFLICTO;
                mensaje = ApiMensajes.MENSAJE_CONFLICTO;
            }
            case 422 -> {
                codigo = ApiMensajes.CODIGO_DATOS_INVALIDOS;
                mensaje = ApiMensajes.MENSAJE_DATOS_INVALIDOS;
            }
            default -> {
                codigo = ApiMensajes.CODIGO_ERROR_INTERNO;
                mensaje = ApiMensajes.MENSAJE_ERROR_INTERNO;
            }
        }
        return Response.status(estado).type(MediaType.APPLICATION_JSON)
                .entity(new ApiResponse(codigo, mensaje, detalle)).build();
    }

    /** Traduce reglas del DAO, aun cuando EJB envuelva la excepcion. */
    private Response errorOperacion(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        int nivel = 0;
        for (Throwable causa = ex; causa != null && nivel++ < 16; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().startsWith("23")) {
                return error(409, "La operación entra en conflicto con datos relacionados");
            }
        }
        nivel = 0;
        for (Throwable causa = ex; causa != null && nivel++ < 16; causa = causa.getCause()) {
            if (causa instanceof IllegalArgumentException) {
                Response respuesta = errorNegocio(causa.getMessage());
                if (respuesta != null) return respuesta;
            }
        }
        return error(500, "No fue posible " + operacion);
    }

    private static Response errorNegocio(String mensaje) {
        if (mensaje == null) return null;
        switch (mensaje) {
            case "El empleado ya no existe", "El empleado que intenta modificar no existe" -> {
                return error(404, "El empleado no existe");
            }
            case "Ya existe un empleado con ese identificador",
                 "No se puede eliminar el empleado porque tiene roles asociados" -> {
                return error(409, mensaje);
            }
            case "El empleado no puede ser nulo", "El identificador es obligatorio",
                 "Identificador de empleado inválido", "Debe seleccionar un empleado válido",
                 "El nombre es obligatorio", "El apellido es obligatorio",
                 "El nombre debe tener entre 2 y 255 caracteres",
                 "El apellido debe tener entre 2 y 255 caracteres",
                 "Debe indicar el estado del empleado" -> {
                return error(422, mensaje);
            }
            default -> { return null; }
        }
    }

    private Response errorTecnico(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        return error(500, "No fue posible " + operacion);
    }
}
