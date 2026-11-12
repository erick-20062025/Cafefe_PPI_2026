package boundary.rest;

import boundary.rest.messages.ApiExceptionUtil;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import entity.TipoDescuento;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

/**
 * Intercepta errores de lectura JSON y los convierte en respuestas uniformes.
 *
 * En TipoDescuento valida además los tipos JSON originales antes de JSON-B:
 * JSON-B podría convertir números a String, textos a Boolean o decimales a
 * Integer, impidiendo que TipoDescuentoResource detecte el formato incorrecto.
 * El resto de los recursos REST conserva su comportamiento actual.
 */
@Provider
public class JsonbExceptionMapper implements ReaderInterceptor {

    /** Límite razonable para el JSON pequeño de TipoDescuento (64 KiB). */
    private static final int MAX_CUERPO_TIPO_DESCUENTO = 64 * 1024;
    private static final BigDecimal MIN_ENTERO = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigDecimal MAX_ENTERO = BigDecimal.valueOf(Integer.MAX_VALUE);

    /**
     * Comprueba el JSON antes de delegar su conversión a Jakarta JSON-B.
     *
     * @param context contexto de lectura de la petición REST
     * @return objeto deserializado por el proveedor REST habitual
     * @throws IOException si falla la lectura del flujo HTTP
     * @throws WebApplicationException si el JSON no es válido
     */
    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context)
            throws IOException, WebApplicationException {

        if (TipoDescuento.class.equals(context.getType())
                && esJson(context.getMediaType())) {
            comprobarCuerpoTipoDescuento(context);
        }

        try {
            return context.proceed();
        } catch (RuntimeException ex) {
            if (ApiExceptionUtil.contieneCausa(ex, JsonbException.class)) {
                throw solicitudInvalida(
                        "El cuerpo de la solicitud contiene un JSON vacío o inválido", ex);
            }
            throw ex;
        }
    }

    private static boolean esJson(MediaType tipo) {
        return tipo != null && (MediaType.APPLICATION_JSON_TYPE.isCompatible(tipo)
                || ("application".equalsIgnoreCase(tipo.getType())
                && tipo.getSubtype() != null
                && tipo.getSubtype().toLowerCase(java.util.Locale.ROOT).endsWith("+json")));
    }

    /**
     * Usa JSON-P para leer y verificar el tipo de cada campo, sin convertirlo.
     * Restituye el flujo para que JSON-B pueda hacer la deserialización normal.
     */
    private static void comprobarCuerpoTipoDescuento(ReaderInterceptorContext context)
            throws IOException {
        InputStream flujo = context.getInputStream();
        if (flujo == null) {
            throw solicitudInvalida("Debe proporcionar un cuerpo JSON", null);
        }

        byte[] cuerpo = flujo.readNBytes(MAX_CUERPO_TIPO_DESCUENTO + 1);
        if (cuerpo.length > MAX_CUERPO_TIPO_DESCUENTO) {
            throw solicitudInvalida("El cuerpo JSON supera el tamaño permitido", null);
        }

        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(cuerpo))) {
            JsonValue valor = reader.readValue();
            if (valor.getValueType() != JsonValue.ValueType.OBJECT) {
                throw solicitudInvalida("El cuerpo JSON debe ser un objeto", null);
            }

            String problema = validarTiposTipoDescuento(valor.asJsonObject());
            if (problema != null) {
                throw solicitudInvalida(problema, null);
            }
        } catch (JsonException ex) {
            throw solicitudInvalida(
                    "El cuerpo de la solicitud contiene un JSON vacío o inválido", ex);
        }

        context.setInputStream(new ByteArrayInputStream(cuerpo));
    }

    /**
     * Reglas puras, sin acceso a BD, para facilitar pruebas con Mockito.
     * Los null y campos ausentes los valida TipoDescuentoResource con HTTP 422.
     *
     * @param objeto objeto JSON previo a deserialización
     * @return mensaje del tipo incorrecto, o null si los tipos son válidos
     */
    static String validarTiposTipoDescuento(JsonObject objeto) {
        if (tipoIncorrecto(objeto.get("nombre"), JsonValue.ValueType.STRING)) {
            return "El campo nombre debe ser una cadena de texto";
        }
        JsonValue activo = objeto.get("activo");
        if (activo != null && activo != JsonValue.NULL
                && activo.getValueType() != JsonValue.ValueType.TRUE
                && activo.getValueType() != JsonValue.ValueType.FALSE) {
            return "El campo activo debe ser true o false, sin comillas";
        }

        JsonValue maximo = objeto.get("descuentoMaximo");
        if (maximo != null && maximo != JsonValue.NULL) {
            if (maximo.getValueType() != JsonValue.ValueType.NUMBER) {
                return "El campo descuentoMaximo debe ser un número entero";
            }
            JsonNumber numero = (JsonNumber) maximo;
            if (!numero.isIntegral()) {
                return "El campo descuentoMaximo debe ser un número entero, sin decimales";
            }
            BigDecimal valor = numero.bigDecimalValue();
            if (valor.compareTo(MIN_ENTERO) < 0 || valor.compareTo(MAX_ENTERO) > 0) {
                return "El campo descuentoMaximo está fuera del rango permitido";
            }
        }

        if (tipoIncorrecto(objeto.get("observaciones"), JsonValue.ValueType.STRING)) {
            return "El campo observaciones debe ser una cadena de texto";
        }
        if (tipoIncorrecto(objeto.get("idTipoDescuento"), JsonValue.ValueType.STRING)) {
            return "El identificador debe ser una cadena de texto UUID";
        }
        return null;
    }

    private static boolean tipoIncorrecto(JsonValue valor, JsonValue.ValueType requerido) {
        return valor != null && valor != JsonValue.NULL
                && valor.getValueType() != requerido;
    }

    private static BadRequestException solicitudInvalida(String detalle, Throwable causa) {
        ApiResponse respuesta = new ApiResponse(
                ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                detalle);
        Response respuestaHttp = Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(respuesta)
                .build();
        return causa == null
                ? new BadRequestException(respuestaHttp)
                : new BadRequestException(respuestaHttp, causa);
    }
}
