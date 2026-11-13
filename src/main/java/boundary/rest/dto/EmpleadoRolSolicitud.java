package boundary.rest.dto;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validaciones de entrada JSON sin conversiones implicitas de JSON-B.
 * En POST exige empleado, rol y activo; en PUT solo admite activo y observaciones:
 * los identificadores de una asignacion existente NO son editables.
 */
public final class EmpleadoRolSolicitud {
    private static final int MAX_JSON = 8192;
    private static final int MAX_OBSERVACIONES = 1000;
    private static final Set<String> CAMPOS_CREAR =
            Set.of("idEmpleado", "idRol", "activo", "observaciones");
    private static final Set<String> CAMPOS_MODIFICAR =
            Set.of("activo", "observaciones");
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final UUID idEmpleado;
    private final UUID idRol;
    private final Boolean activo;
    private final String observaciones;

    private EmpleadoRolSolicitud(UUID idEmpleado, UUID idRol, Boolean activo,
            String observaciones) {
        this.idEmpleado = idEmpleado;
        this.idRol = idRol;
        this.activo = activo;
        this.observaciones = observaciones;
    }

    public static EmpleadoRolSolicitud crear(String json) {
        return leer(json, true);
    }

    public static EmpleadoRolSolicitud modificar(String json) {
        return leer(json, false);
    }

    private static EmpleadoRolSolicitud leer(String json, boolean crear) {
        if (json == null || json.isBlank()) {
            throw new DatoInvalido(400, "Debe enviar un objeto JSON");
        }
        if (json.length() > MAX_JSON) {
            throw new DatoInvalido(400, "El cuerpo JSON supera el tamaño permitido");
        }
        JsonObject objeto;
        try (JsonReader lector = Json.createReader(new StringReader(json))) {
            JsonValue valor = lector.readValue();
            if (valor.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new DatoInvalido(400, "El cuerpo JSON debe ser un objeto");
            }
            objeto = valor.asJsonObject();
        } catch (JsonException ex) {
            throw new DatoInvalido(400, "El cuerpo contiene un JSON inválido");
        }
        Set<String> permitidos = crear ? CAMPOS_CREAR : CAMPOS_MODIFICAR;
        for (String clave : objeto.keySet()) {
            if (!permitidos.contains(clave)) {
                throw new DatoInvalido(400, "Campo no permitido: " + clave);
            }
        }
        UUID idEmpleado = crear ? uuid(valorDe(objeto, "idEmpleado"), "idEmpleado") : null;
        UUID idRol = crear ? uuid(valorDe(objeto, "idRol"), "idRol") : null;
        JsonValue estado = objeto.get("activo");
        if (estado == null || estado == JsonValue.NULL) {
            throw new DatoInvalido(422, "El estado activo es obligatorio");
        }
        if (estado.getValueType() != JsonValue.ValueType.TRUE
                && estado.getValueType() != JsonValue.ValueType.FALSE) {
            throw new DatoInvalido(400, "El campo activo debe ser booleano");
        }
        String nota = observaciones(objeto);
        return new EmpleadoRolSolicitud(idEmpleado, idRol,
                estado.getValueType() == JsonValue.ValueType.TRUE, nota);
    }

    private static JsonValue valorDe(JsonObject objeto, String clave) {
        return objeto.get(clave);
    }

    private static UUID uuid(JsonValue valor, String campo) {
        if (valor == null || valor == JsonValue.NULL) {
            throw new DatoInvalido(422, "El campo " + campo + " es obligatorio");
        }
        if (valor.getValueType() != JsonValue.ValueType.STRING) {
            throw new DatoInvalido(400, "El campo " + campo + " debe ser texto UUID");
        }
        String texto = ((jakarta.json.JsonString) valor).getString().trim();
        if (texto.isEmpty()) {
            throw new DatoInvalido(422, "El campo " + campo + " no puede estar vacío");
        }
        if (!UUID_CANONICO.matcher(texto).matches()) {
            throw new DatoInvalido(400, "El campo " + campo + " no es un UUID válido");
        }
        return UUID.fromString(texto);
    }

    private static String observaciones(JsonObject objeto) {
        JsonValue valor = objeto.get("observaciones");
        if (valor == null || valor == JsonValue.NULL) return null;
        if (valor.getValueType() != JsonValue.ValueType.STRING) {
            throw new DatoInvalido(400, "El campo observaciones debe ser texto");
        }
        String texto = objeto.getString("observaciones");
        int ini = 0, fin = texto.length();
        while (ini < fin && espacio(texto.charAt(ini))) ini++;
        while (fin > ini && espacio(texto.charAt(fin - 1))) fin--;
        String salida = texto.substring(ini, fin);
        if (salida.isEmpty()) return null;
        if (salida.length() > MAX_OBSERVACIONES) {
            throw new DatoInvalido(422, "Observaciones supera el máximo de 1000 caracteres");
        }
        for (int i = 0; i < salida.length(); i++) {
            char c = salida.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\t') {
                throw new DatoInvalido(422, "Observaciones contiene caracteres de control");
            }
        }
        return salida;
    }

    private static boolean espacio(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u200B';
    }

    public UUID getIdEmpleado() { return idEmpleado; }
    public UUID getIdRol() { return idRol; }
    public Boolean getActivo() { return activo; }
    public String getObservaciones() { return observaciones; }

    /** Excepcion con respuesta HTTP bien definida para el Resource. */
    public static final class DatoInvalido extends RuntimeException {
        private final int estadoHttp;
        public DatoInvalido(int estadoHttp, String mensaje) {
            super(mensaje);
            this.estadoHttp = estadoHttp;
        }
        public int getEstadoHttp() { return estadoHttp; }
    }
}
