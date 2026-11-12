package boundary.rest.dto;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Valida el JSON original sin las conversiones implícitas de JSON-B.
 * Esta clase no conoce la base de datos ni las entidades JPA.
 */
public final class OrdenSolicitud {
    private static final int MAX_CUERPO = 65536;
    private static final int MAX_NOMBRE = 100;
    private static final int MAX_NOTA = 400;
    private static final int MAX_LINEAS = 100;
    private static final int MAX_CANTIDAD = 20;
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final UUID idEmpleadoRol;
    private final Date fechaCreacion;
    private final String nombreCliente;
    private final List<ProductoSolicitado> productos;

    private OrdenSolicitud(UUID idEmpleadoRol, Date fechaCreacion,
            String nombreCliente, List<ProductoSolicitado> productos) {
        this.idEmpleadoRol = idEmpleadoRol;
        this.fechaCreacion = new Date(fechaCreacion.getTime());
        this.nombreCliente = nombreCliente;
        this.productos = List.copyOf(productos);
    }

    /**
     * @param publica true si se trata de un pedido sin autenticación.
     *                El camarero de ese pedido lo asignará el servidor.
     */
    public static OrdenSolicitud leer(String json, boolean publica) {
        JsonObject objeto = leerObjeto(json);
        Set<String> permitidos = publica
                ? Set.of("nombreCliente", "productos")
                : Set.of("idEmpleadoRol", "fechaCreacion", "nombreCliente", "productos");
        comprobarClaves(objeto, permitidos);

        UUID camarero = null;
        if (!publica) {
            camarero = uuid(objeto, "idEmpleadoRol");
        }

        String nombre = texto(objeto, "nombreCliente", publica, MAX_NOMBRE, false);
        if (publica && nombre == null) {
            throw new DatoInvalido(422, "El nombre del cliente es obligatorio");
        }

        Date fecha = new Date();
        if (!publica && objeto.containsKey("fechaCreacion")) {
            String valor = texto(objeto, "fechaCreacion", true, 45, false);
            if (valor == null) {
                throw new DatoInvalido(422, "La fecha de creación es obligatoria si se proporciona");
            }
            try {
                fecha = Date.from(OffsetDateTime.parse(valor).toInstant());
            } catch (DateTimeParseException ex) {
                throw new DatoInvalido(400,
                        "fechaCreacion debe usar ISO-8601 con zona, por ejemplo 2026-11-12T09:30:00-06:00");
            }
        }

        JsonValue lista = objeto.get("productos");
        if (lista == null || lista == JsonValue.NULL) {
            throw new DatoInvalido(422, "Debe proporcionar la lista de productos");
        }
        if (lista.getValueType() != JsonValue.ValueType.ARRAY) {
            throw new DatoInvalido(400, "productos debe ser una lista JSON");
        }
        if (lista.asJsonArray().isEmpty()) {
            throw new DatoInvalido(422, "La orden debe contener al menos un producto");
        }
        if (lista.asJsonArray().size() > MAX_LINEAS) {
            throw new DatoInvalido(422, "La orden supera el máximo de productos permitido");
        }
        List<ProductoSolicitado> filas = new ArrayList<>();
        int total = 0;
        for (JsonValue item : lista.asJsonArray()) {
            if (item == null || item.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new DatoInvalido(400, "Cada producto debe ser un objeto JSON");
            }
            JsonObject fila = item.asJsonObject();
            comprobarClaves(fila, Set.of("idProducto", "cantidad", "observaciones"));
            UUID producto = uuid(fila, "idProducto");
            int cantidad = enteroCantidad(fila);
            String observaciones = texto(fila, "observaciones", false, MAX_NOTA, true);
            total += cantidad;
            if (total > MAX_LINEAS) {
                throw new DatoInvalido(422, "La orden supera las 100 unidades permitidas");
            }
            filas.add(new ProductoSolicitado(producto, cantidad, observaciones));
        }
        return new OrdenSolicitud(camarero, fecha, nombre, filas);
    }

    /** Analiza un único producto para agregarlo a una orden ya creada. */
    public static ProductoSolicitado leerProducto(String json) {
        JsonObject obj = leerObjeto(json);
        comprobarClaves(obj, Set.of("idProducto", "cantidad", "observaciones"));
        return new ProductoSolicitado(uuid(obj, "idProducto"), enteroCantidad(obj),
                texto(obj, "observaciones", false, MAX_NOTA, true));
    }

