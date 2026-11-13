package boundary.rest;

import boundary.rest.dto.DescuentoSolicitud;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Valida campos y tipos JSON antes de consultar cualquier DAO. */
class DescuentoSolicitudTest {

    private static final String TIPO = "11111111-1111-4111-8111-111111111111";
    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    private static String futuro() { return LocalDate.now(ZONA).plusDays(3).toString(); }

    private static String valido() {
        return "{\"idTipoDescuento\":\"" + TIPO
                + "\",\"nombre\":\"Oferta de prueba\",\"fechaDesde\":\"" + futuro() + "\"}";
    }

    private static void invalida(String json, int esperado) {
        DescuentoSolicitud.DatoInvalido ex = assertThrows(DescuentoSolicitud.DatoInvalido.class,
                () -> DescuentoSolicitud.leer(json));
        assertEquals(esperado, ex.getEstadoHttp());
    }

    @Test void rechazaJsonVacioMalformadoYTipoNoObjeto() {
        invalida(null, 400);
        invalida("", 400);
        invalida("   ", 400);
        invalida("{", 400);
        invalida("[]", 400);
        invalida("true", 400);
    }

    @Test void validaTipoObligatorioYUuidCanonico() {
        invalida("{}", 422);
        invalida("{\"nombre\":\"Oferta\",\"fechaDesde\":\"" + futuro() + "\"}", 422);
        invalida(valido().replace(TIPO, " "), 422);
        invalida(valido().replace(TIPO, "no-uuid"), 400);
        invalida(valido().replace("\"" + TIPO + "\"", "12"), 400);
        invalida(valido().replace("\"" + TIPO + "\"", "null"), 422);
    }

    @Test void nombreNoPuedeSerVacioNiInvalido() {
        invalida(valido().replace("Oferta de prueba", ""), 422);
        invalida(valido().replace("Oferta de prueba", " "), 422);
        invalida(valido().replace("Oferta de prueba", "a"), 422);
        invalida(valido().replace("Oferta de prueba", "x".repeat(156)), 422);
        invalida(valido().replace("\"Oferta de prueba\"", "33"), 400);
        invalida(valido().replace("Oferta de prueba", "Ofer\\u0000ta"), 422);
    }

    @Test void validaFechaDesdeObligatoriaYConFormatoCorrecto() {
        invalida(valido().replace(",\"fechaDesde\":\"" + futuro() + "\"", ""), 422);
        invalida(valido().replace(futuro(), " "), 422);
        invalida(valido().replace("\"" + futuro() + "\"", "12"), 400);
        invalida(valido().replace(futuro(), "2026-13-80"), 400);
        invalida(valido().replace(futuro(), "2026-11-15T10:00:00"), 400);
    }

    @Test void validaFechaHastaOpcionalYOrdenDelRango() {
        invalida(valido().replace("}", ",\"fechaHasta\":\"1990-01-01\"}"), 422);
        invalida(valido().replace("}", ",\"fechaHasta\":\"2026-15-01\"}"), 400);
        invalida(valido().replace("}", ",\"fechaHasta\":false}"), 400);
        DescuentoSolicitud a = DescuentoSolicitud.leer(valido());
        assertNull(a.getFechaHasta());
        DescuentoSolicitud b = DescuentoSolicitud.leer(valido().replace("}", ",\"fechaHasta\":null}"));
        assertNull(b.getFechaHasta());
    }

    @Test void observacionesSonOpcionalesYConLimite() {
        invalida(valido().replace("}", ",\"observaciones\":123}"), 400);
        invalida(valido().replace("}", ",\"observaciones\":\"" + "x".repeat(1001) + "\"}"), 422);
        invalida(valido().replace("}", ",\"observaciones\":\"a\\u0000b\"}"), 422);
        assertNull(DescuentoSolicitud.leer(valido()).getObservaciones());
    }

    @Test void rechazaCamposProhibidosEIdManipulado() {
        invalida(valido().replace("}", ",\"idDescuento\":\"hack\"}"), 400);
        invalida(valido().replace("}", ",\"descuentoProductoCollection\":[]}"), 400);
    }

    @Test void aceptaFechasCortasYNormalizaTexto() {
        DescuentoSolicitud solicitud = DescuentoSolicitud.leer(valido()
                .replace("Oferta de prueba", "  Descuento útil  ")
                .replace("}", ",\"observaciones\":\"   comentario   \"}"));
        assertEquals("Descuento útil", solicitud.getNombre());
        assertEquals("comentario", solicitud.getObservaciones());
        assertEquals(TIPO, solicitud.getIdTipoDescuento().toString());
        assertEquals(futuro(), solicitud.getFechaDesde().toInstant().atZone(ZONA).toLocalDate().toString());
    }

    @Test void aceptaFechaConZonaHorariaYFinAbierto() {
        String cuerpo = valido().replace("\"" + futuro() + "\"", "\""
                + futuro() + "T13:30:00-06:00\"");
        DescuentoSolicitud sol = DescuentoSolicitud.leer(cuerpo);
        assertEquals(13, sol.getFechaDesde().toInstant().atZone(ZONA).getHour());
        assertNull(sol.getFechaHasta());
    }
}
