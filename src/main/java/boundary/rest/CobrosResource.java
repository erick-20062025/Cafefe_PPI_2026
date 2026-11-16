package boundary.rest;

import boundary.rest.dto.FacturaSolicitud;
import boundary.rest.dto.OrdenDTO;
import boundary.rest.messages.ApiResponse;
import boundary.rest.seguridad.AccesoPersonal;
import control.EmpleadoRolDAO;
import control.OrdenDAO;
import entity.EmpleadoRol;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.StringReader;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/cobros")
@Produces(MediaType.APPLICATION_JSON)
public class CobrosResource {

    private static final Logger LOGGER = Logger.getLogger(CobrosResource.class.getName());
    private static final Set<String> CAMPOS_FACTURA = Set.of("idCaja", "cliente");

    @Inject AccesoPersonal acceso;
    @Inject OrdenDAO ordenDAO;
    @Inject EmpleadoRolDAO empleadoRolDAO;
    @Inject CajaResource cajas;
    @Inject FacturaResource facturas;
    @Inject PagoResource pagos;
    @Context HttpServletRequest request;

    @GET
    @Path("/ordenes-pendientes")
    public Response pendientes() {
        return conCajero(false, empleado -> Response.ok(ordenDAO.findPendientesFacturar().stream()
                .map(OrdenDTO::resumen).toList()).build());
    }

    @GET
    @Path("/ordenes/{id}/productos-facturables")
    public Response productosFacturables(@PathParam("id") String id) {
        return conCajero(false, empleado -> {
            UUID orden = FacturaSolicitud.uuid(id);
            if (ordenDAO.findById(orden) == null) return error(404, "La orden no existe.");
            return Response.ok(ordenDAO.findProductosFacturables(orden).stream()
                    .map(OrdenDTO.ProductoDTO::desde).toList()).build();
        });
    }

    @POST
    @Path("/ordenes/{id}/facturas")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response crearFactura(@PathParam("id") String id, String json) {
        return conCajero(true, empleado -> {
            UUID orden = FacturaSolicitud.uuid(id);
            JsonObject solicitud = leerFactura(json);
            UUID cajero = empleadoRolDAO.findByIdEmpleado(empleado).stream()
                    .filter(relacion -> Boolean.TRUE.equals(relacion.getActivo())
                            && Boolean.TRUE.equals(relacion.getIdRol().getActivo())
                            && relacion.getIdRol().getNombre() != null
                            && "cajero".equalsIgnoreCase(relacion.getIdRol().getNombre().trim()))
                    .map(EmpleadoRol::getIdEmpleadoRol).sorted().findFirst().orElse(null);
            if (cajero == null) return error(403, "La asignación de cajero ya no está activa.");
            if (ordenDAO.findById(orden) == null) return error(404, "La orden no existe.");

            var filas = ordenDAO.findProductosFacturables(orden);
            if (filas.isEmpty()) {
                return error(409, "La orden ya no tiene productos por facturar. Actualizá Cobros.");
            }
            var productos = Json.createArrayBuilder();
            for (var fila : filas) {
                if (fila.getPrecio() == null) return error(422, "La orden contiene un precio inválido.");
                productos.add(Json.createObjectBuilder()
                        .add("idOrdenProducto", fila.getIdOrdenProducto().toString())
                        .add("precio", fila.getPrecio()));
            }
            String factura = Json.createObjectBuilder(solicitud)
                    .add("idEmpleadoRol", cajero.toString())
                    .add("productos", productos)
                    .build().toString();
            return facturas.crear(factura);
        });
    }

    @GET
    @Path("/cajas")
    public Response cajas() {
        return conCajero(false, empleado -> cajas.listar());
    }

    @GET
    @Path("/facturas/pendientes")
    public Response facturasPendientes() {
        return conCajero(false, empleado -> pagos.pendientes());
    }

    @GET
    @Path("/facturas/pagadas")
    public Response facturasPagadas() {
        return conCajero(false, empleado -> pagos.pagadas());
    }

    @GET
    @Path("/facturas/{id}")
    public Response factura(@PathParam("id") String id) {
        return conCajero(false, empleado -> facturas.obtener(id));
    }

    @GET
    @Path("/facturas/{id}/resumen")
    public Response resumen(@PathParam("id") String id) {
        return conCajero(false, empleado -> pagos.resumen(id));
    }

    @GET
    @Path("/facturas/{id}/abonos")
    public Response abonos(@PathParam("id") String id) {
        return conCajero(false, empleado -> pagos.abonos(id));
    }

    @POST
    @Path("/facturas/{id}/abonos")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registrarAbono(@PathParam("id") String id, String json) {
        return conCajero(true, empleado -> pagos.registrarAbono(id, json));
    }

    private static JsonObject leerFactura(String json) {
        if (json == null || json.isBlank() || json.length() > 8192) {
            throw new FacturaSolicitud.DatoInvalido(400, "Debe enviar un objeto JSON de hasta 8192 caracteres.");
        }
        try (var lector = Json.createReader(new StringReader(json))) {
            if (!(lector.readValue() instanceof JsonObject objeto) || !CAMPOS_FACTURA.containsAll(objeto.keySet())) {
                throw new FacturaSolicitud.DatoInvalido(400, "Solo se admiten los campos idCaja y cliente.");
            }
            return objeto;
        } catch (JsonException ex) {
            throw new FacturaSolicitud.DatoInvalido(400, "El cuerpo contiene JSON inválido.");
        }
    }

    private Response conCajero(boolean escritura, Function<UUID, Response> operacion) {
        if (escritura && !SesionResource.ORIGEN.equals(request.getHeader("Origin"))) {
            return error(403, "Origen no permitido.");
        }
        try {
            var sesion = request.getSession(false);
            if (sesion == null || !(sesion.getAttribute(AccesoPersonal.ATRIBUTO) instanceof UUID empleado)) {
                return error(401, "Iniciá sesión para acceder a Cobros.");
            }
            AccesoPersonal.Identidad identidad = acceso.recuperar(request);
            if (identidad == null) return error(401, "Iniciá sesión para acceder a Cobros.");
            if (!identidad.getRoles().contains("cajero")) {
                return error(403, "Solo el cajero puede acceder a Cobros.");
            }
            Response respuesta = operacion.apply(empleado);
            respuesta.getHeaders().putSingle("Cache-Control", "no-store");
            return respuesta;
        } catch (FacturaSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Error en Cobros", ex);
            return error(500, "No se pudo completar la operación de Cobros.");
        }
    }

    private static Response error(int estado, String mensaje) {
        return Response.status(estado).entity(new ApiResponse("COBROS-" + estado, mensaje, null))
                .header("Cache-Control", "no-store").build();
    }
}
