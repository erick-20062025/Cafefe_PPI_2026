package boundary.rest;

import boundary.rest.messages.ApiExceptionUtil;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.DescuentoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.TipoDescuento;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
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
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * API REST de tipos de descuento.
 *
 * Valida el contenido de la petición antes de invocar el DAO. Las consultas,
 * persistencia y relaciones continúan siendo responsabilidad de los DAO.
 * No modifica entidades ni sus restricciones, de modo que el módulo JSF
 * existente conserva su funcionamiento.
 *
 * Contrato de esta API:
 * - nombre: obligatorio, 1..150 caracteres útiles;
 * - activo: obligatorio;
 * - descuentoMaximo: obligatorio y no negativo (no se presupone porcentaje);
 * - observaciones: opcionales, hasta 1000 caracteres;
 * - ID en POST: generado por el servidor;
 * - ID en PUT: si se envía también en JSON, debe coincidir con el de la URL.
 */
@Path("/tipo-descuento")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TipoDescuentoResource {

    private static final Logger LOGGER = Logger.getLogger(TipoDescuentoResource.class.getName());
    private static final int MAX_NOMBRE = 150;
    private static final int MAX_OBSERVACIONES = 1000;
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern BORDES_ESPACIOS = Pattern.compile(
            "^[\\p{Z}\\s\\u200B]+|[\\p{Z}\\s\\u200B]+$");

    @Inject
    private TipoDescuentoDAO tipoDescuentoDAO;

    @Inject
    private DescuentoDAO descuentoDAO;

    /** GET /api/tipo-descuento */
    @GET
    public Response findAll() {
        try {
            List<TipoDescuento> lista = tipoDescuentoDAO.findAll();
            return Response.ok(lista == null ? List.of() : lista).build();
        } catch (Exception ex) {
            return errorOperacion(ex, "consultar los tipos de descuento");
        }
    }

    /** GET /api/tipo-descuento/{id} */
    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = convertirUUID(id);
        } catch (IllegalArgumentException ex) {
            return error(Response.Status.BAD_REQUEST, ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                    ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                    "El identificador proporcionado no es un UUID válido");
        }

        try {
            TipoDescuento encontrado = tipoDescuentoDAO.findById(uuid);
            if (encontrado == null) {
                return noEncontrado("No existe el tipo de descuento solicitado");
            }
            return Response.ok(encontrado).build();
        } catch (Exception ex) {
            return errorOperacion(ex, "consultar el tipo de descuento");
        }
    }

    /** POST /api/tipo-descuento */
    @POST
    public Response crear(TipoDescuento registro) {
        if (registro == null) {
            return error(Response.Status.BAD_REQUEST, ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                    ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                    "Debe proporcionar los datos del tipo de descuento");
        }
        if (registro.getIdTipoDescuento() != null) {
            return datosInvalidos("No envíe idTipoDescuento: el servidor genera el identificador");
        }
        String problema = validarCampos(registro);
        if (problema != null) {
            return datosInvalidos(problema);
        }

        try {
            registro.setIdTipoDescuento(UUID.randomUUID());
            tipoDescuentoDAO.crear(registro);
            return Response.status(Response.Status.CREATED)
                    .entity(new ApiResponse(ApiMensajes.CODIGO_CREADO,
                            ApiMensajes.MENSAJE_CREADO,
                            "Tipo de descuento creado con identificador: "
                            + registro.getIdTipoDescuento()))
                    .build();
        } catch (Exception ex) {
            return errorOperacion(ex, "crear el tipo de descuento");
        }
    }

    /** PUT /api/tipo-descuento/{id} */
    @PUT
    @Path("/{id}")
    public Response modificar(@PathParam("id") String id, TipoDescuento registro) {
        UUID uuid;
        try {
            uuid = convertirUUID(id);
        } catch (IllegalArgumentException ex) {
            return error(Response.Status.BAD_REQUEST, ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                    ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                    "El identificador proporcionado no es un UUID válido");
        }
        if (registro == null) {
            return error(Response.Status.BAD_REQUEST, ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                    ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                    "Debe proporcionar los datos del tipo de descuento");
        }
        if (registro.getIdTipoDescuento() != null
                && !uuid.equals(registro.getIdTipoDescuento())) {
            return datosInvalidos("El identificador del JSON no coincide con el de la URL");
        }
        String problema = validarCampos(registro);
        if (problema != null) {
            return datosInvalidos(problema);
        }

        try {
            TipoDescuento encontrado = tipoDescuentoDAO.findById(uuid);
            if (encontrado == null) {
                return noEncontrado("No existe el tipo de descuento que desea modificar");
            }
            // Únicamente modificamos campos permitidos, nunca el identificador.
            encontrado.setNombre(registro.getNombre());
            encontrado.setActivo(registro.getActivo());
            encontrado.setDescuentoMaximo(registro.getDescuentoMaximo());
            encontrado.setObservaciones(registro.getObservaciones());
            tipoDescuentoDAO.modificar(encontrado);
            return Response.ok(new ApiResponse(ApiMensajes.CODIGO_OK,
                    ApiMensajes.MENSAJE_OK,
                    "Tipo de descuento modificado correctamente")).build();
        } catch (Exception ex) {
            return errorOperacion(ex, "modificar el tipo de descuento");
        }
    }

    /** DELETE /api/tipo-descuento/{id} */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = convertirUUID(id);
        } catch (IllegalArgumentException ex) {
            return error(Response.Status.BAD_REQUEST, ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                    ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                    "El identificador proporcionado no es un UUID válido");
        }

        try {
            TipoDescuento encontrado = tipoDescuentoDAO.findById(uuid);
            if (encontrado == null) {
                return noEncontrado("No existe el tipo de descuento que desea eliminar");
            }
            tipoDescuentoDAO.eliminar(encontrado);
            return Response.ok(new ApiResponse(ApiMensajes.CODIGO_OK,
                    ApiMensajes.MENSAJE_OK,
                    "Tipo de descuento eliminado correctamente")).build();
        } catch (Exception ex) {
            return errorOperacion(ex, "eliminar el tipo de descuento");
        }
    }

    /** GET /api/tipo-descuento/{id}/descuentos */
    @GET
    @Path("/{id}/descuentos")
    public Response findDescuentos(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = convertirUUID(id);
        } catch (IllegalArgumentException ex) {
            return error(Response.Status.BAD_REQUEST, ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                    ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                    "El identificador proporcionado no es un UUID válido");
        }

        try {
            if (tipoDescuentoDAO.findById(uuid) == null) {
                return noEncontrado("No existe el tipo de descuento solicitado");
            }
            List<Descuento> descuentos = descuentoDAO.findByTipoDescuento(uuid);
            return Response.ok(descuentos == null ? List.of() : descuentos).build();
        } catch (Exception ex) {
            return errorOperacion(ex, "consultar los descuentos asociados");
        }
    }

    /**
     * No crea clases Validator externas. Se invoca antes de persistir.
     * Normaliza únicamente los campos de texto de la petición.
     * @return descripción segura del primer dato inválido, o null si es válido
     */
    private static String validarCampos(TipoDescuento registro) {
        String nombre = limpiarBordes(registro.getNombre());
        if (nombre == null || nombre.isEmpty()) {
            return "El nombre es obligatorio y no puede contener solo espacios";
        }
        if (nombre.length() > MAX_NOMBRE) {
            return "El nombre no debe superar " + MAX_NOMBRE + " caracteres";
        }
        if (contieneControl(nombre)) {
            return "El nombre contiene caracteres de control no permitidos";
        }
        if (registro.getActivo() == null) {
            return "Debe indicar si el tipo de descuento está activo";
        }
        if (registro.getDescuentoMaximo() == null) {
            return "El descuento máximo es obligatorio";
        }
        if (registro.getDescuentoMaximo() < 0) {
            return "El descuento máximo no puede ser negativo";
        }
        String observaciones = limpiarBordes(registro.getObservaciones());
        if (observaciones != null && observaciones.length() > MAX_OBSERVACIONES) {
            return "Las observaciones no deben superar " + MAX_OBSERVACIONES + " caracteres";
        }
        if (observaciones != null && contieneControlInvalido(observaciones)) {
            return "Las observaciones contienen caracteres de control no permitidos";
        }
        // Solo modificar la entidad después de que todos los campos pasen.
        registro.setNombre(nombre);
        registro.setObservaciones(observaciones == null || observaciones.isEmpty()
                ? null : observaciones);
        return null;
    }

    private static String limpiarBordes(String texto) {
        return texto == null ? null : BORDES_ESPACIOS.matcher(texto).replaceAll("");
    }

    private static boolean contieneControl(String texto) {
        return texto.codePoints().anyMatch(Character::isISOControl);
    }

    private static boolean contieneControlInvalido(String texto) {
        // Se permiten saltos de línea y tabulaciones en observaciones.
        return texto.codePoints().anyMatch(cp -> Character.isISOControl(cp)
                && cp != '\n' && cp != '\r' && cp != '\t');
    }

    private static UUID convertirUUID(String id) {
        if (id == null || !UUID_CANONICO.matcher(id).matches()) {
            throw new IllegalArgumentException("UUID no canónico");
        }
        return UUID.fromString(id);
    }

    private static Response datosInvalidos(String detalle) {
        return Response.status(422)
                .entity(new ApiResponse(ApiMensajes.CODIGO_DATOS_INVALIDOS,
                        ApiMensajes.MENSAJE_DATOS_INVALIDOS, detalle))
                .build();
    }

    private static Response noEncontrado(String detalle) {
        return error(Response.Status.NOT_FOUND,
                ApiMensajes.CODIGO_NO_ENCONTRADO,
                ApiMensajes.MENSAJE_NO_ENCONTRADO, detalle);
    }

    private static Response error(Response.StatusType estado, String codigo,
            String mensaje, String detalle) {
        return Response.status(estado)
                .entity(new ApiResponse(codigo, mensaje, detalle)).build();
    }

    /**
     * Solo un error de integridad SQL conocido se interpreta como conflicto.
     * Fallas de conexión/transacción y errores desconocidos son 500, no 409.
     */
    private Response errorOperacion(Exception ex, String accion) {
        if (ApiExceptionUtil.contieneCausa(ex, PersistenceException.class)
                || ex instanceof PersistenceException) {
            if (existeSqlStateDeIntegridad(ex)) {
                LOGGER.log(Level.WARNING, "Conflicto de integridad al " + accion, ex);
                return error(Response.Status.CONFLICT,
                        ApiMensajes.CODIGO_CONFLICTO,
                        ApiMensajes.MENSAJE_CONFLICTO,
                        "La operación no puede completarse por una restricción de los datos");
            }
        }
        LOGGER.log(Level.SEVERE, "Error al " + accion, ex);
        return error(Response.Status.INTERNAL_SERVER_ERROR,
                ApiMensajes.CODIGO_ERROR_INTERNO,
                ApiMensajes.MENSAJE_ERROR_INTERNO,
                "No fue posible " + accion);
    }

    private static boolean existeSqlStateDeIntegridad(Throwable ex) {
        Throwable actual = ex;
        for (int profundidad = 0; actual != null && profundidad < 30;
                actual = actual.getCause(), profundidad++) {
            if (actual instanceof SQLException) {
                String estado = ((SQLException) actual).getSQLState();
                if (estado != null && estado.startsWith("23")) {
                    return true;
                }
            }
        }
        return false;
    }
}
