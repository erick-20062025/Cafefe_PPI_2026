package boundary.rest;

import boundary.rest.dto.RolDTO;
import boundary.rest.dto.RolSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.RolDAO;
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
 * Operaciones REST de roles usando exclusivamente RolDAO.
 * No devuelve empleadoRolCollection para evitar ciclos JSON.
 * Requiere autenticacion/autorizacion antes de exponerlo en Internet.
 */
@Path("/roles")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class RolResource {

    private static final Logger LOGGER = Logger.getLogger(RolResource.class.getName());
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Inject private RolDAO rolDAO;

    /** Lista roles resumidos; nunca devuelve relaciones con empleados. */
    @GET
    public Response listar() {
        try {
            List<RolDTO> salida = new ArrayList<>();
            List<Rol> roles = rolDAO.findAll();
            if (roles != null) {
                for (Rol rol : roles) salida.add(new RolDTO(rol));
            }
            return Response.ok(salida).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar los roles");
        }
    }

    /** Consulta un rol mediante UUID canonico. */
    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de rol no es un UUID valido");
        }
        try {
            Rol rol = rolDAO.findById(uuid);
            return rol == null ? error(404, "El rol no existe")
                    : Response.ok(new RolDTO(rol)).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar el rol");
        }
    }

    /** Crea un rol; su identificador es generado siempre por el servidor. */
    @POST
    public Response crear(String json) {
        RolSolicitud solicitud;
        try {
            solicitud = RolSolicitud.leer(json);
        } catch (RolSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        Rol rol = construir(UUID.randomUUID(), solicitud);
        try {
            rolDAO.crear(rol);
            return Response.status(Response.Status.CREATED).entity(new RolDTO(rol)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "crear el rol");
        }
    }

    /** Reemplaza los campos editables. El UUID solo se toma de la ruta. */
    @PUT
    @Path("/{id}")
    public Response modificar(@PathParam("id") String id, String json) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de rol no es un UUID valido");
        }
        RolSolicitud solicitud;
        try {
            solicitud = RolSolicitud.leer(json);
        } catch (RolSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        try {
            if (rolDAO.findById(uuid) == null) return error(404, "El rol no existe");
            Rol rol = construir(uuid, solicitud);
            rolDAO.modificar(rol);
            return Response.ok(new RolDTO(rol)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "modificar el rol");
        }
    }

    /**
     * Elimina un rol. Si tiene empleados asociados, PostgreSQL protege la clave
     * foranea; el error de integridad se traduce a 409 sin exponer SQL interno.
     * No se agregan consultas al DAO que no existian en el backend original.
     */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de rol no es un UUID valido");
        }
        try {
            Rol rol = rolDAO.findById(uuid);
            if (rol == null) return error(404, "El rol no existe");
            rolDAO.eliminar(rol);
            return Response.ok(new ApiResponse(ApiMensajes.CODIGO_OK,
                    ApiMensajes.MENSAJE_OK, "Rol eliminado correctamente")).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "eliminar el rol");
        }
    }

    private static Rol construir(UUID id, RolSolicitud solicitud) {
        Rol rol = new Rol(id);
        rol.setNombre(solicitud.getNombre());
        rol.setActivo(solicitud.getActivo());
        rol.setObservaciones(solicitud.getObservaciones());
        return rol;
    }

    private static UUID uuid(String id) {
        if (id == null || !UUID_CANONICO.matcher(id).matches()) {
            throw new IllegalArgumentException("UUID invalido");
        }
        return UUID.fromString(id);
    }

    /** Usa exactamente el catalogo compartido que utilizan las otras APIs. */
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

    /** Traduce exclusivamente reglas conocidas; evita devolver mensajes SQL. */
    private Response errorOperacion(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        int profundidad = 0;
        for (Throwable actual = ex; actual != null && profundidad++ < 16;
                actual = actual.getCause()) {
            if (actual instanceof SQLException sql) {
                String estado = sql.getSQLState();
                if (estado != null && estado.startsWith("23")) {
                    return error(409, "El rol tiene relaciones que impiden esta operacion");
                }
            }
        }
        profundidad = 0;
        for (Throwable actual = ex; actual != null && profundidad++ < 16;
                actual = actual.getCause()) {
            if (actual instanceof IllegalArgumentException) {
                Response respuesta = errorNegocio(actual.getMessage());
                if (respuesta != null) return respuesta;
            }
        }
        return error(500, "No fue posible " + operacion);
    }

    private static Response errorNegocio(String mensaje) {
        if (mensaje == null) return null;
        if (mensaje.equals("El rol ya no existe")
                || mensaje.equals("El rol que intenta modificar no existe")) {
            return error(404, "El rol no existe");
        }
        if (mensaje.equals("Ya existe un rol con ese identificador")) {
            return error(409, mensaje);
        }
        if (mensaje.equals("El rol no puede ser nulo")
                || mensaje.equals("El identificador del rol es obligatorio")
                || mensaje.equals("El identificador del rol no es válido")
                || mensaje.equals("El nombre del rol es obligatorio")
                || mensaje.equals("El nombre debe tener entre 2 y 155 caracteres")
                || mensaje.equals("Debe indicar el estado del rol")) {
            return error(422, mensaje);
        }
        return null;
    }

    private Response errorTecnico(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        return error(500, "No fue posible " + operacion);
    }
}
