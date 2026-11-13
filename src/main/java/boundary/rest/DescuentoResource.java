package boundary.rest;

import boundary.rest.dto.DescuentoDTO;
import boundary.rest.dto.DescuentoSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.DescuentoDAO;
import control.DescuentoProductoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.TipoDescuento;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * CRUD REST de descuentos. Reutiliza los DAO existentes y no devuelve
 * DescuentoProducto para evitar relaciones JSON circulares.
 *
 * Mantiene las reglas de la pantalla: tipo activo al crear; tipo inmutable al
 * editar; fechas pasadas permitidas solo para modificar históricos.
 * API administrativa: requiere autorización antes de publicarse en Internet.
 */
@Path("/descuentos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DescuentoResource {

    private static final Logger LOGGER = Logger.getLogger(DescuentoResource.class.getName());
    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Inject private DescuentoDAO descuentoDAO;
    @Inject private TipoDescuentoDAO tipoDescuentoDAO;
    @Inject private DescuentoProductoDAO descuentoProductoDAO;

    /** Listado compacto de descuentos, sin sus asociaciones de productos. */
    @GET
    public Response listar() {
        try {
            List<DescuentoDTO> salida = new ArrayList<>();
            List<Descuento> descuentos = descuentoDAO.findAll();
            if (descuentos != null) {
                for (Descuento descuento : descuentos) salida.add(new DescuentoDTO(descuento));
            }
            return Response.ok(salida).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar los descuentos");
        }
    }

    /** Consulta un descuento individual; no carga DescuentoProducto. */
    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") String id) {
        UUID uuid;
        try { uuid = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de descuento no es un UUID válido"); }
        try {
            Descuento descuento = descuentoDAO.findById(uuid);
            return descuento == null ? error(404, "El descuento no existe")
                    : Response.ok(new DescuentoDTO(descuento)).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar el descuento");
        }
    }

    /**
     * Crea un descuento. La selección de tipos se obtiene de la API ya existente
     * /api/tipo-descuento, sin crear otro endpoint de catálogo.
     */
    @POST
    public Response crear(String json) {
        DescuentoSolicitud solicitud;
        try { solicitud = DescuentoSolicitud.leer(json); }
        catch (DescuentoSolicitud.DatoInvalido ex) { return error(ex.getEstadoHttp(), ex.getMessage()); }

        if (solicitud.getFechaDesde().toInstant().atZone(ZONA).toLocalDate()
                .isBefore(LocalDate.now(ZONA))) {
            return error(422, "La fecha desde no puede ser anterior a hoy");
        }
        try {
            TipoDescuento tipo = tipoDescuentoDAO.findById(solicitud.getIdTipoDescuento());
            if (tipo == null) return error(422, "El tipo de descuento seleccionado no existe");
            if (!Boolean.TRUE.equals(tipo.getActivo())) {
                return error(422, "No se puede asignar un tipo de descuento inactivo");
            }
            Descuento descuento = construir(UUID.randomUUID(), solicitud);
            descuentoDAO.crear(descuento);
            return Response.status(Response.Status.CREATED).entity(new DescuentoDTO(descuento)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "crear el descuento");
        }
    }

    /**
     * Actualiza los campos editables. El tipo asociado no se puede cambiar;
     * sí pueden modificarse descuentos históricos con fechas pasadas.
     */
    @PUT
    @Path("/{id}")
    public Response modificar(@PathParam("id") String id, String json) {
        UUID uuid;
        try { uuid = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de descuento no es un UUID válido"); }
        DescuentoSolicitud solicitud;
        try { solicitud = DescuentoSolicitud.leer(json); }
        catch (DescuentoSolicitud.DatoInvalido ex) { return error(ex.getEstadoHttp(), ex.getMessage()); }
        try {
            Descuento anterior = descuentoDAO.findById(uuid);
            if (anterior == null) return error(404, "El descuento no existe");
            if (!solicitud.getIdTipoDescuento().equals(anterior.getIdTipoDescuento())) {
                return error(409, "No se puede cambiar el tipo de un descuento existente");
            }
            Descuento descuento = construir(uuid, solicitud);
            descuentoDAO.modificar(descuento);
            return Response.ok(new DescuentoDTO(descuento)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "modificar el descuento");
        }
    }

    /** Elimina un descuento solo si no tiene relaciones con productos. */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID uuid;
        try { uuid = uuid(id); }
        catch (IllegalArgumentException ex) { return error(400, "El identificador de descuento no es un UUID válido"); }
        try {
            Descuento descuento = descuentoDAO.findById(uuid);
            if (descuento == null) return error(404, "El descuento no existe");
            // Evita el 500 genérico que produce una restricción FK al borrar.
            if (!descuentoProductoDAO.findByIdDescuento(uuid).isEmpty()) {
                return error(409, "No se puede eliminar un descuento asociado a productos");
            }
            descuentoDAO.eliminar(descuento);
            return Response.ok(new ApiResponse(ApiMensajes.CODIGO_OK,
                    ApiMensajes.MENSAJE_OK, "Descuento eliminado correctamente")).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "eliminar el descuento");
        }
    }

    private static Descuento construir(UUID id, DescuentoSolicitud solicitud) {
        Descuento descuento = new Descuento(id);
        descuento.setIdTipoDescuento(solicitud.getIdTipoDescuento());
        descuento.setNombre(solicitud.getNombre());
        descuento.setFechaDesde(solicitud.getFechaDesde());
        descuento.setFechaHasta(solicitud.getFechaHasta());
        descuento.setObservaciones(solicitud.getObservaciones());
        return descuento;
    }

    private static UUID uuid(String valor) {
        if (valor == null || !UUID_CANONICO.matcher(valor).matches()) {
            throw new IllegalArgumentException("UUID inválido");
        }
        return UUID.fromString(valor);
    }

    /** Mantiene los códigos y mensajes comunes de todas las API REST. */
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
        switch (mensaje) {
            case "La entidad no existe", "El descuento no existe",
                 "El descuento ya no existe" -> {
                return error(404, "El descuento no existe");
            }
            case "Ya existe un descuento con ese identificador" -> {
                return error(409, mensaje);
            }
            case "El nombre de descuento es obligatorio", "El nombre es obligatorio",
                 "La fecha desde es obligatoria", "Debe seleccionar un tipo de descuento" -> {
                return error(422, mensaje);
            }
            default -> { return null; }
        }
    }

    private Response errorTecnico(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        return error(500, "No fue posible " + operacion);
    }
}