    private static JsonObject leerObjeto(String json) {
        if (json == null || json.isBlank()) {
            throw new DatoInvalido(400, "Debe proporcionar un cuerpo JSON");
        }
        if (json.length() > MAX_CUERPO) {
            throw new DatoInvalido(400, "El JSON supera el tamaño permitido");
        }
        try (JsonReader lector = Json.createReader(new StringReader(json))) {
            JsonValue valor = lector.readValue();
            if (valor.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new DatoInvalido(400, "El cuerpo JSON debe ser un objeto");
            }
            return valor.asJsonObject();
        } catch (JsonException ex) {
            throw new DatoInvalido(400, "El cuerpo contiene un JSON inválido");
        }
    }

    private static void comprobarClaves(JsonObject obj, Set<String> permitidas) {
        for (String nombre : obj.keySet()) {
            if (!permitidas.contains(nombre)) {
                throw new DatoInvalido(400, "Campo desconocido o no permitido: " + nombre);
            }
        }
    }

    private static UUID uuid(JsonObject obj, String campo) {
        String valor = texto(obj, campo, true, 36, false);
        if (valor == null) {
            throw new DatoInvalido(422, "El campo " + campo + " es obligatorio");
        }
        if (!UUID_CANONICO.matcher(valor).matches()) {
            throw new DatoInvalido(400, "El campo " + campo + " debe ser un UUID válido");
        }
        return UUID.fromString(valor);
    }

    private static String texto(JsonObject obj, String campo, boolean obligatorio,
            int max, boolean permiteSaltosLinea) {
        JsonValue valor = obj.get(campo);
        if (valor == null || valor == JsonValue.NULL) {
            if (obligatorio) throw new DatoInvalido(422, "El campo " + campo + " es obligatorio");
            return null;
        }
        if (valor.getValueType() != JsonValue.ValueType.STRING) {
            throw new DatoInvalido(400, "El campo " + campo + " debe ser texto");
        }
        String texto = obj.getString(campo);
        // strip() no detecta ciertos espacios Unicode; los normalizamos en bordes.
        int inicio = 0;
        int fin = texto.length();
        while (inicio < fin && esEspacio(texto.charAt(inicio))) inicio++;
        while (fin > inicio && esEspacio(texto.charAt(fin - 1))) fin--;
        texto = texto.substring(inicio, fin);
        if (texto.isEmpty()) {
            if (obligatorio) throw new DatoInvalido(422, "El campo " + campo + " no puede estar vacío");
            return null;
        }
        if (texto.length() > max) {
            throw new DatoInvalido(422, "El campo " + campo + " supera " + max + " caracteres");
        }
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (Character.isISOControl(c)
                    && !(permiteSaltosLinea && (c == '\n' || c == '\t'))) {
                throw new DatoInvalido(422, "El campo " + campo + " contiene caracteres no permitidos");
            }
        }
        return texto;
    }

    private static boolean esEspacio(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u200B';
    }

    private static int enteroCantidad(JsonObject obj) {
        JsonValue valor = obj.get("cantidad");
        if (valor == null) return 1;
        if (valor == JsonValue.NULL) {
            throw new DatoInvalido(422, "cantidad no puede ser null");
        }
        if (valor.getValueType() != JsonValue.ValueType.NUMBER) {
            throw new DatoInvalido(400, "cantidad debe ser un entero JSON");
        }
        JsonNumber numero = (JsonNumber) valor;
        if (!numero.toString().matches("-?(0|[1-9][0-9]*)")) {
            throw new DatoInvalido(400, "cantidad no puede contener decimales ni exponentes");
        }
        int cantidad;
        try {
            cantidad = numero.intValueExact();
        } catch (ArithmeticException ex) {
            throw new DatoInvalido(422, "cantidad está fuera del rango admitido");
        }
        if (cantidad <= 0 || cantidad > MAX_CANTIDAD) {
            throw new DatoInvalido(422, "cantidad debe estar entre 1 y 20");
        }
        return cantidad;
    }

    public UUID getIdEmpleadoRol() { return idEmpleadoRol; }
    public Date getFechaCreacion() { return new Date(fechaCreacion.getTime()); }
    public String getNombreCliente() { return nombreCliente; }
    public List<ProductoSolicitado> getProductos() { return productos; }

    public static final class ProductoSolicitado {
        private final UUID idProducto;
        private final int cantidad;
        private final String observaciones;
        private ProductoSolicitado(UUID idProducto, int cantidad, String observaciones) {
            this.idProducto = idProducto;
            this.cantidad = cantidad;
            this.observaciones = observaciones;
        }
        public UUID getIdProducto() { return idProducto; }
        public int getCantidad() { return cantidad; }
        public String getObservaciones() { return observaciones; }
    }

    public static final class DatoInvalido extends RuntimeException {
        private final int estadoHttp;
        public DatoInvalido(int estadoHttp, String mensaje) {
            super(mensaje);
            this.estadoHttp = estadoHttp;
        }
        public int getEstadoHttp() { return estadoHttp; }
    }
}
