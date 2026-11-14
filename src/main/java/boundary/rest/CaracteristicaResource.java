package boundary.rest;

import boundary.rest.dto.CaracteristicaDTO;
import boundary.rest.dto.CaracteristicaSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.CaracteristicaDAO;
import entity.Caracteristica;
import control.TipoCaracteristicaDAO;
import entity.TipoCaracteristica;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/caracteristicas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CaracteristicaResource {
    private static final Logger LOGGER = Logger.getLogger(CaracteristicaResource.class.getName());
    @Inject private CaracteristicaDAO dao;
    @Inject private TipoCaracteristicaDAO tipoDAO;

    @GET
    public Response listar() {
        return ejecutar(() -> Response.ok(dao.findAll().stream().map(CaracteristicaDTO::new).toList()).build());
    }

    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") String id) {
        return ejecutar(() -> {
            Caracteristica entidad = dao.findById(CaracteristicaSolicitud.uuid(id));
            return entidad == null ? error(404, "El registro no existe")
                    : Response.ok(new CaracteristicaDTO(entidad)).build();
        });
    }

    @POST
    public Response crear(String json) {
        return ejecutar(() -> guardar(UUID.randomUUID(), CaracteristicaSolicitud.leer(json), true));
    }

    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        return ejecutar(() -> {
            Caracteristica entidad = dao.findById(CaracteristicaSolicitud.uuid(id));
            if (entidad == null) return error(404, "El registro no existe");
            dao.eliminar(entidad);
            return Response.ok(new ApiResponse(ApiMensajes.CODIGO_OK, ApiMensajes.MENSAJE_OK,
                    "Registro eliminado correctamente")).build();
        });
    }

    @PUT
    @Path("/{id}")
    public Response modificar(@PathParam("id") String id, String json) {
        return ejecutar(() -> guardar(CaracteristicaSolicitud.uuid(id),
                CaracteristicaSolicitud.leer(json), false));
    }

    private Response guardar(UUID id, CaracteristicaSolicitud solicitud, boolean nuevo) {
        Caracteristica entidad = nuevo ? new Caracteristica(id) : dao.findById(id);
        if (entidad == null) return error(404, "La característica no existe");
        TipoCaracteristica tipo = tipoDAO.findById(solicitud.idTipoCaracteristica());
        if (tipo == null) return error(422, "El tipo de característica no existe");
        entidad.setIdTipoCaracteristica(tipo);
        entidad.setNombre(solicitud.nombre());
        entidad.setActivo(solicitud.activo());
        entidad.setObservaciones(solicitud.observaciones());
        if (nuevo) {
            dao.crear(entidad);
        } else {
            dao.modificar(entidad);
        }
        return Response.status(nuevo ? 201 : 200).entity(new CaracteristicaDTO(entidad)).build();
    }

    private Response ejecutar(Supplier<Response> operacion) {
        try {
            return operacion.get();
        } catch (CaracteristicaSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        } catch (RuntimeException ex) {
            int nivel = 0;
            for (Throwable causa = ex; causa != null && nivel++ < 16; causa = causa.getCause()) {
                if (causa instanceof SQLException sql && sql.getSQLState() != null
                        && sql.getSQLState().startsWith("23")) {
                    return error(409, "Conflicto de integridad con registros relacionados");
                }
                if (!(causa instanceof IllegalArgumentException) || causa.getMessage() == null) continue;
                String mensaje = causa.getMessage();
                Response respuesta = switch (mensaje) {
                    case "La característica no puede ser nula", "El nombre es obligatorio",
                         "El nombre no puede superar 155 caracteres",
                         "Debe indicar si la característica está activa o inactiva",
                         "Debe seleccionar un tipo de característica",
                         "No se puede asignar un tipo de característica inactivo" -> error(422, mensaje);
                    default -> null;
                };
                if (respuesta != null) return respuesta;
            }
            LOGGER.log(Level.SEVERE, "Error al gestionar caracteristicas", ex);
            return error(500, "No se pudo completar la operación");
        }
    }

    private Response error(int estado, String detalle) {
        ApiResponse respuesta = switch (estado) {
            case 400 -> new ApiResponse(ApiMensajes.CODIGO_SOLICITUD_INVALIDA, ApiMensajes.MENSAJE_SOLICITUD_INVALIDA, detalle);
            case 404 -> new ApiResponse(ApiMensajes.CODIGO_NO_ENCONTRADO, ApiMensajes.MENSAJE_NO_ENCONTRADO, detalle);
            case 409 -> new ApiResponse(ApiMensajes.CODIGO_CONFLICTO, ApiMensajes.MENSAJE_CONFLICTO, detalle);
            case 422 -> new ApiResponse(ApiMensajes.CODIGO_DATOS_INVALIDOS, ApiMensajes.MENSAJE_DATOS_INVALIDOS, detalle);
            default -> new ApiResponse(ApiMensajes.CODIGO_ERROR_INTERNO, ApiMensajes.MENSAJE_ERROR_INTERNO, detalle);
        };
        return Response.status(estado).type(MediaType.APPLICATION_JSON).entity(respuesta).build();
    }
}
