package boundary.rest.dto;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EfectivoRecibidoTest {
 @Test void conservaMontoAplicadoYRecibido() { var p=PagoSolicitud.leer("{\"monto\":5,\"tipoPago\":\"EFECTIVO\",\"efectivoRecibido\":10}"); assertEquals(0,p.getMonto().compareTo(new BigDecimal("5")));assertEquals(0,p.getEfectivoRecibido().compareTo(new BigDecimal("10"))); }
 @Test void rechazaMenorQueAbono() {assertThrows(PagoSolicitud.DatoInvalido.class,()->PagoSolicitud.leer("{\"monto\":5,\"tipoPago\":\"EFECTIVO\",\"efectivoRecibido\":4}"));}
 @Test void rechazaRecibidoEnTarjeta() {assertThrows(PagoSolicitud.DatoInvalido.class,()->PagoSolicitud.leer("{\"monto\":5,\"tipoPago\":\"TARJETA\",\"referenciaExterna\":\"TEST\",\"efectivoRecibido\":10}"));}
}
