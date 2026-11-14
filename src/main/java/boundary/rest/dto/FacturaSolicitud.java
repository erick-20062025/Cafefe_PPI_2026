package boundary.rest.dto;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record FacturaSolicitud(UUID idCaja, UUID idEmpleadoRol, String cliente,
        String observaciones, List<Detalle> productos) {

    public record Detalle(UUID idOrdenProducto, BigDecimal precio, String observaciones) {}

    public static FacturaSolicitud leer(String json, boolean creacion) {
        if (json == null || json.isBlank() || json.length() > 65536) {
            throw new DatoInvalido(400, "Debe enviar un objeto JSON de hasta 65536 caracteres");
        }
        try (var lector = Json.createReader(new StringReader(json))) {
            JsonValue valor = lector.readValue();
            if (!(valor instanceof JsonObject objeto)) {
                throw new DatoInvalido(400, "El cuerpo debe ser un objeto JSON");
            }
            campos(objeto, creacion
                    ? Set.of("idCaja", "idEmpleadoRol", "cliente", "observaciones", "productos")
                    : Set.of("idCaja", "idEmpleadoRol", "observaciones", "productos"));
            UUID caja = id(objeto, "idCaja");
            UUID empleado = id(objeto, "idEmpleadoRol");
            String cliente = texto(objeto, "cliente", 155);
            String observaciones = texto(objeto, "observaciones", 1000);
            JsonValue lista = objeto.get("productos");
            if (lista == null || lista == JsonValue.NULL) {
                throw new DatoInvalido(422, "Debe enviar los productos de la factura");
            }
            if (!(lista instanceof JsonArray array)) {
                throw new DatoInvalido(400, "productos debe ser una lista JSON");
            }
            if (array.isEmpty() || array.size() > 100) {
                throw new DatoInvalido(422, "La factura debe contener entre 1 y 100 detalles");
            }
            List<Detalle> detalles = new ArrayList<>();
            for (JsonValue item : array) {
                if (!(item instanceof JsonObject fila)) {
                    throw new DatoInvalido(400, "Cada detalle debe ser un objeto JSON");
                }
                campos(fila, Set.of("idOrdenProducto", "precio", "observaciones"));
                JsonValue precio = fila.get("precio");
                if (precio == null || precio == JsonValue.NULL) {
                    throw new DatoInvalido(422, "El precio es obligatorio");
                }
                if (!(precio instanceof JsonNumber numero)) {
                    throw new DatoInvalido(400, "El precio debe ser un número JSON");
                }
                BigDecimal decimal = numero.bigDecimalValue();
                // Evita exponentes que requieran expandir un número desproporcionado.
                if ((long) decimal.precision() - decimal.scale() > 65536) {
                    throw new DatoInvalido(422, "Precio fuera de rango");
                }
                detalles.add(new Detalle(id(fila, "idOrdenProducto"), decimal,
                        texto(fila, "observaciones", 1000)));
            }
            return new FacturaSolicitud(caja, empleado, cliente, observaciones, List.copyOf(detalles));
        } catch (JsonException | NumberFormatException ex) {
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
        if (!(valor instanceof jakarta.json.JsonString cadena)) {
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
