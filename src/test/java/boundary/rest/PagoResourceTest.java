package boundary.rest;

import boundary.rest.dto.PagoDTO;
import boundary.rest.messages.ApiResponse;
import control.PagoDAO;
import control.PagoDetalleDAO;
import entity.Factura;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pruebas REST aisladas: no alteran facturas reales. */
@ExtendWith(MockitoExtension.class)
class PagoResourceTest {

    private static final UUID FACTURA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    @Mock private PagoDAO pagoDAO;
    @Mock private PagoDetalleDAO detalleDAO;
    @InjectMocks private PagoResource recurso;

    @BeforeEach void iniciar() { RestTestRuntime.instalar(); }
    @AfterEach void finalizar() { RestTestRuntime.limpiar(); }

    private static PagoDAO.ResumenPago resumen(String total, String abonado, String saldo, boolean completo) {
        return new PagoDAO.ResumenPago(new BigDecimal(total), new BigDecimal(abonado),
                new BigDecimal(saldo), completo);
    }

    private static String efectivo(String monto) {
        return "{\"monto\":" + monto + ",\"tipoPago\":\"EFECTIVO\"}";
    }

    private static void error(Response respuesta, int codigo) {
        assertEquals(codigo, respuesta.getStatus());
        assertInstanceOf(ApiResponse.class, respuesta.getEntity());
    }

    @Test void rechazaUuidMaloSinAccederAlDao() {
        error(recurso.resumen("invalido"), 400);
        error(recurso.productos("invalido"), 400);
        error(recurso.abonos("invalido"), 400);
        error(recurso.registrarAbono("invalido", efectivo("1")), 400);
        verifyNoInteractions(pagoDAO, detalleDAO);
    }

    @Test void rechazaDatosVaciosYTiposIncorrectosSinAlterarPago() {
        error(recurso.registrarAbono(FACTURA.toString(), "{}"), 422);
        error(recurso.registrarAbono(FACTURA.toString(), efectivo("0")), 422);
        error(recurso.registrarAbono(FACTURA.toString(), efectivo("-4")), 422);
        error(recurso.registrarAbono(FACTURA.toString(), efectivo("\"1\"")), 400);
        error(recurso.registrarAbono(FACTURA.toString(), "{"), 400);
        verifyNoInteractions(pagoDAO, detalleDAO);
    }

    @Test void crearAbonoEnEfectivoRetorna201YSaldoExacto() {
        when(pagoDAO.registrarAbono(eq(FACTURA), eq(new BigDecimal("5.00")),
                eq("EFECTIVO"), isNull(), eq("nota")))
                .thenReturn(resumen("20.00", "5.00", "15.00", false));
        Response respuesta = recurso.registrarAbono(FACTURA.toString(),
                "{\"monto\":5,\"tipoPago\":\"efectivo\",\"observaciones\":\" nota \"}");
        assertEquals(201, respuesta.getStatus());
        PagoDTO.Resumen dto = assertInstanceOf(PagoDTO.Resumen.class, respuesta.getEntity());
        assertEquals(FACTURA, dto.getIdFactura());
        assertEquals(new BigDecimal("15.00"), dto.getSaldo());
        assertFalse(dto.isCompletado());
    }

    @Test void pagoCompletoRetornaSaldoCero() {
        when(pagoDAO.registrarAbono(eq(FACTURA), any(), eq("TRANSFERENCIA"), eq("ABC-001"), isNull()))
                .thenReturn(resumen("20.00", "20.00", "0.00", true));
        Response respuesta = recurso.registrarAbono(FACTURA.toString(),
                "{\"monto\":20,\"tipoPago\":\"TRANSFERENCIA\",\"referenciaExterna\":\"ABC-001\"}");
        assertEquals(201, respuesta.getStatus());
        PagoDTO.Resumen dto = (PagoDTO.Resumen) respuesta.getEntity();
        assertTrue(dto.isCompletado());
        assertEquals(new BigDecimal("0.00"), dto.getSaldo());
    }

