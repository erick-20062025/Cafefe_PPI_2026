package boundary.rest.dto;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
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
 * Valida el JSON original de creación y edición de descuentos.
 * No permite conversiones implícitas de tipos por JSON-B.
 *
 * Las fechas aceptan yyyy-MM-dd (calendario local de El Salvador) o
 * ISO-8601 con zona horaria explícita. fechaHasta es opcional.
 */
public final class DescuentoSolicitud {

    private static final int MAX_JSON = 8192;
    private static final int MAX_NOMBRE = 155;
    private static final int MAX_OBSERVACIONES = 1000; // Límite defensivo REST.
    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern FECHA_CORTA = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
    private static final Set<String> CAMPOS = Set.of(
            "idTipoDescuento", "nombre", "fechaDesde", "fechaHasta", "observaciones");

    private final UUID idTipoDescuento;
    private final String nombre;
    private final Date fechaDesde;
    private final Date fechaHasta;
    private final String observaciones;

    private DescuentoSolicitud(UUID idTipoDescuento, String nombre, Date fechaDesde,
            Date fechaHasta, String observaciones) {
        this.idTipoDescuento = idTipoDescuento;
        this.nombre = nombre;
        this.fechaDesde = new Date(fechaDesde.getTime());
        this.fechaHasta = fechaHasta == null ? null : new Date(fechaHasta.getTime());
        this.observaciones = observaciones;
    }

    /** Analiza cuerpo JSON sin tocar la base de datos. */
    public static DescuentoSolicitud leer(String json) {
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

        String tipo = texto(objeto, "idTipoDescuento", true, 36, false);
        if (!UUID_CANONICO.matcher(tipo).matches()) {
            throw new DatoInvalido(400, "idTipoDescuento debe ser un UUID válido");
        }
        UUID idTipoDescuento = UUID.fromString(tipo);
        String nombre = texto(objeto, "nombre", true, MAX_NOMBRE, false);
        if (nombre.length() < 2) {
            throw new DatoInvalido(422, "El nombre debe tener entre 2 y 155 caracteres");
        }
        String desdeTexto = texto(objeto, "fechaDesde", true, 40, false);
        Date fechaDesde = fecha(desdeTexto, "fechaDesde");
        String hastaTexto = texto(objeto, "fechaHasta", false, 40, false);
        Date fechaHasta = hastaTexto == null ? null : fecha(hastaTexto, "fechaHasta");
        if (fechaHasta != null && fechaHasta.toInstant().atZone(ZONA).toLocalDate()
                .isBefore(fechaDesde.toInstant().atZone(ZONA).toLocalDate())) {
            throw new DatoInvalido(422, "La fecha hasta no puede ser anterior a la fecha desde");
        }
        String observaciones = texto(objeto, "observaciones", false, MAX_OBSERVACIONES, true);
        return new DescuentoSolicitud(idTipoDescuento, nombre, fechaDesde,
                fechaHasta, observaciones);
    }

    private static Date fecha(String valor, String campo) {
        try {
            if (FECHA_CORTA.matcher(valor).matches()) {
                LocalDate dia = LocalDate.parse(valor, DateTimeFormatter.ISO_LOCAL_DATE);
                return Date.from(dia.atStartOfDay(ZONA).toInstant());
            }
            return Date.from(OffsetDateTime.parse(valor, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant());
        } catch (DateTimeParseException ex) {
            throw new DatoInvalido(400, campo
                    + " debe ser una fecha válida yyyy-MM-dd o ISO-8601 con zona horaria");
        }
    }

    private static String texto(JsonObject objeto, String campo, boolean obligatorio,
            int maximo, boolean permitirSaltos) {
        JsonValue valor = objeto.get(campo);
        if (valor == null || valor == JsonValue.NULL) {
            if (obligatorio) throw new DatoInvalido(422, "El campo " + campo + " es obligatorio");
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
            if (obligatorio) throw new DatoInvalido(422, "El campo " + campo + " no puede estar vacío");
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

    private static boolean esEspacio(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u200B';
    }

    public UUID getIdTipoDescuento() { return idTipoDescuento; }
    public String getNombre() { return nombre; }
    public Date getFechaDesde() { return new Date(fechaDesde.getTime()); }
    public Date getFechaHasta() { return fechaHasta == null ? null : new Date(fechaHasta.getTime()); }
    public String getObservaciones() { return observaciones; }

    /** Error validado, con estado HTTP preciso. */
    public static final class DatoInvalido extends RuntimeException {
        private final int estadoHttp;
        public DatoInvalido(int estadoHttp, String mensaje) {
            super(mensaje);
            this.estadoHttp = estadoHttp;
        }
        public int getEstadoHttp() { return estadoHttp; }
    }
}
