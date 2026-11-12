package control;

import entity.PagoDetalle;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoDetalleDAOTest {
    @Mock EntityManager em;
    @InjectMocks PagoDetalleDAO dao;

    @Test
    void identificadorNuloNoDebeConsultar() {
        assertThrows(IllegalArgumentException.class, () -> dao.findByFactura(null));
        verifyNoInteractions(em);
    }

    @Test
    @SuppressWarnings("unchecked")
    void debeConsultarAbonosDeFacturaPorNamedQuery() {
        UUID id = UUID.randomUUID();
        TypedQuery<PagoDetalle> q = mock(TypedQuery.class);
        when(em.createNamedQuery("PagoDetalle.findByFactura", PagoDetalle.class)).thenReturn(q);
        when(q.setParameter("idFactura", id)).thenReturn(q);
        PagoDetalle abono = new PagoDetalle(UUID.randomUUID());
        when(q.getResultList()).thenReturn(List.of(abono));
        assertEquals(List.of(abono), dao.findByFactura(id));
    }

    @Test
    @SuppressWarnings("unchecked")
    void debeAceptarHistorialVacio() {
        UUID id = UUID.randomUUID();
        TypedQuery<PagoDetalle> q = mock(TypedQuery.class);
        when(em.createNamedQuery("PagoDetalle.findByFactura", PagoDetalle.class)).thenReturn(q);
        when(q.setParameter("idFactura", id)).thenReturn(q);
        when(q.getResultList()).thenReturn(List.of());
        assertTrue(dao.findByFactura(id).isEmpty());
    }
}
