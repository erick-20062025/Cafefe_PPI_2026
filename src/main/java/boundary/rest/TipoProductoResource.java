package boundary.rest;

import boundary.rest.dto.TipoProductoDTO;
import boundary.rest.dto.TipoProductoSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.TipoProductoDAO;
import entity.TipoProducto;
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
 * CRUD REST de TipoProducto basado en TipoProductoDAO. No serializa entidades JPA ni colecciones JPA.
 * Requiere autorizacion de personal antes de publicarse en Internet.
 */
@Path("/tipos-producto")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TipoProductoResource {

    private static final Logger LOGGER = Logger.getLogger(TipoProductoResource.class.getName());
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Inject private TipoProductoDAO tipoProductoDAO;

    /** Lista tipos de producto sin sus productos asociados. */
    @GET
    public Response listar() {
        try {
            List<TipoProductoDTO> salida = new ArrayList<>();
            List<TipoProducto> tipos = tipoProductoDAO.findAll();
            if (tipos != null) {
                for (TipoProducto tipoProducto : tipos) {
                    salida.add(new TipoProductoDTO(tipoProducto));
                }
            }
            return Response.ok(salida).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar los tipos de producto");
        }
    }

    /** Busca un tipo de producto por UUID. */
    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de tipo de producto no es un UUID valido");
        }
        try {
            TipoProducto tipoProducto = tipoProductoDAO.findById(uuid);
            if (tipoProducto == null) return error(404, "El tipo de producto no existe");
            return Response.ok(new TipoProductoDTO(tipoProducto)).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar el tipo de producto");
        }
    }

    /** Crea un tipo de producto; el UUID lo asigna exclusivamente el servidor. */
    @POST
    public Response crear(String json) {
        TipoProductoSolicitud solicitud;
        try {
            solicitud = TipoProductoSolicitud.leer(json);
        } catch (TipoProductoSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        TipoProducto tipoProducto = construir(UUID.randomUUID(), solicitud);
        try {
            tipoProductoDAO.crear(tipoProducto);
            return Response.status(Response.Status.CREATED).entity(new TipoProductoDTO(tipoProducto)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "crear el tipo de producto");
        }
    }

    /** Reemplaza los campos editables; el identificador se recibe por URL. */
    @PUT
    @Path("/{id}")
    public Response modificar(@PathParam("id") String id, String json) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de tipo de producto no es un UUID valido");
        }
        TipoProductoSolicitud solicitud;
        try {
            solicitud = TipoProductoSolicitud.leer(json);
        } catch (TipoProductoSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        try {
            if (tipoProductoDAO.findById(uuid) == null) return error(404, "El tipo de producto no existe");
            TipoProducto tipoProducto = construir(uuid, solicitud);
            tipoProductoDAO.modificar(tipoProducto);
            return Response.ok(new TipoProductoDTO(tipoProducto)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "modificar el tipo de producto");
        }
    }

    /** Elimina solo un tipo de producto sin asociaciones; el DAO verifica nuevamente la regla. */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de tipo de producto no es un UUID valido");
        }
        try {
            TipoProducto existente = tipoProductoDAO.findById(uuid);
            if (existente == null) return error(404, "El tipo de producto no existe");
            if (tipoProductoDAO.countRelaciones(uuid) > 0) {
                return error(409, "No se puede eliminar un tipo de producto con productos asociados");
            }
            tipoProductoDAO.eliminar(existente);
            return Response.ok(new ApiResponse(
                    ApiMensajes.CODIGO_OK, ApiMensajes.MENSAJE_OK,
                    "Tipo de producto eliminado correctamente")).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "eliminar el tipo de producto");
        }
    }

    private static TipoProducto construir(UUID id, TipoProductoSolicitud solicitud) {
        TipoProducto tipoProducto = new TipoProducto(id);
        tipoProducto.setNombre(solicitud.getNombre());
        tipoProducto.setActivo(solicitud.getActivo());
        tipoProducto.setObservaciones(solicitud.getObservaciones());
        return tipoProducto;
    }

    private static UUID uuid(String id) {
        if (id == null || !UUID_CANONICO.matcher(id).matches()) {
            throw new IllegalArgumentException("UUID invalido");
        }
        return UUID.fromString(id);
    }

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

    /** Solo transforma reglas reconocidas de TipoProductoDAO; no filtra errores SQL. */
    private Response errorOperacion(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        int profundidad = 0;
        for (Throwable actual = ex; actual != null && profundidad++ < 16;
                actual = actual.getCause()) {
            if (actual instanceof SQLException sql) {
                String estado = sql.getSQLState();
                if (estado != null && estado.startsWith("23")) {
                    return error(409, "El tipo de producto tiene datos relacionados que impiden la operacion");
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
        if (mensaje.equals("El tipo de producto ya no existe")
                || mensaje.equals("El tipo de producto que intenta modificar no existe")) {
            return error(404, "El tipo de producto no existe");
        }
        if (mensaje.equals("No se puede eliminar un tipo de producto que tiene productos asociados")
                || mensaje.equals("Ya existe un tipo de producto con ese identificador")) {
            return error(409, mensaje);
        }
        if (mensaje.equals("El tipo de producto no puede ser nulo")
                || mensaje.equals("El identificador del tipo de producto es obligatorio")
                || mensaje.equals("Debe ingresar el nombre del tipo de producto")
                || mensaje.equals("El nombre del tipo de producto no puede superar 155 caracteres")
                || mensaje.equals("Debe indicar el estado del tipo de producto")) {
            return error(422, mensaje);
        }
        return null;
    }

    private Response errorTecnico(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        return error(500, "No fue posible " + operacion);
    }
}
