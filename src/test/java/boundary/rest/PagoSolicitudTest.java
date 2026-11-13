package boundary.rest;

import boundary.rest.dto.PagoSolicitud;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Validaciones de entrada: no requiere base de datos ni Liberty. */
class PagoSolicitudTest {

    private static void invalida(String json, int codigo) {
        PagoSolicitud.DatoInvalido e = assertThrows(PagoSolicitud.DatoInvalido.class,
                () -> PagoSolicitud.leer(json));
        assertEquals(codigo, e.getEstadoHttp());
    }

    @Test void rechazaCuerposVaciosYJsonMalformado() {
        invalida(null, 400);
        invalida("", 400);
        invalida("  ", 400);
        invalida("{", 400);
        invalida("[]", 400);
    }

    @Test void validaMontoObligatorio() {
        invalida("{}", 422);
        invalida("{\"tipoPago\":\"EFECTIVO\"}", 422);
        invalida("{\"tipoPago\":\"EFECTIVO\",\"monto\":null}", 422);
    }

    @Test void rechazaMontosIncoherentes() {
        for (String valor : new String[]{"0", "-1", "1.234", "1000000000000"}) {
            invalida("{\"monto\":" + valor + ",\"tipoPago\":\"EFECTIVO\"}", 422);
        }
    }

    @Test void rechazaMontosDeTipoIncorrecto() {
        for (String valor : new String[]{"\"4.50\"", "true", "[]", "{}"}) {
            invalida("{\"monto\":" + valor + ",\"tipoPago\":\"EFECTIVO\"}", 400);
        }
    }

    @Test void validaMetodoDePago() {
        invalida("{\"monto\":1}", 422);
        invalida("{\"monto\":1,\"tipoPago\":null}", 422);
        invalida("{\"monto\":1,\"tipoPago\":\"    \"}", 422);
        invalida("{\"monto\":1,\"tipoPago\":123}", 400);
        invalida("{\"monto\":1,\"tipoPago\":\"BITCOIN\"}", 422);
    }

    @Test void referenciasSonObligatoriasEnMetodosNoEfectivo() {
        for (String metodo : new String[]{"TARJETA", "TRANSFERENCIA", "QR"}) {
            invalida("{\"monto\":1,\"tipoPago\":\"" + metodo + "\"}", 422);
            invalida("{\"monto\":1,\"tipoPago\":\"" + metodo
                    + "\",\"referenciaExterna\":\"   \"}", 422);
        }
    }

    @Test void rechazaClavesDesconocidasYCamposDeTipoIncorrecto() {
        invalida("{\"monto\":1,\"tipoPago\":\"EFECTIVO\",\"estado\":\"PAGADA\"}", 400);
        invalida("{\"monto\":1,\"tipoPago\":\"EFECTIVO\",\"observaciones\":10}", 400);
        invalida("{\"monto\":1,\"tipoPago\":\"EFECTIVO\",\"referenciaExterna\":false}", 400);
    }

    @Test void rechazaObservacionesExcesivasYCaracteresControl() {
        invalida("{\"monto\":1,\"tipoPago\":\"EFECTIVO\",\"observaciones\":\""
                + "x".repeat(1001) + "\"}", 422);
        invalida("{\"monto\":1,\"tipoPago\":\"EFECTIVO\",\"referenciaExterna\":\"a\\u0000b\"}", 422);
    }

    @Test void aceptaEfectivoYNormalizaValores() {
        PagoSolicitud s = PagoSolicitud.leer("{\"monto\":2.5,\"tipoPago\":\" efectivo \","
                + "\"observaciones\":\"  abono parcial  \"}");
        assertEquals(new BigDecimal("2.50"), s.getMonto());
        assertEquals("EFECTIVO", s.getTipoPago());
        assertNull(s.getReferenciaExterna());
        assertEquals("abono parcial", s.getObservaciones());
    }

    @Test void aceptaTransferenciaConReferencia() {
        PagoSolicitud s = PagoSolicitud.leer("{\"monto\":10.00,\"tipoPago\":\" transferencia \","
                + "\"referenciaExterna\":\"  TRF-001 \"}");
        assertEquals(new BigDecimal("10.00"), s.getMonto());
        assertEquals("TRANSFERENCIA", s.getTipoPago());
        assertEquals("TRF-001", s.getReferenciaExterna());
    }
}
