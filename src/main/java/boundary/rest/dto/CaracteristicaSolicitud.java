package boundary.rest.dto;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.util.Set;
import java.util.UUID;

public record CaracteristicaSolicitud(UUID idTipoCaracteristica, String nombre, Boolean activo, String observaciones) {

    public static CaracteristicaSolicitud leer(String json) {
        if (json == null || json.isBlank() || json.length() > 8192) {
            throw new DatoInvalido(400, "Debe enviar un objeto JSON de hasta 8192 caracteres");
        }
        try (var lector = Json.createReader(new StringReader(json))) {
            if (!(lector.readValue() instanceof JsonObject objeto)) {
                throw new DatoInvalido(400, "El cuerpo debe ser un objeto JSON");
            }
            campos(objeto, Set.of("idTipoCaracteristica", "nombre", "activo", "observaciones"));
            JsonValue activo = objeto.get("activo");
            if (activo == null || activo == JsonValue.NULL) {
                throw new DatoInvalido(422, "El estado es obligatorio");
            }
            if (activo != JsonValue.TRUE && activo != JsonValue.FALSE) {
                throw new DatoInvalido(400, "activo debe ser booleano");
            }
            String nombre = texto(objeto, "nombre", 155);
            if (nombre == null) throw new DatoInvalido(422, "El nombre es obligatorio");
            return new CaracteristicaSolicitud(id(objeto, "idTipoCaracteristica"), nombre,
                    activo == JsonValue.TRUE, texto(objeto, "observaciones", 1000));
        } catch (JsonException ex) {
            throw new DatoInvalido(400, "El cuerpo contiene JSON inválido");
        }
    }

    private static void campos(JsonObject objeto, Set<String> permitidos) {
        if (!permitidos.containsAll(objeto.keySet())) {
            throw new DatoInvalido(400, "El cuerpo contiene campos no permitidos");
        }
    }

    private static UUID id(JsonObject objeto, String campo) {
        String valor = texto(objeto, campo, 65536);
        if (valor == null) {
            throw new DatoInvalido(422, "El campo " + campo + " es obligatorio");
        }
        return uuid(valor);
    }

    public static UUID uuid(String valor) {
        if (valor == null || !valor.matches("(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")) {
            throw new DatoInvalido(400, "Identificador UUID inválido");
        }
        return UUID.fromString(valor);
    }

    private static String texto(JsonObject objeto, String campo, int maximo) {
        JsonValue valor = objeto.get(campo);
        if (valor == null || valor == JsonValue.NULL) return null;
        if (!(valor instanceof JsonString cadena)) {
            throw new DatoInvalido(400, "El campo " + campo + " debe ser texto");
        }
        String texto = cadena.getString().strip();
        if (texto.length() > maximo || texto.chars().anyMatch(c ->
                Character.isISOControl(c) && c != '\n' && c != '\t')) {
            throw new DatoInvalido(422, "El campo " + campo + " excede su límite o contiene caracteres de control");
        }
        return texto.isEmpty() ? null : texto;
    }

    public static class DatoInvalido extends IllegalArgumentException {
        private final int estado;

        public DatoInvalido(int estado, String mensaje) {
            super(mensaje);
            this.estado = estado;
        }

        public int getEstadoHttp() { return estado; }
    }
}
