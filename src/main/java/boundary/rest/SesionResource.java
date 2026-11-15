package boundary.rest;

import boundary.rest.messages.ApiResponse;
import boundary.rest.seguridad.AccesoPersonal;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.io.StringReader;
import java.util.Set;
import java.util.UUID;

@Path("/sesion")
@Produces(MediaType.APPLICATION_JSON)
public class SesionResource {
    public static final String ORIGEN = "http://127.0.0.1:5501";
    @Inject AccesoPersonal acceso;
    @Context HttpServletRequest request;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response entrar(String json) {
        if (!ORIGEN.equals(request.getHeader("Origin"))) return error(403, "Origen no permitido.");
        String nombre;
        UUID id;
        try (JsonReader lector = Json.createReader(new StringReader(json == null ? "" : json))) {
            if (json == null || json.length() > 2048) return error(400, "Solicitud inválida.");
            JsonObject datos = lector.readObject();
            if (!datos.keySet().equals(Set.of("nombre", "uuid"))
                    || datos.get("nombre").getValueType() != JsonValue.ValueType.STRING
                    || datos.get("uuid").getValueType() != JsonValue.ValueType.STRING) return error(400, "Solicitud inválida.");
            nombre = datos.getString("nombre").trim();
            String clave = datos.getString("uuid").trim();
            if (nombre.isEmpty() || nombre.length() > 255
                    || !clave.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) return error(400, "Solicitud inválida.");
            id = UUID.fromString(clave);
        } catch (RuntimeException ex) { return error(400, "Solicitud inválida."); }
        try {
            AccesoPersonal.Identidad identidad = acceso.consultar(id);
            if (identidad == null || !nombre.equals(identidad.getNombre())) return error(401, "No se pudo iniciar sesión con esas credenciales.");
            if (identidad.getRoles().isEmpty()) return error(403, "El empleado no tiene acceso habilitado.");
            HttpSession anterior = request.getSession(false);
            if (anterior != null) anterior.invalidate();
            HttpSession nueva = request.getSession(true);
            nueva.setMaxInactiveInterval(30 * 60);
            nueva.setAttribute(AccesoPersonal.ATRIBUTO, id);
            return respuesta(Response.ok(identidad));
        } catch (RuntimeException ex) { return error(503, "No fue posible iniciar sesión. Inténtalo de nuevo."); }
    }

    @GET
    public Response recuperar() {
        try {
            AccesoPersonal.Identidad identidad = acceso.recuperar(request);
            return identidad == null ? error(401, "La sesión no está vigente.") : respuesta(Response.ok(identidad));
        } catch (RuntimeException ex) { return error(503, "No fue posible comprobar la sesión."); }
    }

    @DELETE
    public Response salir() {
        if (!ORIGEN.equals(request.getHeader("Origin"))) return error(403, "Origen no permitido.");
        HttpSession sesion = request.getSession(false);
        if (sesion != null) sesion.invalidate();
        return respuesta(Response.noContent());
    }

    public static Response error(int estado, String mensaje) {
        return respuesta(Response.status(estado).entity(new ApiResponse("AUTH-" + estado, mensaje, null)));
    }
    private static Response respuesta(Response.ResponseBuilder respuesta) {
        return respuesta.header("Cache-Control", "no-store").header("Pragma", "no-cache").build();
    }
}
