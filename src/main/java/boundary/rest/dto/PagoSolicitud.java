package boundary.rest.dto;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/**
 * Datos permitidos para registrar un abono. Comprueba los tipos JSON originales
 * para impedir conversiones implícitas, por ejemplo, texto a número.
 * Las reglas transaccionales permanecen en PagoDAO.
 */
public final class PagoSolicitud {

    private static final int MAX_JSON = 8192;
    private static final int MAX_REFERENCIA = 120;
    private static final int MAX_OBSERVACIONES = 1000;
    private static final Set<String> CAMPOS = Set.of(
            "monto", "tipoPago", "referenciaExterna", "observaciones", "efectivoRecibido");
    private static final Set<String> METODOS = Set.of(
            "EFECTIVO", "TARJETA", "TRANSFERENCIA", "QR");

    private BigDecimal efectivoRecibido;
    public BigDecimal getEfectivoRecibido() { return efectivoRecibido; }
    private final BigDecimal monto;
    private final String tipoPago;
    private final String referenciaExterna;
    private final String observaciones;

    private PagoSolicitud(BigDecimal monto, String tipoPago,
            String referenciaExterna, String observaciones) {
        this.monto = monto;
        this.tipoPago = tipoPago;
        this.referenciaExterna = referenciaExterna;
        this.observaciones = observaciones;
    }

    /** Lee y valida una solicitud de cobro sin consultar la base de datos. */
    public static PagoSolicitud leer(String json) {
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
        for (String campo : objeto.keySet()) {
            if (!CAMPOS.contains(campo)) {
                throw new DatoInvalido(400, "Campo no permitido: " + campo);
            }
        }
        BigDecimal monto = monto(objeto);
        String metodo = texto(objeto, "tipoPago", true, 20, false);
        if (!METODOS.contains(metodo.toUpperCase(Locale.ROOT))) {
            throw new DatoInvalido(422, "El método de pago no es válido");
        }
        metodo = metodo.toUpperCase(Locale.ROOT);
        String referencia = texto(objeto, "referenciaExterna", false, MAX_REFERENCIA, false);
        String observaciones = texto(objeto, "observaciones", false,
                MAX_OBSERVACIONES, true);
        if (!"EFECTIVO".equals(metodo) && referencia == null) {
            throw new DatoInvalido(422, "Debe indicar una referencia para " + metodo);
        }
        PagoSolicitud salida = new PagoSolicitud(monto, metodo, referencia, observaciones);
        if (objeto.containsKey("efectivoRecibido")) {
            if (!"EFECTIVO".equals(metodo) || !(objeto.get("efectivoRecibido") instanceof JsonNumber recibido))
                throw new DatoInvalido(400, "efectivoRecibido solo se admite como número para efectivo");
            salida.efectivoRecibido = monto(Json.createObjectBuilder().add("monto", recibido).build());
            if (salida.efectivoRecibido.compareTo(monto) < 0) throw new DatoInvalido(422, "El efectivo recibido es menor que el abono");
        }
        return salida;
    }

    private static BigDecimal monto(JsonObject objeto) {
        JsonValue valor = objeto.get("monto");
        if (valor == null || valor == JsonValue.NULL) {
            throw new DatoInvalido(422, "El monto es obligatorio");
        }
        if (valor.getValueType() != JsonValue.ValueType.NUMBER) {
            throw new DatoInvalido(400, "monto debe ser un número JSON");
        }
        BigDecimal numero = ((JsonNumber) valor).bigDecimalValue();
        if (numero.signum() <= 0) {
            throw new DatoInvalido(422, "El monto debe ser mayor que cero");
        }
        // Evita montos desproporcionados y exponentes que expandan el número.
        if (numero.compareTo(new BigDecimal("999999999999.99")) > 0) {
            throw new DatoInvalido(422, "El monto supera el rango permitido");
        }
        if (numero.stripTrailingZeros().scale() > 2) {
            throw new DatoInvalido(422, "El monto solo admite dos decimales");
        }
        return numero.setScale(2);
    }

    private static String texto(JsonObject objeto, String campo, boolean obligatorio,
            int maximo, boolean saltos) {
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
        String texto = objeto.getString(campo).strip();
        // Java strip() no cubre todos los espacios Unicode; se verifica aparte.
        int inicio = 0;
        int fin = texto.length();
        while (inicio < fin && esEspacio(texto.charAt(inicio))) inicio++;
        while (fin > inicio && esEspacio(texto.charAt(fin - 1))) fin--;
        texto = texto.substring(inicio, fin);
        if (texto.isEmpty()) {
            if (obligatorio) {
                throw new DatoInvalido(422, "El campo " + campo + " no puede estar vacío");
            }
            return null;
        }
        if (texto.length() > maximo) {
            throw new DatoInvalido(422, "El campo " + campo + " es demasiado largo");
        }
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (Character.isISOControl(c)
                    && !(saltos && (c == '\n' || c == '\t'))) {
                throw new DatoInvalido(422,
                        "El campo " + campo + " contiene caracteres no permitidos");
            }
        }
        return texto;
    }

    private static boolean esEspacio(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u200B';
    }

    public BigDecimal getMonto() { return monto; }
    public String getTipoPago() { return tipoPago; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public String getObservaciones() { return observaciones; }

    /** Error de validación de entrada con estado HTTP concreto. */
    public static final class DatoInvalido extends RuntimeException {
        private final int estadoHttp;
        public DatoInvalido(int estadoHttp, String mensaje) {
            super(mensaje);
            this.estadoHttp = estadoHttp;
        }
        public int getEstadoHttp() { return estadoHttp; }
    }
}
