package boundary.rest.dto;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.util.Set;

/**
 * Valida el JSON ORIGINAL de alta/edicion de rol.
 * Impide que JSON-B convierta numeros a texto o cadenas a booleanos.
 * Las reglas de persistencia se conservan en RolDAO.
 */
public final class RolSolicitud {

    private static final int MAX_JSON = 8192;
    private static final int MAX_NOMBRE = 155; // Mismo limite que RolDAO.
    private static final int MAX_OBSERVACIONES = 1000; // Limite defensivo de la API.
    private static final Set<String> CAMPOS = Set.of("nombre", "activo", "observaciones");

    private final String nombre;
    private final Boolean activo;
    private final String observaciones;

    private RolSolicitud(String nombre, Boolean activo, String observaciones) {
        this.nombre = nombre;
        this.activo = activo;
        this.observaciones = observaciones;
    }

    /** Interpreta y valida un cuerpo JSON de Rol. */
    public static RolSolicitud leer(String json) {
        if (json == null || json.isBlank()) {
            throw new DatoInvalido(400, "Debe enviar un objeto JSON");
        }
        if (json.length() > MAX_JSON) {
            throw new DatoInvalido(400, "El cuerpo JSON supera el tamano permitido");
        }
        JsonObject objeto;
        try (JsonReader lector = Json.createReader(new StringReader(json))) {
            JsonValue valor = lector.readValue();
            if (valor.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new DatoInvalido(400, "El cuerpo JSON debe ser un objeto");
            }
            objeto = valor.asJsonObject();
        } catch (JsonException ex) {
            throw new DatoInvalido(400, "El cuerpo contiene un JSON invalido");
        }
        for (String clave : objeto.keySet()) {
            if (!CAMPOS.contains(clave)) {
                throw new DatoInvalido(400, "Campo no permitido: " + clave);
            }
        }
        String nombre = texto(objeto, "nombre", true, MAX_NOMBRE, false);
        if (nombre.length() < 2) {
            throw new DatoInvalido(422, "El nombre debe tener entre 2 y 155 caracteres");
        }
        JsonValue estado = objeto.get("activo");
        if (estado == null || estado == JsonValue.NULL) {
            throw new DatoInvalido(422, "Debe indicar el estado del rol");
        }
        if (estado.getValueType() != JsonValue.ValueType.TRUE
                && estado.getValueType() != JsonValue.ValueType.FALSE) {
            throw new DatoInvalido(400, "activo debe ser booleano true o false");
        }
        String observaciones = texto(objeto, "observaciones", false, MAX_OBSERVACIONES, true);
        return new RolSolicitud(nombre, estado.getValueType() == JsonValue.ValueType.TRUE, observaciones);
    }

    private static String texto(JsonObject objeto, String campo, boolean obligatorio,
            int maximo, boolean permitirSaltos) {
        JsonValue valor = objeto.get(campo);
        if (valor == null || valor == JsonValue.NULL) {
            if (obligatorio) {
                throw new DatoInvalido(422, "El campo " + campo + " es obligatorio");
            }
            return null;
        }
        if (valor.getValueType() != JsonValue.ValueType.STRING) {
            throw new DatoInvalido(400, "El campo " + campo + " debe ser texto");
        }
        String entrada = objeto.getString(campo);
        int inicio = 0;
        int fin = entrada.length();
        while (inicio < fin && esEspacio(entrada.charAt(inicio))) inicio++;
        while (fin > inicio && esEspacio(entrada.charAt(fin - 1))) fin--;
        String salida = entrada.substring(inicio, fin);
        if (salida.isEmpty()) {
            if (obligatorio) {
                throw new DatoInvalido(422, "El campo " + campo + " no puede estar vacio");
            }
            return null;
        }
        if (salida.length() > maximo) {
            throw new DatoInvalido(422, "El campo " + campo + " excede el limite permitido");
        }
        for (int i = 0; i < salida.length(); i++) {
            char c = salida.charAt(i);
            if (Character.isISOControl(c)
                    && !(permitirSaltos && (c == '\n' || c == '\t'))) {
                throw new DatoInvalido(422,
                        "El campo " + campo + " contiene caracteres de control");
            }
        }
        return salida;
    }

    private static boolean esEspacio(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u200B';
    }

    public String getNombre() { return nombre; }
    public Boolean getActivo() { return activo; }
    public String getObservaciones() { return observaciones; }

    /** Error de solicitud con estado HTTP preciso. */
    public static final class DatoInvalido extends RuntimeException {
        private final int estadoHttp;

        public DatoInvalido(int estadoHttp, String mensaje) {
            super(mensaje);
            this.estadoHttp = estadoHttp;
        }

        public int getEstadoHttp() { return estadoHttp; }
    }
}
