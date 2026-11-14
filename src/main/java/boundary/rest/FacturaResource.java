package boundary.rest;

import boundary.rest.dto.FacturaDTO;
import boundary.rest.dto.FacturaSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.FacturaDAO;
import control.FacturaOrdenProductoDAO;
import entity.Caja;
import entity.EmpleadoRol;
import entity.Factura;
import entity.FacturaOrdenProducto;
import entity.OrdenProducto;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/facturas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FacturaResource {
    private static final Logger LOGGER = Logger.getLogger(FacturaResource.class.getName());
    @Inject private FacturaDAO facturaDAO;
    @Inject private FacturaOrdenProductoDAO detalleDAO;

    @GET
    public Response listar() {
        return ejecutar(() -> Response.ok(facturaDAO.findAll().stream()
                .map(factura -> new FacturaDTO(factura, null)).toList()).build());
    }

    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") String id) {
        return ejecutar(() -> Response.ok(consultar(FacturaSolicitud.uuid(id))).build());
    }

    @POST
    public Response crear(String json) {
        return ejecutar(() -> guardar(UUID.randomUUID(), FacturaSolicitud.leer(json, true), true));
    }

    @PUT
    @Path("/{id}")
    public Response modificar(@PathParam("id") String id, String json) {
        return ejecutar(() -> guardar(FacturaSolicitud.uuid(id), FacturaSolicitud.leer(json, false), false));
    }

    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        return ejecutar(() -> {
            facturaDAO.eliminarFacturaCompleta(new Factura(FacturaSolicitud.uuid(id)));
            return Response.ok(new ApiResponse(ApiMensajes.CODIGO_OK, ApiMensajes.MENSAJE_OK,
                    "Factura eliminada correctamente")).build();
        });
    }

    private FacturaDTO consultar(UUID id) {
        Factura factura = facturaDAO.findById(id);
        if (factura == null) throw new FacturaDAO.NoEncontrada();
        return new FacturaDTO(factura, detalleDAO.findByIdFactura(id));
    }

    private Response guardar(UUID id, FacturaSolicitud solicitud, boolean nuevo) {
        Factura factura = new Factura(id);
        factura.setIdCaja(new Caja(solicitud.idCaja()));
        factura.setIdEmpleadoRol(new EmpleadoRol(solicitud.idEmpleadoRol()));
        factura.setObservaciones(solicitud.observaciones());
        List<FacturaOrdenProducto> detalles = solicitud.productos().stream().map(producto -> {
            FacturaOrdenProducto detalle = new FacturaOrdenProducto();
            detalle.setIdOrdenProducto(new OrdenProducto(producto.idOrdenProducto()));
            detalle.setPrecio(producto.precio());
            detalle.setObservaciones(producto.observaciones());
            return detalle;
        }).toList();
        if (nuevo) {
            factura.setCliente(solicitud.cliente());
            factura.setFechaFacturacion(new Date());
            factura.setEstado("PENDIENTE");
            facturaDAO.crearFacturaCompleta(factura, detalles);
            return Response.status(201).entity(new FacturaDTO(factura, detalles)).build();
        }
        facturaDAO.modificarFacturaCompleta(factura, detalles);
        return Response.ok(consultar(id)).build();
    }

    private Response ejecutar(Supplier<Response> operacion) {
        try {
            return operacion.get();
        } catch (FacturaSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        } catch (RuntimeException ex) {
            int nivel = 0;
            for (Throwable causa = ex; causa != null && nivel++ < 16; causa = causa.getCause()) {
                if (causa instanceof FacturaDAO.NoEncontrada) return error(404, causa.getMessage());
                if (causa instanceof FacturaDAO.Conflicto) return error(409, causa.getMessage());
                if (causa instanceof SQLException sql && sql.getSQLState() != null
                        && sql.getSQLState().startsWith("23")) {
                    return error(409, "Conflicto de integridad de la factura");
                }
                if (causa instanceof FacturaDAO.DatosInvalidos) return error(422, causa.getMessage());
            }
            LOGGER.log(Level.SEVERE, "Error al gestionar la factura", ex);
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
