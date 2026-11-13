package boundary.rest;

import boundary.rest.dto.OrdenSolicitud;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de formatos JSON originales, no conversiones permisivas de JSON-B. */
class OrdenSolicitudTest {
    private static final String CAMARERO = "11111111-1111-4111-8111-111111111111";
    private static final String PRODUCTO = "22222222-2222-4222-8222-222222222222";

    private static String interna(String productos) {
        return "{\"idEmpleadoRol\":\"" + CAMARERO + "\",\"productos\":" + productos + "}";
    }
    private static String fila(String extra) {
        return "[{\"idProducto\":\"" + PRODUCTO + "\"" + extra + "}]";
    }
    private static void invalido(String json, boolean publica, int http) {
        OrdenSolicitud.DatoInvalido ex = assertThrows(OrdenSolicitud.DatoInvalido.class,
                () -> OrdenSolicitud.leer(json, publica));
        assertEquals(http, ex.getEstadoHttp());
    }

    @Test void rechazaCuerposNulosVaciosYMalformed() {
        invalido(null, false, 400);
        invalido("", false, 400);
        invalido("   ", false, 400);
        invalido("{", false, 400);
        invalido("[]", false, 400);
        invalido("null", false, 400);
        invalido("{}", false, 422);
        invalido(" ".repeat(65537), false, 400);
    }

    @Test void rechazaTiposIncompatibles() {
        invalido("{\"idEmpleadoRol\":123,\"productos\":[]}", false, 400);
        invalido(interna("\"hola\""), false, 400);
        invalido(interna("[1]"), false, 400);
        invalido(interna(fila(",\"cantidad\":\"2\"")), false, 400);
        invalido(interna(fila(",\"cantidad\":1.5")), false, 400);
        invalido(interna(fila(",\"cantidad\":1e1")), false, 400);
        invalido(interna(fila(",\"observaciones\":true")), false, 400);
        invalido(interna(fila(",\"precio\":0.01")), false, 400);
    }

    @Test void rechazaProductosNulosVaciosYMasivos() {
        invalido(interna("null"), false, 422);
        invalido(interna("[]"), false, 422);
        invalido(interna(fila(",\"cantidad\":0")), false, 422);
        invalido(interna(fila(",\"cantidad\":-1")), false, 422);
        invalido(interna(fila(",\"cantidad\":21")), false, 422);
        invalido(interna(fila(",\"cantidad\":999999999999999999999")), false, 422);
        invalido(interna(fila(",\"cantidad\":null")), false, 422);
        StringBuilder total = new StringBuilder("[");
        for (int i = 0; i < 6; i++) {
            if (i > 0) total.append(',');
            total.append("{\"idProducto\":\"").append(PRODUCTO).append("\",\"cantidad\":20}");
        }
        total.append(']');
        invalido(interna(total.toString()), false, 422);
    }

    @Test void rechazaUuidsYCamposExtranios() {
        invalido(interna("[{\"cantidad\":1}]"), false, 422);
        invalido(interna("[{\"idProducto\":\"abc\"}]"), false, 400);
        invalido(interna(fila(",\"idOrden\":\"no\"")), false, 400);
        invalido("{\"idEmpleadoRol\":\"" + CAMARERO + "\",\"idOrden\":\"x\",\"productos\":[]}", false, 400);
        invalido("{\"idEmpleadoRol\":\"xx\",\"productos\":[]}", false, 400);
    }

    @Test void rechazaNombresIncoherentesYCaracteresControl() {
        invalido("{\"nombreCliente\":\"   \",\"productos\":" + fila("") + "}", true, 422);
        invalido("{\"nombreCliente\":12,\"productos\":" + fila("") + "}", true, 400);
        invalido("{\"nombreCliente\":\"" + "x".repeat(101) + "\",\"productos\":" + fila("") + "}", true, 422);
        invalido("{\"nombreCliente\":\"Cliente\\u0000\",\"productos\":" + fila("") + "}", true, 422);
        invalido("{\"productos\":" + fila("") + "}", true, 422);
    }

    @Test void pedidoPublicoNoAceptaCamareroArbitrario() {
        invalido("{\"nombreCliente\":\"Ana\",\"idEmpleadoRol\":\"" + CAMARERO
                + "\",\"productos\":" + fila("") + "}", true, 400);
        invalido("{\"nombreCliente\":\"Ana\",\"fechaCreacion\":\"2020\",\"productos\":"
                + fila("") + "}", true, 400);
    }

    @Test void fechaInternaExigeZonaHoraria() {
        String base = "{\"idEmpleadoRol\":\"" + CAMARERO + "\",\"fechaCreacion\":%s,\"productos\":" + fila("") + "}";
        invalido(String.format(base, "\"2026-11-12\""), false, 400);
        invalido(String.format(base, "34"), false, 400);
        invalido(String.format(base, "null"), false, 422);
        OrdenSolicitud valida = OrdenSolicitud.leer(String.format(base,
                "\"2025-04-01T10:20:00-06:00\""), false);
        assertNotNull(valida.getFechaCreacion());
    }

    @Test void parseaSolicitudValidaYExpandeCantidadSinMutarOrigen() {
        OrdenSolicitud solicitud = OrdenSolicitud.leer(interna(fila(",\"cantidad\":3,\"observaciones\":\" sin cebolla \"")), false);
        assertEquals(UUID.fromString(CAMARERO), solicitud.getIdEmpleadoRol());
        assertEquals(UUID.fromString(PRODUCTO), solicitud.getProductos().get(0).getIdProducto());
        assertEquals(3, solicitud.getProductos().get(0).getCantidad());
        assertEquals("sin cebolla", solicitud.getProductos().get(0).getObservaciones());
        assertThrows(UnsupportedOperationException.class,
                () -> solicitud.getProductos().clear());
    }

    @Test void permiteClienteConNotasYProductoRepetido() {
        OrdenSolicitud solicitud = OrdenSolicitud.leer("{\"nombreCliente\":\"Ana\",\"productos\":"
                + fila("") + "}", true);
        assertEquals("Ana", solicitud.getNombreCliente());
        assertNull(solicitud.getIdEmpleadoRol());
        assertEquals(1, solicitud.getProductos().size());
    }

    @Test void productoIndividualRechazaEntradasMalformadas() {
        assertThrows(OrdenSolicitud.DatoInvalido.class,
                () -> OrdenSolicitud.leerProducto("{\"idProducto\":false}"));
        assertThrows(OrdenSolicitud.DatoInvalido.class,
                () -> OrdenSolicitud.leerProducto("{\"idProducto\":\"" + PRODUCTO + "\",\"cantidad\":0}"));
        assertEquals(1, OrdenSolicitud.leerProducto("{\"idProducto\":\"" + PRODUCTO + "\"}").getCantidad());
    }
}
