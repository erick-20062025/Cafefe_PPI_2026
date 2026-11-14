package boundary.rest;

import boundary.rest.dto.ProductoCaracteristicaDTO;
import boundary.rest.dto.ProductoCaracteristicaSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.ProductoCaracteristicaDAO;
import entity.ProductoCaracteristica;
import entity.Caracteristica;
import entity.Producto;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/producto-caracteristicas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProductoCaracteristicaResource {
    private static final Logger LOGGER = Logger.getLogger(ProductoCaracteristicaResource.class.getName());
    @Inject private ProductoCaracteristicaDAO dao;

    @GET
    public Response listar() {
        return ejecutar(() -> Response.ok(dao.findAll().stream().map(ProductoCaracteristicaDTO::new).toList()).build());
    }

    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") String id) {
        return ejecutar(() -> {
            ProductoCaracteristica entidad = dao.findById(ProductoCaracteristicaSolicitud.uuid(id));
            return entidad == null ? error(404, "El registro no existe")
                    : Response.ok(new ProductoCaracteristicaDTO(entidad)).build();
        });
    }

    @POST
    public Response crear(String json) {
        return ejecutar(() -> guardar(UUID.randomUUID(), ProductoCaracteristicaSolicitud.leer(json)));
    }

    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        return ejecutar(() -> {
            ProductoCaracteristica entidad = dao.findById(ProductoCaracteristicaSolicitud.uuid(id));
            if (entidad == null) return error(404, "El registro no existe");
            dao.eliminar(entidad);
            return Response.ok(new ApiResponse(ApiMensajes.CODIGO_OK, ApiMensajes.MENSAJE_OK,
                    "Registro eliminado correctamente")).build();
        });
    }

    @GET
    @Path("/productos/{idProducto}")
    public Response listarPorProducto(@PathParam("idProducto") String id) {
        return ejecutar(() -> Response.ok(dao.findByIdProducto(ProductoCaracteristicaSolicitud.uuid(id))
                .stream().map(ProductoCaracteristicaDTO::new).toList()).build());
    }

    private Response guardar(UUID id, ProductoCaracteristicaSolicitud solicitud) {
        ProductoCaracteristica entidad = new ProductoCaracteristica(id);
        entidad.setIdProducto(new Producto(solicitud.idProducto()));
        entidad.setIdCaracteristica(new Caracteristica(solicitud.idCaracteristica()));
        entidad.setValor(solicitud.valor());
        dao.crear(entidad);
        return Response.status(201).entity(new ProductoCaracteristicaDTO(entidad)).build();
    }

    private Response ejecutar(Supplier<Response> operacion) {
        try {
            return operacion.get();
        } catch (ProductoCaracteristicaSolicitud.DatoInvalido ex) {
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
                    case "La característica ya está asociada al producto" -> error(409, mensaje);
                    case "La asociación no existe" -> error(404, mensaje);
                    case "La relación producto-característica no puede ser nula", "Debe seleccionar un producto válido",
                         "Debe seleccionar una característica válida", "El producto no existe",
                         "La característica no existe", "No se puede asignar una característica inactiva",
                         "Debe ingresar un valor para la característica",
                         "El valor ingresado no cumple con el formato requerido",
                         "La expresión regular configurada no es válida" -> error(422, mensaje);
                    default -> null;
                };
                if (respuesta != null) return respuesta;
            }
            LOGGER.log(Level.SEVERE, "Error al gestionar producto-caracteristicas", ex);
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
