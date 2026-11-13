package boundary.rest;

import boundary.rest.dto.ProductoDTO;
import boundary.rest.dto.ProductoSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.ProductoDAO;
import entity.Producto;
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
 * CRUD REST de Producto basado en ProductoDAO. No serializa entidades JPA ni colecciones JPA.
 * Requiere autorizacion de personal antes de publicarse en Internet.
 */
@Path("/productos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProductoResource {

    private static final Logger LOGGER = Logger.getLogger(ProductoResource.class.getName());
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Inject private ProductoDAO productoDAO;

    /** Lista productos sin sus relaciones asociadas. */
    @GET
    public Response listar() {
        try {
            List<ProductoDTO> salida = new ArrayList<>();
            List<Producto> productos = productoDAO.findAll();
            if (productos != null) {
                for (Producto producto : productos) {
                    salida.add(new ProductoDTO(producto));
                }
            }
            return Response.ok(salida).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar los productos");
        }
    }

    /** Busca un producto por UUID. */
    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de producto no es un UUID valido");
        }
        try {
            Producto producto = productoDAO.findById(uuid);
            if (producto == null) return error(404, "El producto no existe");
            return Response.ok(new ProductoDTO(producto)).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar el producto");
        }
    }

    /** Crea un producto; el UUID lo asigna exclusivamente el servidor. */
    @POST
    public Response crear(String json) {
        ProductoSolicitud solicitud;
        try {
            solicitud = ProductoSolicitud.leer(json);
        } catch (ProductoSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        Producto producto = construir(UUID.randomUUID(), solicitud);
        try {
            productoDAO.crear(producto);
            return Response.status(Response.Status.CREATED).entity(new ProductoDTO(producto)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "crear el producto");
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
            return error(400, "El identificador de producto no es un UUID valido");
        }
        ProductoSolicitud solicitud;
        try {
            solicitud = ProductoSolicitud.leer(json);
        } catch (ProductoSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        try {
            if (productoDAO.findById(uuid) == null) return error(404, "El producto no existe");
            Producto producto = construir(uuid, solicitud);
            productoDAO.modificar(producto);
            return Response.ok(new ProductoDTO(producto)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "modificar el producto");
        }
    }

    /** Elimina solo un producto sin relaciones; el DAO verifica nuevamente la regla. */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de producto no es un UUID valido");
        }
        try {
            Producto existente = productoDAO.findById(uuid);
            if (existente == null) return error(404, "El producto no existe");
            if (productoDAO.countRelaciones(uuid) > 0) {
                return error(409, "No se puede eliminar un producto con relaciones asociadas");
            }
            productoDAO.eliminar(existente);
            return Response.ok(new ApiResponse(
                    ApiMensajes.CODIGO_OK, ApiMensajes.MENSAJE_OK,
                    "Producto eliminado correctamente")).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "eliminar el producto");
        }
    }

    private static Producto construir(UUID id, ProductoSolicitud solicitud) {
        Producto producto = new Producto(id);
        producto.setPrecioSugerido(solicitud.getPrecioSugerido());
        producto.setNombre(solicitud.getNombre());
        producto.setActivo(solicitud.getActivo());
        producto.setComentarios(solicitud.getComentarios());
        return producto;
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

    /** Solo transforma reglas reconocidas de ProductoDAO; no filtra errores SQL. */
    private Response errorOperacion(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        int profundidad = 0;
        for (Throwable actual = ex; actual != null && profundidad++ < 16;
                actual = actual.getCause()) {
            if (actual instanceof SQLException sql) {
                String estado = sql.getSQLState();
                if (estado != null && estado.startsWith("23")) {
                    return error(409, "El producto tiene datos relacionados que impiden la operacion");
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
        if (mensaje.equals("El producto ya no existe")
                || mensaje.equals("El producto que intenta modificar no existe")) {
            return error(404, "El producto no existe");
        }
        if (mensaje.equals("No se puede eliminar un producto que tiene relaciones asociadas")
                || mensaje.equals("Ya existe un producto con ese identificador")) {
            return error(409, mensaje);
        }
        if (mensaje.equals("El producto no puede ser nulo")
                || mensaje.equals("El identificador del producto es obligatorio")
                || mensaje.equals("Debe ingresar el nombre del producto")
                || mensaje.equals("El nombre del producto no puede superar 155 caracteres")
                || mensaje.equals("Debe indicar el estado del producto")
                || mensaje.equals("Debe ingresar el precio sugerido del producto")
                || mensaje.equals("El precio sugerido no puede ser negativo")) {
            return error(422, mensaje);
        }
        return null;
    }

    private Response errorTecnico(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        return error(500, "No fue posible " + operacion);
    }
}
