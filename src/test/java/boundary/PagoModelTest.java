package boundary;

import control.PagoDAO;
import control.PagoDetalleDAO;
import entity.Factura;
import entity.FacturaOrdenProducto;
import entity.PagoDetalle;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoModelTest {

    @Mock PagoDAO pagoDAO;
    @Mock PagoDetalleDAO pagoDetalleDAO;
    private ModeloPrueba modelo;

    private static class ModeloPrueba extends PagoModel {
        String exito, advertencia, error;
        @Override protected void mostrarExito(String mensaje) { exito = mensaje; }
        @Override protected void mostrarAdvertencia(String mensaje) { advertencia = mensaje; }
        @Override protected void mostrarError(String mensaje) { error = mensaje; }
    }

    @BeforeEach
    void preparar() throws Exception {
        modelo = new ModeloPrueba();
        Field dao = PagoModel.class.getDeclaredField("pagoDAO");
        dao.setAccessible(true);
        dao.set(modelo, pagoDAO);
        Field detalle = PagoModel.class.getDeclaredField("pagoDetalleDAO");
        detalle.setAccessible(true);
        detalle.set(modelo, pagoDetalleDAO);
    }

    private Factura factura() {
        Factura f = new Factura(UUID.randomUUID());
        f.setEstado("PENDIENTE");
        f.setCliente("Cliente de prueba");
        return f;
    }

    private PagoDAO.ResumenPago resumen(String total, String abonado, String saldo, boolean completo) {
        return new PagoDAO.ResumenPago(new BigDecimal(total), new BigDecimal(abonado),
                new BigDecimal(saldo), completo);
    }

    private void seleccionar(Factura factura) {
        UUID id = factura.getIdFactura();
        when(pagoDAO.consultarResumen(id)).thenReturn(resumen("15.00", "0.00", "15.00", false));
        when(pagoDAO.obtenerDetallesFactura(id)).thenReturn(
                List.of(new FacturaOrdenProducto(UUID.randomUUID())));
        when(pagoDetalleDAO.findByFactura(id)).thenReturn(List.of());
        modelo.seleccionarFactura(factura);
    }

    @Test
    void debeAbrirPestanaDeCobroAlSeleccionarFactura() {
        Factura factura = factura();
        assertEquals(0, modelo.getIndicePestana());
        seleccionar(factura);
        assertEquals(1, modelo.getIndicePestana());
    }

    @Test
    void regresarAlListadoNoDebeBorrarAbonosGuardados() {
        Factura factura = factura();
        seleccionar(factura);
        when(pagoDAO.listarFacturasPendientes()).thenReturn(List.of(factura));
        modelo.volverAlListado();
        assertEquals(0, modelo.getIndicePestana());
        assertSame(factura, modelo.getFacturaSeleccionada());
        verify(pagoDAO, never()).registrarAbono(any(), any(), any(), any(), any());
    }

    @Test
    void alCerrarDebeVolverALaPrimeraPestana() {
        Factura factura = factura();
        seleccionar(factura);
        modelo.cancelarSeleccion();
        assertEquals(0, modelo.getIndicePestana());
        assertNull(modelo.getFacturaSeleccionada());
    }

    @Test
    void soloPagosNoEfectivoRequierenReferencia() {
        assertFalse(modelo.isRequiereReferencia());
        modelo.setTipoPago("EFECTIVO");
        assertFalse(modelo.isRequiereReferencia());
        modelo.setTipoPago("tarjeta");
        assertTrue(modelo.isRequiereReferencia());
        modelo.setTipoPago("TRANSFERENCIA");
        assertTrue(modelo.isRequiereReferencia());
        modelo.setTipoPago("QR");
        assertTrue(modelo.isRequiereReferencia());
    }

    @Test
    void inicioDebeConsultarFacturasPendientes() {
        Factura factura = factura();
        when(pagoDAO.listarFacturasPendientes()).thenReturn(List.of(factura));
        modelo.init();
        assertEquals(List.of(factura), modelo.getFacturasPendientes());
    }

    @Test
    void noDebeSeleccionarFacturaNula() {
        modelo.seleccionarFactura(null);
        assertNotNull(modelo.advertencia);
        verifyNoInteractions(pagoDAO, pagoDetalleDAO);
    }

    @Test
    void debeMostrarDetalleDeFacturaSeleccionada() {
        Factura f = factura();
        seleccionar(f);
        assertSame(f, modelo.getFacturaSeleccionada());
        assertEquals(new BigDecimal("15.00"), modelo.getResumen().getSaldo());
        assertEquals(1, modelo.getProductosFactura().size());
    }

    @Test
    void noDebeRegistrarSiNoHayFacturaSeleccionada() {
        modelo.registrarPago();
        assertNotNull(modelo.advertencia);
        verifyNoInteractions(pagoDAO, pagoDetalleDAO);
    }

    @Test
    void abonoParcialDebeConservarFacturaYSaldos() {
        Factura f = factura();
        seleccionar(f);
        when(pagoDAO.registrarAbono(f.getIdFactura(),
                new BigDecimal("5.00"), "EFECTIVO", null, null))
                .thenReturn(resumen("15.00", "5.00", "10.00", false));
        when(pagoDAO.listarFacturasPendientes()).thenReturn(List.of(f));
        modelo.setTipoPago("EFECTIVO");
        modelo.setMonto(new BigDecimal("5.00"));
        modelo.registrarPago();
        assertNotNull(modelo.exito);
        assertEquals(new BigDecimal("10.00"), modelo.getResumen().getSaldo());
        assertSame(f, modelo.getFacturaSeleccionada());
        assertFalse(modelo.isFacturaPagada());
    }

    @Test
    void abonoCompletoDebeHabilitarImpresion() {
        Factura f = factura();
        seleccionar(f);
        when(pagoDAO.registrarAbono(f.getIdFactura(),
                new BigDecimal("15.00"), "TARJETA", "ABC", null))
                .thenReturn(resumen("15.00", "15.00", "0.00", true));
        when(pagoDAO.listarFacturasPendientes()).thenReturn(List.of());
        modelo.setTipoPago("TARJETA");
        modelo.setMonto(new BigDecimal("15.00"));
        modelo.setReferenciaExterna("ABC");
        modelo.registrarPago();
        assertTrue(modelo.isFacturaPagada());
        assertEquals("PAGADA", f.getEstado());
        assertNotNull(modelo.exito);
    }

    @Test
    void errorDeNegocioNoDebeLimpiarMontoNiConfirmar() {
        Factura f = factura();
        seleccionar(f);
        when(pagoDAO.registrarAbono(f.getIdFactura(),
                new BigDecimal("20.00"), "EFECTIVO", null, null))
                .thenThrow(new IllegalArgumentException("El abono supera el saldo pendiente"));
        modelo.setTipoPago("EFECTIVO");
        modelo.setMonto(new BigDecimal("20.00"));
        modelo.registrarPago();
        assertEquals(new BigDecimal("20.00"), modelo.getMonto());
        assertNotNull(modelo.advertencia);
        assertNull(modelo.exito);
    }

    @Test
    void falloDePersistenciaDebeMostrarErrorSinConfirmar() {
        Factura f = factura();
        seleccionar(f);
        when(pagoDAO.registrarAbono(f.getIdFactura(),
                new BigDecimal("5.00"), "EFECTIVO", null, null))
                .thenThrow(new RuntimeException("BD desconectada"));
        modelo.setTipoPago("EFECTIVO");
        modelo.setMonto(new BigDecimal("5.00"));
        modelo.registrarPago();
        assertNotNull(modelo.error);
        assertNull(modelo.exito);
        assertEquals(new BigDecimal("5.00"), modelo.getMonto());
    }

    @Test
    void siFallaLaRecargaNoDebeAnunciarQueFalloElAbono() {
        Factura f = factura();
        seleccionar(f);
        when(pagoDAO.registrarAbono(f.getIdFactura(),
                new BigDecimal("5.00"), "EFECTIVO", null, null))
                .thenReturn(resumen("15.00", "5.00", "10.00", false));
        when(pagoDetalleDAO.findByFactura(f.getIdFactura()))
                .thenThrow(new RuntimeException("No hay conexión"));
        modelo.setTipoPago("EFECTIVO");
        modelo.setMonto(new BigDecimal("5.00"));
        modelo.registrarPago();
        assertNotNull(modelo.exito);
        assertNotNull(modelo.advertencia);
        assertNull(modelo.error);
    }

    @Test
    void cerrarDetalleNoDebeBorrarAbonosEnLaBaseDeDatos() {
        Factura f = factura();
        seleccionar(f);
        modelo.cancelarSeleccion();
        assertNull(modelo.getFacturaSeleccionada());
        assertNull(modelo.getResumen());
        verify(pagoDAO, never()).registrarAbono(any(), any(), any(), any(), any());
    }

    @Test
    void fallaConsultaInicialDebeRegistrarErrorSevero() {
        when(pagoDAO.listarFacturasPendientes())
                .thenThrow(new RuntimeException("Conexión rechazada"));
        Logger logger = Logger.getLogger(PagoModel.class.getName());
        final boolean[] registrado = {false};
        Handler handler = new Handler() {
            @Override public void publish(LogRecord registro) {
                if (registro.getLevel().intValue() >= Level.SEVERE.intValue()) {
                    registrado[0] = true;
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try {
            modelo.init();
            assertTrue(registrado[0]);
            assertNotNull(modelo.error);
        } finally {
            logger.removeHandler(handler);
        }
    }
}
