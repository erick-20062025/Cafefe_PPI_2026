package boundary.rest;

import boundary.rest.dto.ProductoTipoProductoSolicitud;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductoTipoProductoSolicitudTest {
    static final String ID = "11111111-1111-4111-8111-111111111111";
    static final String BASE = "{\"idProducto\":\"" + ID + "\",\"idTipoProducto\":\"" + ID + "\"";
    private void error(String json, int status) {
        assertEquals(status, assertThrows(ProductoTipoProductoSolicitud.DatoInvalido.class,
                () -> ProductoTipoProductoSolicitud.leer(json)).getEstadoHttp());
    }
    @Test void cuerpoYCamposDesconocidos() {
        for (String s : new String[]{null, "", " ", "{", "[]", "true"}) error(s, 400);
        for (String campo : new String[]{"idProductoTipoProducto", "fechaCreacion", "activo", "producto", "precio"}) {
            error(BASE + ",\"" + campo + "\":null}", 400);
        }
    }
    @Test void uuidObligatoriosYTiposEstrictos() {
        error("{}", 422);
        for (String campo : new String[]{"idProducto", "idTipoProducto"}) {
            for (String valor : new String[]{"null", "\"\"", "\"   \""}) {
                error((BASE + "}").replace("\"" + campo + "\":\"" + ID + "\"", "\"" + campo + "\":" + valor), 422);
            }
            for (String valor : new String[]{"1", "true", "{}", "[]", "\"1-1-1-1-1\"", "\"mal\""}) {
                error((BASE + "}").replace("\"" + campo + "\":\"" + ID + "\"", "\"" + campo + "\":" + valor), 400);
            }
        }
    }
    @Test void observacionesOpcionalesYNormalizadas() {
        var s = ProductoTipoProductoSolicitud.leer(BASE + ",\"observaciones\":\" nota\\nlinea \"}");
        assertEquals(UUID.fromString(ID), s.getIdProducto());
        assertEquals(UUID.fromString(ID), s.getIdTipoProducto());
        assertEquals("nota\nlinea", s.getObservaciones());
        assertNull(ProductoTipoProductoSolicitud.leer(BASE + "}").getObservaciones());
        assertNull(ProductoTipoProductoSolicitud.leer(BASE + ",\"observaciones\":null}").getObservaciones());
    }
    @Test void limitesYCaracteresDeControl() {
        error(BASE + ",\"observaciones\":42}", 400);
        error(BASE + ",\"observaciones\":\"a\\u0000b\"}", 422);
        error(BASE + ",\"observaciones\":\"" + "x".repeat(1001) + "\"}", 422);
        String json = BASE + ",\"observaciones\":\"" + "x".repeat(1000) + "\"}";
        assertEquals(1000, ProductoTipoProductoSolicitud.leer(json).getObservaciones().length());
        assertNotNull(ProductoTipoProductoSolicitud.leer(json + " ".repeat(8192-json.length())));
        error(json + " ".repeat(8193-json.length()), 400);
    }
}
