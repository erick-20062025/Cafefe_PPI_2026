package boundary.rest;

import boundary.rest.dto.ProductoTipoProductoDTO;
import boundary.rest.dto.ProductoTipoProductoSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.ProductoTipoProductoDAO;
import entity.TipoProducto;
import entity.ProductoTipoProducto;
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
 * REST de asociaciones ProductoTipoProducto.
 * Cada respuesta incluye solo IDs, fecha de creacion y observaciones;
 * no serializa colecciones ni objetos JPA con relaciones circulares.
 *
 * Reutiliza ProductoTipoProductoDAO: no duplica sus validaciones transaccionales.
 * Los endpoints son administrativos y requieren autorizacion antes de publicar.
 */
@Path("/producto-tipos-producto")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProductoTipoProductoResource {

    private static final Logger LOGGER = Logger.getLogger(ProductoTipoProductoResource.class.getName());
    // UUID canonico: 8-4-4-4-12, cinco grupos.
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");

    @Inject
    private ProductoTipoProductoDAO productoTipoProductoDAO;

    /** GET /api/producto-tipos-producto. */
    @GET
    public Response listar() {
        try {
            return Response.ok(convertir(productoTipoProductoDAO.findAll())).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar las asociaciones de tipos de producto");
        }
    }

    /** GET /api/producto-tipos-producto/tipos-producto/{idTipoProducto}. */
    @GET
    @Path("/tipos-producto/{idTipoProducto}")
    public Response listarPorTipoProducto(@PathParam("idTipoProducto") String idTipoProducto) {
        UUID id;
        try { id = uuid(idTipoProducto); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de tipo de producto no es válido"); }
        try {
            return Response.ok(convertir(productoTipoProductoDAO.findByIdTipoProducto(id))).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar los productos del tipo de producto");
        }
    }

    /** GET /api/producto-tipos-producto/productos/{idProducto}. */
    @GET
    @Path("/productos/{idProducto}")
    public Response listarPorProducto(@PathParam("idProducto") String idProducto) {
        UUID id;
        try { id = uuid(idProducto); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de producto no es válido"); }
        try {
            return Response.ok(convertir(productoTipoProductoDAO.findByIdProducto(id))).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar los tipos de producto del producto");
        }
    }

    /** GET /api/producto-tipos-producto/{id}. */
    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") String id) {
        UUID identificador;
        try { identificador = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de asociación no es válido"); }
        try {
            ProductoTipoProducto relacion = productoTipoProductoDAO.findById(identificador);
            return relacion == null ? error(404, "La asociación no existe")
                    : Response.ok(new ProductoTipoProductoDTO(relacion)).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar la asociación");
        }
    }

    /**
     * POST /api/producto-tipos-producto. Crea una combinacion producto-tipo.
     * El DAO verifica referencias reales, tipo activo y duplicados.
     */
    @POST
    public Response crear(String json) {
        ProductoTipoProductoSolicitud solicitud;
        try { solicitud = ProductoTipoProductoSolicitud.leer(json); }
        catch (ProductoTipoProductoSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        ProductoTipoProducto relacion = new ProductoTipoProducto(UUID.randomUUID());
        relacion.setIdTipoProducto(new TipoProducto(solicitud.getIdTipoProducto()));
        relacion.setProducto(new Producto(solicitud.getIdProducto()));
        relacion.setFechaCreacion(new java.util.Date());
        relacion.setObservaciones(solicitud.getObservaciones());
        try {
            productoTipoProductoDAO.crear(relacion);
            return Response.status(Response.Status.CREATED)
                    .entity(new ProductoTipoProductoDTO(relacion)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "crear la asociación");
        }
    }

    /**
     * DELETE /api/producto-tipos-producto/{id}: elimina solo la asociacion.
     * No elimina ni el tipo de producto principal ni el producto.
     */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID identificador;
        try { identificador = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de asociación no es válido"); }
        try {
            ProductoTipoProducto relacion = productoTipoProductoDAO.findById(identificador);
            if (relacion == null) return error(404, "La asociación no existe");
            productoTipoProductoDAO.eliminar(relacion);
            return Response.ok(new ApiResponse(ApiMensajes.CODIGO_OK,
                    ApiMensajes.MENSAJE_OK, "Asociación eliminada correctamente")).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "eliminar la asociación");
        }
    }

    private static List<ProductoTipoProductoDTO> convertir(List<ProductoTipoProducto> origen) {
        List<ProductoTipoProductoDTO> salida = new ArrayList<>();
        if (origen != null) {
            for (ProductoTipoProducto relacion : origen) {
                salida.add(new ProductoTipoProductoDTO(relacion));
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
                || mensaje.equals("Este tipo de producto ya está asignado al producto")) {
            return error(409, mensaje);
        }
        if (mensaje.equals("El producto seleccionado no existe")
                || mensaje.equals("El tipo de producto seleccionado no existe")
                || mensaje.equals("No se puede asignar un tipo de producto inactivo")
                || mensaje.equals("Debe seleccionar un producto válido")
                || mensaje.equals("Debe seleccionar un tipo de producto válido")
                || mensaje.equals("La relación producto-tipo no puede ser nula")
                || mensaje.equals("La asociación debe tener un identificador")
                || mensaje.equals("Debe seleccionar una asociación válida")) {
            return error(422, mensaje);
        }
        return null;
    }

    private Response errorTecnico(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        return error(500, "No fue posible " + operacion);
    }
}
