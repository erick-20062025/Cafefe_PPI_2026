package boundary.rest;

import boundary.rest.dto.FacturaDTO;
import boundary.rest.messages.ApiResponse;
import control.FacturaDAO;
import control.FacturaOrdenProductoDAO;
import entity.Factura;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacturaResourceTest {
    @Mock FacturaDAO facturaDAO;
    @Mock FacturaOrdenProductoDAO detalleDAO;
    @InjectMocks FacturaResource recurso;

    @BeforeEach void iniciar() { RestTestRuntime.instalar(); }
    @AfterEach void terminar() { RestTestRuntime.limpiar(); }

    @Test void creaPendienteConFechaYUuid() {
        var respuesta = recurso.crear(FacturaSolicitudTest.SOLICITUD);
        assertEquals(201, respuesta.getStatus());
        var dto = assertInstanceOf(FacturaDTO.class, respuesta.getEntity());
        assertNotNull(dto.getIdFactura());
        assertNotNull(dto.getFechaFacturacion());
        assertEquals("PENDIENTE", dto.getEstado());
        verify(facturaDAO).crearFacturaCompleta(any(), anyList());
    }

    @Test void putReutilizaDaoCompleto() {
        UUID id = UUID.randomUUID();
        when(facturaDAO.findById(id)).thenReturn(new Factura(id));
        when(detalleDAO.findByIdFactura(id)).thenReturn(List.of());
        assertEquals(200, recurso.modificar(id.toString(), FacturaSolicitudTest.SOLICITUD).getStatus());
        verify(facturaDAO).modificarFacturaCompleta(argThat(f -> id.equals(f.getIdFactura())), anyList());
    }

    @Test void traduceErroresDeNegocioEnvueltos() {
        String id = UUID.randomUUID().toString();
        doThrow(new RuntimeException(new FacturaDAO.DatosInvalidos("Caja inválida")))
                .when(facturaDAO).crearFacturaCompleta(any(), anyList());
        assertEquals(422, recurso.crear(FacturaSolicitudTest.SOLICITUD).getStatus());
        doThrow(new RuntimeException(new FacturaDAO.Conflicto("Con pagos")))
                .when(facturaDAO).eliminarFacturaCompleta(any());
        assertEquals(409, recurso.eliminar(id).getStatus());
        assertEquals(404, recurso.obtener(id).getStatus());
        assertEquals(400, recurso.obtener("mal").getStatus());
    }

    @Test void ocultaErroresInternos() {
        when(facturaDAO.findAll()).thenThrow(new RuntimeException("SQL interno"));
        var respuesta = recurso.listar();
        assertEquals(500, respuesta.getStatus());
        assertFalse(((ApiResponse) respuesta.getEntity()).getDetalle().contains("SQL"));
    }
}
