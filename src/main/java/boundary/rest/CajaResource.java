package boundary.rest;

import boundary.rest.dto.CajaDTO;
import boundary.rest.dto.CajaSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.CajaDAO;
import entity.Caja;
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
 * CRUD REST de Caja basado en CajaDAO. No serializa entidades JPA ni facturaCollection.
 * Requiere autorizacion de personal antes de publicarse en Internet.
 */
@Path("/cajas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CajaResource {

    private static final Logger LOGGER = Logger.getLogger(CajaResource.class.getName());
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Inject private CajaDAO cajaDAO;

    /** Lista cajas sin sus facturas asociadas. */
    @GET
    public Response listar() {
        try {
            List<CajaDTO> salida = new ArrayList<>();
            List<Caja> cajas = cajaDAO.findAll();
            if (cajas != null) {
                for (Caja caja : cajas) {
                    salida.add(new CajaDTO(caja));
                }
            }
            return Response.ok(salida).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar las cajas");
        }
    }

    /** Busca una caja por UUID. */
    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de caja no es un UUID valido");
        }
        try {
            Caja caja = cajaDAO.findById(uuid);
            if (caja == null) return error(404, "La caja no existe");
            return Response.ok(new CajaDTO(caja)).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar la caja");
        }
    }

    /** Crea una caja; el UUID lo asigna exclusivamente el servidor. */
    @POST
    public Response crear(String json) {
        CajaSolicitud solicitud;
        try {
            solicitud = CajaSolicitud.leer(json);
        } catch (CajaSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        Caja caja = construir(UUID.randomUUID(), solicitud);
        try {
            cajaDAO.crear(caja);
            return Response.status(Response.Status.CREATED).entity(new CajaDTO(caja)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "crear la caja");
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
            return error(400, "El identificador de caja no es un UUID valido");
        }
        CajaSolicitud solicitud;
        try {
            solicitud = CajaSolicitud.leer(json);
        } catch (CajaSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        try {
            if (cajaDAO.findById(uuid) == null) return error(404, "La caja no existe");
            Caja caja = construir(uuid, solicitud);
            cajaDAO.modificar(caja);
            return Response.ok(new CajaDTO(caja)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "modificar la caja");
        }
    }

    /** Elimina solo una caja sin facturas; el DAO verifica nuevamente la regla. */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de caja no es un UUID valido");
        }
        try {
            Caja existente = cajaDAO.findById(uuid);
            if (existente == null) return error(404, "La caja no existe");
            if (cajaDAO.countFacturas(uuid) > 0) {
                return error(409, "No se puede eliminar una caja con facturas asociadas");
            }
            cajaDAO.eliminar(existente);
            return Response.ok(new ApiResponse(
                    ApiMensajes.CODIGO_OK, ApiMensajes.MENSAJE_OK,
                    "Caja eliminada correctamente")).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "eliminar la caja");
        }
    }

    private static Caja construir(UUID id, CajaSolicitud solicitud) {
        Caja caja = new Caja(id);
        caja.setNombre(solicitud.getNombre());
        caja.setActivo(solicitud.getActivo());
        caja.setObservaciones(solicitud.getObservaciones());
        return caja;
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

    /** Solo transforma reglas reconocidas de CajaDAO; no filtra errores SQL. */
    private Response errorOperacion(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        int profundidad = 0;
        for (Throwable actual = ex; actual != null && profundidad++ < 16;
                actual = actual.getCause()) {
            if (actual instanceof SQLException sql) {
                String estado = sql.getSQLState();
                if (estado != null && estado.startsWith("23")) {
                    return error(409, "La caja tiene datos relacionados que impiden la operacion");
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
        if (mensaje.equals("La caja ya no existe")
                || mensaje.equals("La caja que intenta modificar no existe")) {
            return error(404, "La caja no existe");
        }
        if (mensaje.equals("No se puede eliminar una caja que tiene facturas asociadas")
                || mensaje.equals("Ya existe una caja con ese identificador")) {
            return error(409, mensaje);
        }
        if (mensaje.equals("La caja no puede ser nula")
                || mensaje.equals("El identificador es obligatorio")
                || mensaje.equals("El identificador de la caja no es valido")
                || mensaje.equals("El nombre de la caja es obligatorio")
                || mensaje.equals("El nombre debe tener entre 2 y 155 caracteres")
                || mensaje.equals("Debe indicar el estado de la caja")) {
            return error(422, mensaje);
        }
        return null;
    }

    private Response errorTecnico(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        return error(500, "No fue posible " + operacion);
    }
}
