package boundary.rest.dto;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.math.BigDecimal;
import jakarta.json.JsonNumber;
import java.util.Set;

/**
 * Valida el JSON ORIGINAL de alta/edicion de producto.
 * Impide que JSON-B convierta numeros a texto o cadenas a booleanos.
 * Las reglas de persistencia se conservan en ProductoDAO.
 */
public final class ProductoSolicitud {

    private static final int MAX_JSON = 8192;
    private static final int MAX_NOMBRE = 155; // Mismo limite que ProductoDAO.
    private static final int MAX_COMENTARIOS = 1000; // Limite defensivo de la API.
    private static final Set<String> CAMPOS = Set.of("nombre", "activo", "comentarios", "precioSugerido");

    private final BigDecimal precioSugerido;
    private final String nombre;
    private final Boolean activo;
    private final String comentarios;

    private ProductoSolicitud(String nombre, Boolean activo, String comentarios, BigDecimal precioSugerido) {
        this.precioSugerido = precioSugerido;
        this.nombre = nombre;
        this.activo = activo;
        this.comentarios = comentarios;
    }

    /** Interpreta y valida un cuerpo JSON de Producto. */
    public static ProductoSolicitud leer(String json) {
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
        JsonValue estado = objeto.get("activo");
        if (estado == null || estado == JsonValue.NULL) {
            throw new DatoInvalido(422, "Debe indicar el estado del producto");
        }
        if (estado.getValueType() != JsonValue.ValueType.TRUE
                && estado.getValueType() != JsonValue.ValueType.FALSE) {
            throw new DatoInvalido(400, "activo debe ser booleano true o false");
        }
        String comentarios = texto(objeto, "comentarios", false, MAX_COMENTARIOS, true);
        JsonValue valorPrecio = objeto.get("precioSugerido");
        if (valorPrecio == null || valorPrecio == JsonValue.NULL) {
            throw new DatoInvalido(422, "Debe ingresar el precio sugerido del producto");
        }
        if (valorPrecio.getValueType() != JsonValue.ValueType.NUMBER) {
            throw new DatoInvalido(400, "precioSugerido debe ser un numero");
        }
        BigDecimal precio = ((JsonNumber) valorPrecio).bigDecimalValue();
        if (precio.signum() < 0) {
            throw new DatoInvalido(422, "El precio sugerido no puede ser negativo");
        }
        return new ProductoSolicitud(nombre, estado.getValueType() == JsonValue.ValueType.TRUE, comentarios, precio);
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

    public BigDecimal getPrecioSugerido() { return precioSugerido; }
    public String getNombre() { return nombre; }
    public Boolean getActivo() { return activo; }
    public String getComentarios() { return comentarios; }

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
