package boundary.rest;

import boundary.rest.dto.DescuentoProductoDTO;
import boundary.rest.dto.DescuentoProductoSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.DescuentoProductoDAO;
import entity.Descuento;
import entity.DescuentoProducto;
import entity.Producto;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
 * REST de asociaciones DescuentoProducto.
 * Cada respuesta incluye solo IDs, porcentaje, vigencia y observaciones;
 * no serializa colecciones ni objetos JPA con relaciones circulares.
 *
 * Reutiliza DescuentoProductoDAO: no duplica sus validaciones transaccionales.
 * Los endpoints son administrativos y requieren autorizacion antes de publicar.
 */
@Path("/descuento-productos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DescuentoProductoResource {

    private static final Logger LOGGER = Logger.getLogger(DescuentoProductoResource.class.getName());
    // UUID canonico: 8-4-4-4-12, cinco grupos.
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");

    @Inject
    private DescuentoProductoDAO descuentoProductoDAO;

    /** GET /api/descuento-productos. */
    @GET
    public Response listar() {
        try {
            return Response.ok(convertir(descuentoProductoDAO.findAll())).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar las asociaciones de descuentos");
        }
    }

    /** GET /api/descuento-productos/descuentos/{idDescuento}. */
    @GET
    @Path("/descuentos/{idDescuento}")
    public Response listarPorDescuento(@PathParam("idDescuento") String idDescuento) {
        UUID id;
        try { id = uuid(idDescuento); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de descuento no es válido"); }
        try {
            return Response.ok(convertir(descuentoProductoDAO.findByIdDescuento(id))).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar los productos del descuento");
        }
    }

    /** GET /api/descuento-productos/productos/{idProducto}. */
    @GET
    @Path("/productos/{idProducto}")
    public Response listarPorProducto(@PathParam("idProducto") String idProducto) {
        UUID id;
        try { id = uuid(idProducto); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de producto no es válido"); }
        try {
            return Response.ok(convertir(descuentoProductoDAO.findByIdProducto(id))).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar los descuentos del producto");
        }
    }

    /** GET /api/descuento-productos/{id}. */
    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") String id) {
        UUID identificador;
        try { identificador = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de asociación no es válido"); }
        try {
            DescuentoProducto relacion = descuentoProductoDAO.findById(identificador);
            return relacion == null ? error(404, "La asociación no existe")
                    : Response.ok(new DescuentoProductoDTO(relacion)).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar la asociación");
        }
    }

    /**
     * POST /api/descuento-productos. Crea una combinacion producto-descuento.
     * El DAO verifica producto activo, fechas, tipo, maximo y duplicados.
     */
    @POST
    public Response crear(String json) {
        DescuentoProductoSolicitud solicitud;
        try { solicitud = DescuentoProductoSolicitud.leer(json); }
        catch (DescuentoProductoSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        DescuentoProducto relacion = new DescuentoProducto(UUID.randomUUID());
        relacion.setIdDescuento(new Descuento(solicitud.getIdDescuento()));
        relacion.setIdProducto(new Producto(solicitud.getIdProducto()));
        relacion.setFechaDesde(solicitud.getFechaDesde());
        relacion.setFechaHasta(solicitud.getFechaHasta());
        relacion.setValor(solicitud.getValor());
        relacion.setObservaciones(solicitud.getObservaciones());
        try {
            descuentoProductoDAO.crear(relacion);
            return Response.status(Response.Status.CREATED)
                    .entity(new DescuentoProductoDTO(relacion)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "crear la asociación");
        }
    }

    /**
     * DELETE /api/descuento-productos/{id}: elimina solo la asociacion.
     * No elimina ni el descuento principal ni el producto.
     */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID identificador;
        try { identificador = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de asociación no es válido"); }
        try {
            DescuentoProducto relacion = descuentoProductoDAO.findById(identificador);
            if (relacion == null) return error(404, "La asociación no existe");
            descuentoProductoDAO.eliminar(relacion);
            return Response.ok(new ApiResponse(ApiMensajes.CODIGO_OK,
                    ApiMensajes.MENSAJE_OK, "Asociación eliminada correctamente")).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "eliminar la asociación");
        }
    }

    private static List<DescuentoProductoDTO> convertir(List<DescuentoProducto> origen) {
        List<DescuentoProductoDTO> salida = new ArrayList<>();
        if (origen != null) {
            for (DescuentoProducto relacion : origen) {
                salida.add(new DescuentoProductoDTO(relacion));
            }
        }
        return salida;
    }

    private static UUID uuid(String texto) {
        if (texto == null || !UUID_CANONICO.matcher(texto).matches()) {
            throw new IllegalArgumentException("UUID inválido");
        }
        return UUID.fromString(texto);
    }

    /** Errores coherentes con todas las API del proyecto. */
    private static Response error(int estado, String detalle) {
        String codigo, mensaje;
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

    /** Desenvuelve errores EJB, pero no filtra detalles SQL a la respuesta. */
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
        if (mensaje.equals("La asociación ya no existe") || mensaje.equals("La entidad no existe")) {
            return error(404, "La asociación no existe");
        }
        if (mensaje.equals("La asociación ya existe")
                || mensaje.equals("Este descuento ya está asociado al producto")) {
            return error(409, mensaje);
        }
        if (mensaje.equals("El producto seleccionado no existe")
                || mensaje.equals("El descuento seleccionado no existe")
                || mensaje.equals("No se pueden asociar descuentos a un producto inactivo")
                || mensaje.equals("El descuento no tiene un tipo válido")
                || mensaje.equals("No se pudo determinar el límite del tipo de descuento")
                || mensaje.equals("El descuento no tiene fecha inicial")
                || mensaje.startsWith("El descuento aplicado no puede superar ")
                || mensaje.startsWith("La fecha ")
                || mensaje.equals("Las fechas están fuera de la vigencia del descuento")
                || mensaje.equals("Debe indicar la fecha desde")
                || mensaje.equals("Debe indicar la fecha hasta para este descuento")
                || mensaje.equals("El descuento debe ser mayor que 0 %")
                || mensaje.equals("Debe seleccionar un producto válido")
                || mensaje.equals("Debe seleccionar un descuento válido")
                || mensaje.equals("La asociación debe tener un identificador")) {
            return error(422, mensaje);
        }
        return null;
    }

    private Response errorTecnico(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        return error(500, "No fue posible " + operacion);
    }
}
