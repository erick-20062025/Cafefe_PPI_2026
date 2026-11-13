package boundary.rest;

import boundary.rest.dto.DescuentoProductoSolicitud;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Validaciones puras del JSON, sin acceso a DAO ni PostgreSQL. */
class DescuentoProductoSolicitudTest {

    private static final String DESCUENTO = "11111111-1111-4111-8111-111111111111";
    private static final String PRODUCTO = "22222222-2222-4222-8222-222222222222";
    private static final String FECHA = "2026-11-18";

    private static String json() {
        return "{\"idDescuento\":\"" + DESCUENTO + "\",\"idProducto\":\"" + PRODUCTO
                + "\",\"valor\":10,\"fechaDesde\":\"" + FECHA + "\"}";
    }

    private static void invalida(String entrada, int estado) {
        DescuentoProductoSolicitud.DatoInvalido ex = assertThrows(
                DescuentoProductoSolicitud.DatoInvalido.class,
                () -> DescuentoProductoSolicitud.leer(entrada));
        assertEquals(estado, ex.getEstadoHttp());
    }

    @Test void validaJsonVacioMalformadoYTipoRaiz() {
        invalida(null, 400);
        invalida("  ", 400);
        invalida("{", 400);
        invalida("[]", 400);
        invalida("{}", 422);
        invalida(" ".repeat(8193), 400);
    }

    @Test void exigeIdDeDescuentoSinConvertirTipos() {
        invalida(json().replace("\"idDescuento\":\"" + DESCUENTO + "\",", ""), 422);
        invalida(json().replace("\"" + DESCUENTO + "\"", "null"), 422);
        invalida(json().replace("\"" + DESCUENTO + "\"", "\"   \""), 422);
        invalida(json().replace("\"" + DESCUENTO + "\"", "7"), 400);
        invalida(json().replace(DESCUENTO, "uuid-invalido"), 400);
    }

    @Test void exigeIdDeProductoSinConvertirTipos() {
        invalida(json().replace("\"idProducto\":\"" + PRODUCTO + "\",", ""), 422);
        invalida(json().replace("\"" + PRODUCTO + "\"", "null"), 422);
        invalida(json().replace("\"" + PRODUCTO + "\"", "\"  \""), 422);
        invalida(json().replace("\"" + PRODUCTO + "\"", "false"), 400);
        invalida(json().replace(PRODUCTO, "mal-uuid"), 400);
    }

    @Test void exigePorcentajeEnteroValido() {
        invalida(json().replace("\"valor\":10,", ""), 422);
        invalida(json().replace("\"valor\":10", "\"valor\":null"), 422);
        invalida(json().replace("\"valor\":10", "\"valor\":\"10\""), 400);
        invalida(json().replace("\"valor\":10", "\"valor\":true"), 400);
        invalida(json().replace("\"valor\":10", "\"valor\":12.5"), 400);
        invalida(json().replace("\"valor\":10", "\"valor\":0"), 422);
        invalida(json().replace("\"valor\":10", "\"valor\":-4"), 422);
        invalida(json().replace("\"valor\":10", "\"valor\":101"), 422);
        invalida(json().replace("\"valor\":10", "\"valor\":99999999999999999999"), 422);
    }

    @Test void fechaDesdeObligatoriaYConZonaEnFechaCompleta() {
        invalida(json().replace(",\"fechaDesde\":\"" + FECHA + "\"", ""), 422);
        invalida(json().replace("\"" + FECHA + "\"", "null"), 422);
        invalida(json().replace("\"" + FECHA + "\"", "\" \""), 422);
        invalida(json().replace("\"" + FECHA + "\"", "20261118"), 400);
        invalida(json().replace(FECHA, "2026-13-40"), 400);
        invalida(json().replace(FECHA, "2026-11-18T10:30:00"), 400);
    }

    @Test void fechaHastaOpcionalPeroNoAnterior() {
        assertNull(DescuentoProductoSolicitud.leer(json()).getFechaHasta());
        assertNull(DescuentoProductoSolicitud.leer(json().replace("}", ",\"fechaHasta\":null}")).getFechaHasta());
        invalida(json().replace("}", ",\"fechaHasta\":\"2026-11-17\"}"), 422);
        invalida(json().replace("}", ",\"fechaHasta\":\"error\"}"), 400);
        invalida(json().replace("}", ",\"fechaHasta\":0}"), 400);
    }

    @Test void observacionesOpcionalesLimitadasYSinCaracteresControl() {
        assertNull(DescuentoProductoSolicitud.leer(json()).getObservaciones());
        assertNull(DescuentoProductoSolicitud.leer(json().replace("}", ",\"observaciones\":null}")).getObservaciones());
        invalida(json().replace("}", ",\"observaciones\":42}"), 400);
        invalida(json().replace("}", ",\"observaciones\":\"" + "x".repeat(1001) + "\"}"), 422);
        invalida(json().replace("}", ",\"observaciones\":\"a\\u0000b\"}"), 422);
    }

    @Test void rechazaCamposAjenoYIdElegidoPorCliente() {
        invalida(json().replace("}", ",\"idDescuentoProducto\":\"" + UUID.randomUUID() + "\"}"), 400);
        invalida(json().replace("}", ",\"descuentoProductoCollection\":[]}"), 400);
        invalida(json().replace("}", ",\"idProducto.nombre\":\"mal\"}"), 400);
    }

    @Test void aceptaUUIDCanonicosYFechaCorta() {
        DescuentoProductoSolicitud s = DescuentoProductoSolicitud.leer(json());
        assertEquals(UUID.fromString(DESCUENTO), s.getIdDescuento());
        assertEquals(UUID.fromString(PRODUCTO), s.getIdProducto());
        assertEquals(10, s.getValor());
        assertNotNull(s.getFechaDesde());
    }

    @Test void aceptaFechasISOConOffsetYTextoNormalizado() {
        String entrada = json().replace(FECHA, "2026-11-18T10:00:00-06:00")
                .replace("}", ",\"fechaHasta\":\"2026-11-21T23:00:00-06:00\","
                        + "\"observaciones\":\"  Prueba de fecha  \"}");
        DescuentoProductoSolicitud s = DescuentoProductoSolicitud.leer(entrada);
        assertEquals("Prueba de fecha", s.getObservaciones());
        assertNotNull(s.getFechaHasta());
        assertTrue(s.getFechaHasta().after(s.getFechaDesde()));
    }

    @Test void defensivoFechasNoSeExponenMutables() {
        DescuentoProductoSolicitud s = DescuentoProductoSolicitud.leer(json());
        long original = s.getFechaDesde().getTime();
        s.getFechaDesde().setTime(0);
        assertEquals(original, s.getFechaDesde().getTime());
    }
}
