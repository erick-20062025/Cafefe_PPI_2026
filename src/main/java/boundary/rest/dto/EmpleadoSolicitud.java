package boundary.rest.dto;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.util.Set;

/**
 * Lectura estricta del JSON para crear o modificar empleados.
 * Valida el tipo JSON real; nunca permite coerciones implicitas de JSON-B.
 * Las reglas transaccionales permanecen en EmpleadoDAO.
 */
public final class EmpleadoSolicitud {

    private static final int MAX_JSON = 8192;
    private static final int MAX_NOMBRE = 255;
    private static final int MAX_APELLIDO = 255;
    // Limite defensivo de la API; la entidad permite una longitud mayor.
    private static final int MAX_COMENTARIOS = 1000;
    private static final Set<String> CAMPOS = Set.of(
            "nombre", "apellido", "activo", "comentarios");

    private final String nombre;
    private final String apellido;
    private final Boolean activo;
    private final String comentarios;

    private EmpleadoSolicitud(String nombre, String apellido,
            Boolean activo, String comentarios) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.activo = activo;
        this.comentarios = comentarios;
    }

    /** Interpreta un objeto JSON, rechaza campos inesperados y valida tipos. */
    public static EmpleadoSolicitud leer(String json) {
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
        String nombre = texto(objeto, "nombre", true, MAX_NOMBRE, false);
        String apellido = texto(objeto, "apellido", true, MAX_APELLIDO, false);
        if (nombre.length() < 2) {
            throw new DatoInvalido(422, "El nombre debe tener entre 2 y 255 caracteres");
        }
        if (apellido.length() < 2) {
            throw new DatoInvalido(422, "El apellido debe tener entre 2 y 255 caracteres");
        }
        JsonValue estado = objeto.get("activo");
        if (estado == null || estado == JsonValue.NULL) {
            throw new DatoInvalido(422, "Debe indicar el estado del empleado");
        }
        if (estado.getValueType() != JsonValue.ValueType.TRUE
                && estado.getValueType() != JsonValue.ValueType.FALSE) {
            throw new DatoInvalido(400, "activo debe ser booleano true o false");
        }
        String comentarios = texto(objeto, "comentarios", false, MAX_COMENTARIOS, true);
        return new EmpleadoSolicitud(nombre, apellido,
                estado.getValueType() == JsonValue.ValueType.TRUE, comentarios);
    }

    private static String texto(JsonObject objeto, String campo,
            boolean obligatorio, int maximo, boolean permitirSaltos) {
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
                throw new DatoInvalido(422, "El campo " + campo + " no puede estar vacío");
            }
            return null;
        }
        if (salida.length() > maximo) {
            throw new DatoInvalido(422, "El campo " + campo + " excede el límite permitido");
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
    public String getApellido() { return apellido; }
    public Boolean getActivo() { return activo; }
    public String getComentarios() { return comentarios; }

    /** Excepcion de entrada con estado HTTP y detalle seguro para el cliente. */
    public static final class DatoInvalido extends RuntimeException {
        private final int estadoHttp;

        public DatoInvalido(int estadoHttp, String mensaje) {
            super(mensaje);
            this.estadoHttp = estadoHttp;
        }

        public int getEstadoHttp() { return estadoHttp; }
    }
}
