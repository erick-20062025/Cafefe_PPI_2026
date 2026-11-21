package control;

import entity.Factura;
import entity.FacturaOrdenProducto;
import entity.Pago;
import entity.PagoDetalle;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoDAOTest {

    @Mock private EntityManager em;
    @InjectMocks private PagoDAO dao;

    private final UUID idFactura = UUID.randomUUID();

    private Factura pendiente() {
        Factura factura = new Factura(idFactura);
        factura.setEstado("PENDIENTE");
        return factura;
    }

    private Factura prepararFacturaBloqueada() {
        Factura factura = pendiente();
        doReturn(factura).when(em).find(
                Factura.class, idFactura, LockModeType.PESSIMISTIC_WRITE);
        return factura;
    }

    @SuppressWarnings("unchecked")
    private <T> TypedQuery<T> consulta(String nombre, Class<T> tipo, T resultado) {
        TypedQuery<T> query = mock(TypedQuery.class);
        doReturn(query).when(em).createNamedQuery(nombre, tipo);
        when(query.setParameter("idFactura", idFactura)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(resultado);
        return query;
    }

    @SuppressWarnings("unchecked")
    private <T> TypedQuery<T> consultaLista(String nombre, Class<T> tipo, List<T> resultados) {
        TypedQuery<T> query = mock(TypedQuery.class);
        doReturn(query).when(em).createNamedQuery(nombre, tipo);
        when(query.setParameter("idFactura", idFactura)).thenReturn(query);
        when(query.getResultList()).thenReturn(resultados);
        return query;
    }

    private void prepararTotales(String total, String abonado) {
        consulta("Pago.detallesInvalidosFactura", Long.class, 0L);
        consulta("Pago.totalFactura", BigDecimal.class, new BigDecimal(total));
        consulta("PagoDetalle.totalAbonadoFactura", BigDecimal.class,
                abonado == null ? null : new BigDecimal(abonado));
    }

    @Test
    void consultaProyeccionSoloDeLaFacturaSolicitada() {
        ProductoFacturado fila = new ProductoFacturado(UUID.randomUUID(), UUID.randomUUID(),
                "Café", new BigDecimal("7.25"));
        consultaLista("Pago.productosFacturados", ProductoFacturado.class, List.of(fila));
        assertEquals(List.of(fila), dao.listarProductosFacturados(idFactura));
    }

    @Test
    void proyeccionRechazaFacturaNulaSinConsultar() {
        assertThrows(IllegalArgumentException.class, () -> dao.listarProductosFacturados(null));
        verifyNoInteractions(em);
    }

    @Test
    void noDebeRegistrarSinFactura() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.registrarAbono(null, new BigDecimal("5.00"),
                        "EFECTIVO", null, null));
        verifyNoInteractions(em);
    }

    @Test
    void noDebeRegistrarMontoNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.registrarAbono(idFactura, null, "EFECTIVO", null, null));
        verifyNoInteractions(em);
    }

    @Test
    void noDebeRegistrarMontoCeroONegativo() {
        for (String monto : List.of("0", "-1.00")) {
            assertThrows(IllegalArgumentException.class,
                    () -> dao.registrarAbono(idFactura, new BigDecimal(monto),
                            "EFECTIVO", null, null));
        }
        verifyNoInteractions(em);
    }

    @Test
    void noDebeAceptarMontoConMasDeDosDecimales() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.registrarAbono(idFactura, new BigDecimal("2.345"),
                        "EFECTIVO", null, null));
        verifyNoInteractions(em);
    }

    @Test
    void debeRechazarMetodoDePagoDesconocido() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.registrarAbono(idFactura, new BigDecimal("1.00"),
                        "CRIPTOMONEDA", null, null));
        verifyNoInteractions(em);
    }

    @Test
    void tarjetaRequiereReferencia() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.registrarAbono(idFactura, new BigDecimal("1.00"),
                        "TARJETA", "  ", null));
        verifyNoInteractions(em);
    }

    @Test
    void facturaInexistenteNoDebePersistir() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.registrarAbono(idFactura, new BigDecimal("2.00"),
                        "EFECTIVO", null, null));
        verify(em).find(Factura.class, idFactura, LockModeType.PESSIMISTIC_WRITE);
        verify(em, never()).persist(any());
    }

    @Test
    void facturaPagadaNoDebeAceptarMasAbonos() {
        Factura factura = pendiente();
        factura.setEstado("PAGADA");
        doReturn(factura).when(em).find(
                Factura.class, idFactura, LockModeType.PESSIMISTIC_WRITE);
        assertThrows(IllegalArgumentException.class,
                () -> dao.registrarAbono(idFactura, new BigDecimal("1.00"),
                        "EFECTIVO", null, null));
        verify(em, never()).persist(any());
    }

    @Test
    void facturaConPrecioInvalidoNoDebeAceptarAbono() {
        prepararFacturaBloqueada();
        consulta("Pago.detallesInvalidosFactura", Long.class, 2L);
        assertThrows(IllegalStateException.class,
                () -> dao.registrarAbono(idFactura, new BigDecimal("1.00"),
                        "EFECTIVO", null, null));
        verify(em, never()).persist(any());
    }

    @Test
    void facturaSinTotalNoDebeAceptarAbono() {
        prepararFacturaBloqueada();
        consulta("Pago.detallesInvalidosFactura", Long.class, 0L);
        consulta("Pago.totalFactura", BigDecimal.class, null);
        assertThrows(IllegalArgumentException.class,
                () -> dao.registrarAbono(idFactura, new BigDecimal("1.00"),
                        "EFECTIVO", null, null));
        verify(em, never()).persist(any());
    }

    @Test
    void noDebeAceptarSobrepago() {
        prepararFacturaBloqueada();
        prepararTotales("10.00", "7.00");
        assertThrows(IllegalArgumentException.class,
                () -> dao.registrarAbono(idFactura, new BigDecimal("4.00"),
                        "EFECTIVO", null, null));
        verify(em, never()).persist(any());
    }

    @Test
    void debeCrearPagoParcialConDetalleEnEfectivo() {
        Factura factura = prepararFacturaBloqueada();
        prepararTotales("12.00", null);
        consultaLista("Pago.findByFactura", Pago.class, List.of());

        PagoDAO.ResumenPago resultado = dao.registrarAbono(idFactura,
                new BigDecimal("5.00"), " efectivo ", null, "Primer abono");

        assertEquals(new BigDecimal("12.00"), resultado.getTotal());
        assertEquals(new BigDecimal("5.00"), resultado.getAbonado());
        assertEquals(new BigDecimal("7.00"), resultado.getSaldo());
        assertFalse(resultado.isCompletado());
        assertEquals("PENDIENTE", factura.getEstado());
        verify(em).persist(any(Pago.class));
        verify(em).persist(argThat(valor -> valor instanceof PagoDetalle pd
                && "EFECTIVO".equals(pd.getTipoPago())
                && new BigDecimal("5.00").equals(pd.getMonto())
                && pd.getIdPago() != null));
        verify(em).flush();
    }

    @SuppressWarnings("unchecked")
    private void prepararReferencia(String tipo, String referencia, long cantidad) {
        TypedQuery<Long> q = mock(TypedQuery.class);
        doReturn(q).when(em).createNamedQuery(
                "PagoDetalle.countReferenciaFactura", Long.class);
        when(q.setParameter("idFactura", idFactura)).thenReturn(q);
        when(q.setParameter("tipoPago", tipo)).thenReturn(q);
        when(q.setParameter("referenciaExterna", referencia)).thenReturn(q);
        when(q.getSingleResult()).thenReturn(cantidad);
    }

    @Test
    void debeCompletarFacturaSumandoAbonosDeDistintosMetodos() {
        Factura factura = prepararFacturaBloqueada();
        prepararTotales("15.00", "5.00");
        Pago existente = new Pago(UUID.randomUUID());
        existente.setEstado("PARCIAL");
        existente.setIdFactura(factura);
        consultaLista("Pago.findByFactura", Pago.class, List.of(existente));
        prepararReferencia("TARJETA", "A-123", 0);

        PagoDAO.ResumenPago resultado = dao.registrarAbono(idFactura,
                new BigDecimal("10.00"), "tarjeta", "A-123", "Pago restante");

        assertTrue(resultado.isCompletado());
        assertEquals(new BigDecimal("0.00"), resultado.getSaldo());
        assertEquals("PAGADA", factura.getEstado());
        assertEquals("COMPLETADO", existente.getEstado());
        verify(em, never()).persist(any(Pago.class));
        verify(em).persist(argThat(valor -> valor instanceof PagoDetalle pd
                && "TARJETA".equals(pd.getTipoPago())
                && "A-123".equals(pd.getReferenciaExterna())
                && pd.getIdPago() == existente));
        verify(em).flush();
    }

    @Test
    void debeRechazarReferenciaDeTarjetaRepetida() {
        prepararFacturaBloqueada();
        prepararTotales("15.00", "5.00");
        prepararReferencia("TARJETA", "REF-77", 1L);
        assertThrows(IllegalArgumentException.class,
                () -> dao.registrarAbono(idFactura, new BigDecimal("3.00"),
                        "TARJETA", "REF-77", null));
        verify(em, never()).persist(any());
    }

    @Test
    void debeCompletarFacturaEnUnSoloPago() {
        Factura factura = prepararFacturaBloqueada();
        prepararTotales("8.50", "0.00");
        consultaLista("Pago.findByFactura", Pago.class, List.of());
        PagoDAO.ResumenPago resultado = dao.registrarAbono(idFactura,
                new BigDecimal("8.50"), "EFECTIVO", null, null);
        assertTrue(resultado.isCompletado());
        assertEquals("PAGADA", factura.getEstado());
        verify(em).persist(argThat(valor -> valor instanceof Pago pago
                && "COMPLETADO".equals(pago.getEstado())));
    }

    @Test
    void noDebeContinuarSiLosAbonosYaCubrenElTotal() {
        prepararFacturaBloqueada();
        prepararTotales("10.00", "10.00");
        assertThrows(IllegalArgumentException.class,
                () -> dao.registrarAbono(idFactura, new BigDecimal("1.00"),
                        "EFECTIVO", null, null));
        verify(em, never()).persist(any());
    }

    @Test
    void debeRechazarMasDeUnaCabeceraDePagoEnFactura() {
        prepararFacturaBloqueada();
        prepararTotales("10.00", "2.00");
        consultaLista("Pago.findByFactura", Pago.class,
                List.of(new Pago(UUID.randomUUID()), new Pago(UUID.randomUUID())));
        assertThrows(IllegalStateException.class,
                () -> dao.registrarAbono(idFactura, new BigDecimal("1.00"),
                        "EFECTIVO", null, null));
        verify(em, never()).persist(any());
    }

    @Test
    void debePropagarFalloEnPersistenciaParaRollbackDelContenedor() {
        prepararFacturaBloqueada();
        prepararTotales("10.00", "0.00");
        consultaLista("Pago.findByFactura", Pago.class, List.of());
        doThrow(new IllegalStateException("BD desconectada")).when(em).flush();
        assertThrows(IllegalStateException.class,
                () -> dao.registrarAbono(idFactura, new BigDecimal("4.00"),
                        "EFECTIVO", null, null));
    }

    @Test
    void debeCalcularResumenSinMontosPagados() {
        Factura factura = pendiente();
        doReturn(factura).when(em).find(Factura.class, idFactura);
        prepararTotales("18.50", null);
        PagoDAO.ResumenPago resumen = dao.consultarResumen(idFactura);
        assertEquals(new BigDecimal("18.50"), resumen.getSaldo());
        assertFalse(resumen.isCompletado());
    }

    @Test
    void debeBuscarDetallesFacturaConConsultaNombrada() {
        consultaLista("Pago.detallesFactura", FacturaOrdenProducto.class,
                List.of(new FacturaOrdenProducto(UUID.randomUUID())));
        assertEquals(1, dao.obtenerDetallesFactura(idFactura).size());
        verify(em).createNamedQuery("Pago.detallesFactura", FacturaOrdenProducto.class);
    }

    @Test
    void debeListarFacturasPagadasUsandoNamedQuerySinEditarFacturas() {
        TypedQuery<Factura> query = mock(TypedQuery.class);
        List<Factura> pagadas = List.of(new Factura(UUID.randomUUID()));
        when(em.createNamedQuery("Pago.facturasPagadas", Factura.class)).thenReturn(query);
        when(query.getResultList()).thenReturn(pagadas);
        assertSame(pagadas, dao.listarFacturasPagadas());
        verify(em).createNamedQuery("Pago.facturasPagadas", Factura.class);
        verify(em, never()).merge(any());
        verify(em, never()).persist(any());
        verify(em, never()).remove(any());
    }
}
