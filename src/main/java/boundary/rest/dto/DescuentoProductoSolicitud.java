package boundary.rest.dto;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Analiza y valida el JSON original de una nueva asociacion entre producto y
 * descuento. No permite conversiones implicitas de tipos JSON ni campos JPA.
 * Las reglas entre entidades y la vigencia definitiva se validan en el DAO.
 */
public final class DescuentoProductoSolicitud {

    private static final int MAX_JSON = 8192;
    private static final int MAX_OBSERVACIONES = 1000;
    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");
    // UUID canonico: CINCO grupos (8-4-4-4-12). Comprobado expresamente.
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");
    private static final Pattern FECHA_CORTA = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
    private static final Set<String> CAMPOS = Set.of(
            "idDescuento", "idProducto", "valor", "fechaDesde", "fechaHasta", "observaciones");

    private final UUID idDescuento;
    private final UUID idProducto;
    private final int valor;
    private final Date fechaDesde;
    private final Date fechaHasta;
    private final String observaciones;

    private DescuentoProductoSolicitud(UUID idDescuento, UUID idProducto, int valor,
            Date fechaDesde, Date fechaHasta, String observaciones) {
        this.idDescuento = idDescuento;
        this.idProducto = idProducto;
        this.valor = valor;
        this.fechaDesde = new Date(fechaDesde.getTime());
        this.fechaHasta = fechaHasta == null ? null : new Date(fechaHasta.getTime());
        this.observaciones = observaciones;
    }

    /** Valida el cuerpo sin acceder a PostgreSQL. */
    public static DescuentoProductoSolicitud leer(String json) {
        if (json == null || json.isBlank()) {
            throw new DatoInvalido(400, "Debe enviar un objeto JSON");
        }
        if (json.length() > MAX_JSON) {
            throw new DatoInvalido(400, "El cuerpo JSON supera el tamaño permitido");
        }

        JsonObject objeto;
        try (JsonReader lector = Json.createReader(new StringReader(json))) {
            JsonValue contenido = lector.readValue();
            if (contenido.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new DatoInvalido(400, "El cuerpo JSON debe ser un objeto");
            }
            objeto = contenido.asJsonObject();
        } catch (JsonException ex) {
            throw new DatoInvalido(400, "El cuerpo contiene un JSON inválido");
        }

        for (String campo : objeto.keySet()) {
            if (!CAMPOS.contains(campo)) {
                throw new DatoInvalido(400, "Campo no permitido: " + campo);
            }
        }

        UUID descuento = uuid(obligatorio(objeto, "idDescuento"), "idDescuento");
        UUID producto = uuid(obligatorio(objeto, "idProducto"), "idProducto");
        int porcentaje = porcentaje(objeto);
        Date desde = fecha(obligatorio(objeto, "fechaDesde"), "fechaDesde");
        String hastaTexto = texto(objeto, "fechaHasta", false, 40, false);
        Date hasta = hastaTexto == null ? null : fecha(hastaTexto, "fechaHasta");
        if (hasta != null && hasta.toInstant().atZone(ZONA).toLocalDate()
                .isBefore(desde.toInstant().atZone(ZONA).toLocalDate())) {
            throw new DatoInvalido(422, "La fecha hasta no puede ser anterior a la fecha desde");
        }
        String notas = texto(objeto, "observaciones", false, MAX_OBSERVACIONES, true);
        return new DescuentoProductoSolicitud(descuento, producto, porcentaje, desde, hasta, notas);
    }

    private static String obligatorio(JsonObject objeto, String campo) {
        return texto(objeto, campo, true, 40, false);
    }

    private static UUID uuid(String texto, String campo) {
        if (!UUID_CANONICO.matcher(texto).matches()) {
            throw new DatoInvalido(400, campo + " debe ser un UUID válido");
        }
        return UUID.fromString(texto);
    }

    private static int porcentaje(JsonObject objeto) {
        JsonValue campo = objeto.get("valor");
        if (campo == null || campo == JsonValue.NULL) {
            throw new DatoInvalido(422, "El porcentaje de descuento es obligatorio");
        }
        if (campo.getValueType() != JsonValue.ValueType.NUMBER) {
            throw new DatoInvalido(400, "valor debe ser un número entero");
        }
        BigDecimal numero = ((JsonNumber) campo).bigDecimalValue();
        if (numero.stripTrailingZeros().scale() > 0) {
            throw new DatoInvalido(400, "valor debe ser un número entero");
        }
        if (numero.compareTo(BigDecimal.ONE) < 0
                || numero.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new DatoInvalido(422, "El porcentaje debe estar entre 1 y 100");
        }
        return numero.intValueExact();
    }

    private static Date fecha(String texto, String campo) {
        try {
            if (FECHA_CORTA.matcher(texto).matches()) {
                LocalDate dia = LocalDate.parse(texto, DateTimeFormatter.ISO_LOCAL_DATE);
                return Date.from(dia.atStartOfDay(ZONA).toInstant());
            }
            return Date.from(OffsetDateTime.parse(texto, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toInstant());
        } catch (DateTimeParseException ex) {
            throw new DatoInvalido(400, campo
                    + " debe ser yyyy-MM-dd o ISO-8601 con zona horaria");
        }
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
        int inicio = 0, fin = entrada.length();
        while (inicio < fin && espacio(entrada.charAt(inicio))) inicio++;
        while (fin > inicio && espacio(entrada.charAt(fin - 1))) fin--;
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
            if (Character.isISOControl(c) && !(permitirSaltos && (c == '\n' || c == '\t'))) {
                throw new DatoInvalido(422, "El campo " + campo + " contiene caracteres de control");
            }
        }
        return salida;
    }

    private static boolean espacio(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u200B';
    }

    public UUID getIdDescuento() { return idDescuento; }
    public UUID getIdProducto() { return idProducto; }
    public int getValor() { return valor; }
    public Date getFechaDesde() { return new Date(fechaDesde.getTime()); }
    public Date getFechaHasta() { return fechaHasta == null ? null : new Date(fechaHasta.getTime()); }
    public String getObservaciones() { return observaciones; }

    /** Error de validacion con codigo HTTP especifico. */
    public static final class DatoInvalido extends RuntimeException {
        private final int estadoHttp;
        public DatoInvalido(int estadoHttp, String mensaje) {
            super(mensaje);
            this.estadoHttp = estadoHttp;
        }
        public int getEstadoHttp() { return estadoHttp; }
    }
}
