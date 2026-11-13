package boundary.rest;

import boundary.rest.dto.EmpleadoRolDTO;
import boundary.rest.dto.EmpleadoRolSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.EmpleadoRolDAO;
import entity.Empleado;
import entity.EmpleadoRol;
import entity.Rol;
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
 * Operaciones de asignacion de roles existentes a empleados existentes.
 * Solo utiliza EmpleadoRolDAO y DTO: no serializa las relaciones con Orden ni Factura.
 * Requiere autenticacion/autorizacion del personal antes de publicarse en Internet.
 */
@Path("/empleado-roles")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EmpleadoRolResource {

    private static final Logger LOGGER = Logger.getLogger(EmpleadoRolResource.class.getName());
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Inject private EmpleadoRolDAO empleadoRolDAO;

    /** GET /api/empleado-roles: lista asignaciones sin relaciones ciclicas. */
    @GET
    public Response listar() {
        try {
            return Response.ok(convertir(empleadoRolDAO.findAll())).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar las asignaciones");
        }
    }

    /** GET /api/empleado-roles/empleados/{idEmpleado}: roles de ESE empleado. */
    @GET
    @Path("/empleados/{idEmpleado}")
    public Response listarPorEmpleado(@PathParam("idEmpleado") String idEmpleado) {
        UUID id;
        try { id = uuid(idEmpleado); }
        catch (IllegalArgumentException ex) { return error(400, "El empleado debe tener un UUID válido"); }
        try {
            return Response.ok(convertir(empleadoRolDAO.findByIdEmpleado(id))).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar los roles del empleado");
        }
    }

    /** GET /api/empleado-roles/{id}: consulta por UUID. */
    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") String id) {
        UUID uuid;
        try { uuid = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de asignación no es válido"); }
        try {
            EmpleadoRol relacion = empleadoRolDAO.findById(uuid);
            return relacion == null ? error(404, "La asignación no existe")
                    : Response.ok(new EmpleadoRolDTO(relacion)).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar la asignación");
        }
    }

    /** POST: crea asignacion entre empleado y rol existentes y activos. */
    @POST
    public Response crear(String json) {
        EmpleadoRolSolicitud solicitud;
        try { solicitud = EmpleadoRolSolicitud.crear(json); }
        catch (EmpleadoRolSolicitud.DatoInvalido ex) { return error(ex.getEstadoHttp(), ex.getMessage()); }
        EmpleadoRol relacion = new EmpleadoRol(UUID.randomUUID());
        Empleado empleado = new Empleado();
        empleado.setIdEmpleado(solicitud.getIdEmpleado());
        relacion.setIdEmpleado(empleado);
        relacion.setIdRol(new Rol(solicitud.getIdRol()));
        relacion.setActivo(solicitud.getActivo());
        relacion.setObservaciones(solicitud.getObservaciones());
        try {
            empleadoRolDAO.crear(relacion);
            return Response.status(Response.Status.CREATED).entity(new EmpleadoRolDTO(relacion)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "crear la asignación");
        }
    }

    /** PUT: cambia exclusivamente activo/observaciones; no reasigna empleado ni rol. */
    @PUT
    @Path("/{id}")
    public Response modificar(@PathParam("id") String id, String json) {
        UUID uuid;
        try { uuid = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de asignación no es válido"); }
        EmpleadoRolSolicitud solicitud;
        try { solicitud = EmpleadoRolSolicitud.modificar(json); }
        catch (EmpleadoRolSolicitud.DatoInvalido ex) { return error(ex.getEstadoHttp(), ex.getMessage()); }
        try {
            EmpleadoRol actual = empleadoRolDAO.findById(uuid);
            if (actual == null) return error(404, "La asignación no existe");
            EmpleadoRol nueva = new EmpleadoRol(uuid);
            nueva.setIdEmpleado(actual.getIdEmpleado());
            nueva.setIdRol(actual.getIdRol());
            nueva.setActivo(solicitud.getActivo());
            nueva.setObservaciones(solicitud.getObservaciones());
            empleadoRolDAO.modificar(nueva);
            return Response.ok(new EmpleadoRolDTO(nueva)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "modificar la asignación");
        }
    }

    /** DELETE: el DAO protege asignaciones con historial de ordenes o facturas. */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID uuid;
        try { uuid = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de asignación no es válido"); }
        try {
            EmpleadoRol relacion = empleadoRolDAO.findById(uuid);
            if (relacion == null) return error(404, "La asignación no existe");
            empleadoRolDAO.eliminar(relacion);
            return Response.ok(new ApiResponse(ApiMensajes.CODIGO_OK,
                    ApiMensajes.MENSAJE_OK, "Asignación eliminada correctamente")).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "eliminar la asignación");
        }
    }

    private static List<EmpleadoRolDTO> convertir(List<EmpleadoRol> origen) {
        List<EmpleadoRolDTO> lista = new ArrayList<>();
        if (origen != null) {
            for (EmpleadoRol relacion : origen) lista.add(new EmpleadoRolDTO(relacion));
        }
        return lista;
    }

    private static UUID uuid(String texto) {
        if (texto == null || !UUID_CANONICO.matcher(texto).matches()) {
            throw new IllegalArgumentException("UUID inválido");
        }
        return UUID.fromString(texto);
    }

    private static Response error(int estado, String detalle) {
        String codigo, mensaje;
        switch (estado) {
            case 400 -> { codigo = ApiMensajes.CODIGO_SOLICITUD_INVALIDA;
                          mensaje = ApiMensajes.MENSAJE_SOLICITUD_INVALIDA; }
            case 404 -> { codigo = ApiMensajes.CODIGO_NO_ENCONTRADO;
                          mensaje = ApiMensajes.MENSAJE_NO_ENCONTRADO; }
            case 409 -> { codigo = ApiMensajes.CODIGO_CONFLICTO;
                          mensaje = ApiMensajes.MENSAJE_CONFLICTO; }
            case 422 -> { codigo = ApiMensajes.CODIGO_DATOS_INVALIDOS;
                          mensaje = ApiMensajes.MENSAJE_DATOS_INVALIDOS; }
            default -> { codigo = ApiMensajes.CODIGO_ERROR_INTERNO;
                         mensaje = ApiMensajes.MENSAJE_ERROR_INTERNO; }
        }
        return Response.status(estado).type(MediaType.APPLICATION_JSON)
                .entity(new ApiResponse(codigo, mensaje, detalle)).build();
    }

    /** Traduce las excepciones de negocio incluso cuando EJB las envuelve. */
    private Response errorOperacion(RuntimeException ex, String accion) {
        LOGGER.log(Level.SEVERE, "Error al " + accion, ex);
        int n = 0;
        for (Throwable t = ex; t != null && n++ < 16; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().startsWith("23")) {
                return error(409, "La operación entra en conflicto con relaciones existentes");
            }
        }
        n = 0;
        for (Throwable t = ex; t != null && n++ < 16; t = t.getCause()) {
            if (t instanceof IllegalArgumentException) {
                Response respuesta = errorNegocio(t.getMessage());
                if (respuesta != null) return respuesta;
            }
        }
        return error(500, "No fue posible " + accion);
    }

    private static Response errorNegocio(String texto) {
        if (texto == null) return null;
        switch (texto) {
            case "La asignación no existe", "La asignación ya no existe" -> {
                return error(404, "La asignación no existe");
            }
            case "La asignación ya existe", "El empleado ya tiene ese rol asignado",
                 "No se puede quitar este rol porque tiene órdenes o facturas asociadas",
                 "No se puede cambiar el empleado o el rol de una asignación existente" -> {
                return error(409, texto);
            }
            case "El empleado o el rol no existe",
                 "No se pueden asignar roles a un empleado inactivo",
                 "No se puede asignar un rol inactivo",
                 "No se puede activar una relación con empleado o rol inactivo",
                 "Debe seleccionar un empleado", "Debe seleccionar un rol",
                 "Debe indicar el estado de la asignación",
                 "La asignación no puede ser nula",
                 "Identificador de relación inválido",
                 "Debe seleccionar una asignación válida" -> {
                return error(422, texto);
            }
            default -> {
                if (texto.startsWith("El identificador de ")
                        && texto.endsWith(" es obligatorio")) return error(422, texto);
                return null;
            }
        }
    }

    private Response errorTecnico(RuntimeException ex, String accion) {
        LOGGER.log(Level.SEVERE, "Error al " + accion, ex);
        return error(500, "No fue posible " + accion);
    }
}