    @Test void facturaInexistenteRetorna404AunqueEjbEnvuelvaExcepcion() {
        when(pagoDAO.consultarResumen(FACTURA)).thenThrow(
                new RuntimeException(new IllegalArgumentException("La factura seleccionada no existe")));
        error(recurso.resumen(FACTURA.toString()), 404);
    }

    @Test void rechazoDeSobrepagoRetorna422() {
        when(pagoDAO.registrarAbono(eq(FACTURA), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("El abono supera el saldo pendiente de $5.00"));
        error(recurso.registrarAbono(FACTURA.toString(), efectivo("7")), 422);
    }

    @Test void facturaPagadaYReferenciaDuplicadaRetornan409() {
        when(pagoDAO.registrarAbono(eq(FACTURA), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Solo se pueden registrar abonos a facturas pendientes"))
                .thenThrow(new RuntimeException(new IllegalArgumentException(
                        "La referencia de este pago ya fue registrada en la factura")));
        error(recurso.registrarAbono(FACTURA.toString(), efectivo("1")), 409);
        error(recurso.registrarAbono(FACTURA.toString(),
                "{\"monto\":1,\"tipoPago\":\"TARJETA\",\"referenciaExterna\":\"R-01\"}"), 409);
    }

    @Test void errorTecnicoNoRevelaCredencialesNiSql() {
        when(pagoDAO.registrarAbono(eq(FACTURA), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("jdbc:postgresql://admin:secreto SQL SELECT pago"));
        Response r = recurso.registrarAbono(FACTURA.toString(), efectivo("1"));
        error(r, 500);
        String detalle = ((ApiResponse) r.getEntity()).getDetalle();
        assertFalse(detalle.contains("secreto"));
        assertFalse(detalle.contains("SELECT"));
    }

    @Test void listaPendientesYPagadasDevuelveDtos() {
        Factura f = new Factura(FACTURA);
        f.setEstado("PENDIENTE");
        when(pagoDAO.listarFacturasPendientes()).thenReturn(List.of(f));
        when(pagoDAO.listarFacturasPagadas()).thenReturn(List.of());
        Response pendientes = recurso.pendientes();
        Response pagadas = recurso.pagadas();
        assertEquals(200, pendientes.getStatus());
        assertEquals(200, pagadas.getStatus());
        assertInstanceOf(PagoDTO.FacturaItem.class, ((List<?>) pendientes.getEntity()).get(0));
        assertTrue(((List<?>) pagadas.getEntity()).isEmpty());
    }

    @Test void consultaResumenYSoloDetallesDeUnaFactura() {
        when(pagoDAO.consultarResumen(FACTURA))
                .thenReturn(resumen("12.00", "2.00", "10.00", false));
        when(pagoDAO.obtenerDetallesFactura(FACTURA)).thenReturn(List.of());
        when(detalleDAO.findByFactura(FACTURA)).thenReturn(List.of());
        assertEquals(200, recurso.resumen(FACTURA.toString()).getStatus());
        Response productos = recurso.productos(FACTURA.toString());
        Response abonos = recurso.abonos(FACTURA.toString());
        assertEquals(200, productos.getStatus());
        assertEquals(200, abonos.getStatus());
        assertTrue(((List<?>) productos.getEntity()).isEmpty());
        assertTrue(((List<?>) abonos.getEntity()).isEmpty());
        verify(pagoDAO, times(3)).consultarResumen(FACTURA);
        verify(pagoDAO).obtenerDetallesFactura(FACTURA);
        verify(detalleDAO).findByFactura(FACTURA);
    }

    @Test void consultaFallidaDePostgresqlRetorna500Seguro() {
        when(pagoDAO.listarFacturasPendientes()).thenThrow(new RuntimeException("password=abc"));
        Response respuesta = recurso.pendientes();
        error(respuesta, 500);
        assertFalse(((ApiResponse) respuesta.getEntity()).getDetalle().contains("password"));
    }
}
