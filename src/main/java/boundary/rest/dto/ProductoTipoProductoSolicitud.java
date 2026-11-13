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
 * POST acepta solo producto, tipo y observaciones; UUID y fecha son del servidor.
 */
public final class ProductoTipoProductoSolicitud {
    private static final int MAX_JSON = 8192;
    private static final int MAX_OBSERVACIONES = 1000;
    private static final Set<String> CAMPOS = Set.of("idProducto", "idTipoProducto", "observaciones");
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final UUID idProducto;
    private final UUID idTipoProducto;
    private final String observaciones;

    private ProductoTipoProductoSolicitud(UUID idProducto, UUID idTipoProducto,
            String observaciones) {
        this.idProducto = idProducto;
        this.idTipoProducto = idTipoProducto;
        this.observaciones = observaciones;
    }

    public static ProductoTipoProductoSolicitud leer(String json) {
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
        for (String clave : objeto.keySet()) {
            if (!CAMPOS.contains(clave)) {
                throw new DatoInvalido(400, "Campo no permitido: " + clave);
            }
        }
        UUID idProducto = uuid(valorDe(objeto, "idProducto"), "idProducto");
        UUID idTipoProducto = uuid(valorDe(objeto, "idTipoProducto"), "idTipoProducto");
        String nota = observaciones(objeto);
        return new ProductoTipoProductoSolicitud(idProducto, idTipoProducto,
                nota);
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

    public UUID getIdProducto() { return idProducto; }
    public UUID getIdTipoProducto() { return idTipoProducto; }
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
