package boundary.rest;

import boundary.rest.messages.ApiResponse;
import control.DescuentoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.TipoDescuento;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

/**
 * Pruebas de contrato REST; comprueban respuestas HTTP y que una petición
 * inválida jamás invoque un método de escritura en el DAO.
 */
@ExtendWith(MockitoExtension.class)
class TipoDescuentoResourceTest {

    /** Prepara el constructor HTTP que falta fuera de Open Liberty. */
    @BeforeEach
    void prepararRuntimeRest() {
        RestTestRuntime.instalar();
    }

    @AfterEach
    void limpiarRuntimeRest() {
        RestTestRuntime.limpiar();
    }


    @Mock private TipoDescuentoDAO tipoDescuentoDAO;
    @Mock private DescuentoDAO descuentoDAO;
    @InjectMocks private TipoDescuentoResource resource;

    private static TipoDescuento valido() {
        TipoDescuento tipo = new TipoDescuento();
        tipo.setNombre("Promoción especial");
        tipo.setActivo(Boolean.TRUE);
        tipo.setDescuentoMaximo(10);
        tipo.setObservaciones("Solo para pruebas");
        return tipo;
    }

    private static String detalle(Response respuesta) {
        assertTrue(respuesta.getEntity() instanceof ApiResponse);
        return ((ApiResponse) respuesta.getEntity()).getDetalle();
    }

    private static void codigo(Response respuesta, int esperado, String codigo) {
        assertEquals(esperado, respuesta.getStatus());
        assertEquals(codigo, ((ApiResponse) respuesta.getEntity()).getCodigo());
    }

    // ----- POST: escenarios inválidos que NO deben persistirse -----

    @Test
    void crearRechazaObjetoNulo() {
        codigo(resource.crear(null), 400, "API-400");
        verifyNoInteractions(tipoDescuentoDAO, descuentoDAO);
    }

    @Test
    void crearRechazaNombreNuloVacioBlancoYEspaciosUnicode() {
        for (String valor : new String[]{null, "", "   ", "\t\n", "\u00A0\u00A0", "\u200B"}) {
            TipoDescuento tipo = valido();
            tipo.setNombre(valor);
            codigo(resource.crear(tipo), 422, "API-422");
        }
        verifyNoInteractions(tipoDescuentoDAO, descuentoDAO);
    }

    @Test
    void crearRechazaNombreExcesivo() {
        TipoDescuento tipo = valido();
        tipo.setNombre("a".repeat(151));
        codigo(resource.crear(tipo), 422, "API-422");
        verifyNoInteractions(tipoDescuentoDAO);
    }

    @Test
    void crearAceptaNombreDeLongitudLimite() {
        TipoDescuento tipo = valido();
        tipo.setNombre("a".repeat(150));
        codigo(resource.crear(tipo), 201, "API-201");
        verify(tipoDescuentoDAO).crear(same(tipo));
    }

    @Test
    void crearRechazaCaracterDeControlEnNombre() {
        TipoDescuento tipo = valido();
        tipo.setNombre("Promo\u0000peligrosa");
        codigo(resource.crear(tipo), 422, "API-422");
        verifyNoInteractions(tipoDescuentoDAO);
    }

    @Test
    void crearRechazaActivoNuloPeroPermiteFalse() {
        TipoDescuento tipo = valido();
        tipo.setActivo(null);
        codigo(resource.crear(tipo), 422, "API-422");
        verifyNoInteractions(tipoDescuentoDAO);

        tipo.setActivo(Boolean.FALSE);
        codigo(resource.crear(tipo), 201, "API-201");
        verify(tipoDescuentoDAO).crear(same(tipo));
    }

    @Test
    void crearRechazaDescuentoMaximoNulo() {
        TipoDescuento tipo = valido();
        tipo.setDescuentoMaximo(null);
        codigo(resource.crear(tipo), 422, "API-422");
        verifyNoInteractions(tipoDescuentoDAO);
    }

    @Test
    void crearRechazaDescuentoMaximoNegativoExtremo() {
        for (int valor : new int[]{-1, -50, Integer.MIN_VALUE}) {
            TipoDescuento tipo = valido();
            tipo.setDescuentoMaximo(valor);
            codigo(resource.crear(tipo), 422, "API-422");
        }
        verifyNoInteractions(tipoDescuentoDAO);
    }

    @Test
    void crearPermiteDescuentoMaximoCero() {
        TipoDescuento tipo = valido();
        tipo.setDescuentoMaximo(0);
        codigo(resource.crear(tipo), 201, "API-201");
        verify(tipoDescuentoDAO).crear(same(tipo));
    }

    @Test
    void crearRechazaObservacionesDemasiadoLargas() {
        TipoDescuento tipo = valido();
        tipo.setObservaciones("x".repeat(1001));
        codigo(resource.crear(tipo), 422, "API-422");
        verifyNoInteractions(tipoDescuentoDAO);
    }

    @Test
    void crearAceptaObservacionesVaciasComoOpcional() {
        TipoDescuento tipo = valido();
        tipo.setObservaciones("   ");
        codigo(resource.crear(tipo), 201, "API-201");
        assertNull(tipo.getObservaciones());
        verify(tipoDescuentoDAO).crear(same(tipo));
    }

    @Test
    void crearRechazaControlEnObservacionesPeroPermiteSaltosLinea() {
        TipoDescuento tipo = valido();
        tipo.setObservaciones("notas\u0000secretas");
        codigo(resource.crear(tipo), 422, "API-422");
        verifyNoInteractions(tipoDescuentoDAO);

        tipo.setObservaciones("una\nsegunda");
        codigo(resource.crear(tipo), 201, "API-201");
        verify(tipoDescuentoDAO).crear(same(tipo));
    }

    @Test
    void crearNoAceptaIdControladoPorElCliente() {
        TipoDescuento tipo = valido();
        tipo.setIdTipoDescuento(UUID.randomUUID());
        codigo(resource.crear(tipo), 422, "API-422");
        verifyNoInteractions(tipoDescuentoDAO);
    }

    @Test
    void crearGeneraUUIDYNormalizaTexto() {
        TipoDescuento tipo = valido();
        tipo.setNombre("  \tPromoción especial \u00A0 ");
        tipo.setObservaciones("  nota importante   ");

        Response respuesta = resource.crear(tipo);

        codigo(respuesta, 201, "API-201");
        assertNotNull(tipo.getIdTipoDescuento());
        assertEquals("Promoción especial", tipo.getNombre());
        assertEquals("nota importante", tipo.getObservaciones());
        assertTrue(detalle(respuesta).contains(tipo.getIdTipoDescuento().toString()));
        verify(tipoDescuentoDAO).crear(same(tipo));
    }

    // ----- PUT: datos coherentes, UUID y ausencia de mutaciones -----

    @Test
    void modificarRechazaIdIncorrectoAntesDeConsultar() {
        TipoDescuento tipo = valido();
        for (String id : new String[]{"no-uuid", "1-1-1-1-1", "", "   "}) {
            codigo(resource.modificar(id, tipo), 400, "API-400");
        }
        verifyNoInteractions(tipoDescuentoDAO);
    }

    @Test
    void modificarRechazaCuerpoNulo() {
        codigo(resource.modificar(UUID.randomUUID().toString(), null), 400, "API-400");
        verifyNoInteractions(tipoDescuentoDAO);
    }

    @Test
    void modificarRechazaIdDelCuerpoDistintoAlDeLaURL() {
        TipoDescuento tipo = valido();
        tipo.setIdTipoDescuento(UUID.randomUUID());
        codigo(resource.modificar(UUID.randomUUID().toString(), tipo), 422, "API-422");
        verifyNoInteractions(tipoDescuentoDAO);
    }

    @Test
    void modificarRechazaValoresInvalidosSinLeerNiModificar() {
        String id = UUID.randomUUID().toString();
        for (int caso = 0; caso < 5; caso++) {
            TipoDescuento tipo = valido();
            switch (caso) {
                case 0: tipo.setNombre(" "); break;
                case 1: tipo.setNombre("a".repeat(151)); break;
                case 2: tipo.setActivo(null); break;
                case 3: tipo.setDescuentoMaximo(-10); break;
                default: tipo.setObservaciones("x".repeat(1001)); break;
            }
            codigo(resource.modificar(id, tipo), 422, "API-422");
        }
        verifyNoInteractions(tipoDescuentoDAO);
    }

    @Test
    void modificarDevuelve404SinCrearRegistroNuevo() {
        UUID id = UUID.randomUUID();
        when(tipoDescuentoDAO.findById(id)).thenReturn(null);
        codigo(resource.modificar(id.toString(), valido()), 404, "API-404");
        verify(tipoDescuentoDAO, never()).modificar(any());
        verify(tipoDescuentoDAO, never()).crear(any());
    }

    @Test
    void modificarSoloCambiaCamposPermitidos() {
        UUID id = UUID.randomUUID();
        TipoDescuento guardado = valido();
        guardado.setIdTipoDescuento(id);
        TipoDescuento entrante = valido();
        entrante.setIdTipoDescuento(id);
        entrante.setNombre("   Nueva oferta  ");
        entrante.setActivo(false);
        entrante.setDescuentoMaximo(0);
        entrante.setObservaciones("   actualización ");
        when(tipoDescuentoDAO.findById(id)).thenReturn(guardado);

        codigo(resource.modificar(id.toString(), entrante), 200, "API-200");

        assertEquals(id, guardado.getIdTipoDescuento());
        assertEquals("Nueva oferta", guardado.getNombre());
        assertFalse(guardado.getActivo());
        assertEquals(0, guardado.getDescuentoMaximo());
        assertEquals("actualización", guardado.getObservaciones());
        verify(tipoDescuentoDAO).modificar(same(guardado));
        verify(tipoDescuentoDAO, never()).crear(any());
    }

    // ----- Consultas y eliminación -----

    @Test
    void listarSinRegistrosDevuelve200YListaVacia() {
        when(tipoDescuentoDAO.findAll()).thenReturn(List.of());
        Response respuesta = resource.findAll();
        assertEquals(200, respuesta.getStatus());
        assertEquals(List.of(), respuesta.getEntity());
    }

    @Test
    void buscarPorIdInvalidoNoConsultaDAO() {
        codigo(resource.findById("1-1-1-1-1"), 400, "API-400");
        verifyNoInteractions(tipoDescuentoDAO);
    }

    @Test
    void buscarInexistenteDevuelve404() {
        UUID id = UUID.randomUUID();
        when(tipoDescuentoDAO.findById(id)).thenReturn(null);
        codigo(resource.findById(id.toString()), 404, "API-404");
    }

    @Test
    void buscarExistenteDevuelveEntidad() {
        UUID id = UUID.randomUUID();
        TipoDescuento tipo = valido();
        when(tipoDescuentoDAO.findById(id)).thenReturn(tipo);
        Response respuesta = resource.findById(id.toString());
        assertEquals(200, respuesta.getStatus());
        assertSame(tipo, respuesta.getEntity());
    }

    @Test
    void eliminarInvalidoNoTocaDAO() {
        codigo(resource.eliminar("invalid"), 400, "API-400");
        verifyNoInteractions(tipoDescuentoDAO);
    }

    @Test
    void eliminarInexistenteNoBorraNada() {
        UUID id = UUID.randomUUID();
        when(tipoDescuentoDAO.findById(id)).thenReturn(null);
        codigo(resource.eliminar(id.toString()), 404, "API-404");
        verify(tipoDescuentoDAO, never()).eliminar(any());
    }

    @Test
    void eliminarValidoInvocaDAO() {
        UUID id = UUID.randomUUID();
        TipoDescuento tipo = valido();
        when(tipoDescuentoDAO.findById(id)).thenReturn(tipo);
        codigo(resource.eliminar(id.toString()), 200, "API-200");
        verify(tipoDescuentoDAO).eliminar(same(tipo));
    }

    @Test
    void descuentosRelacionadosRechazaUUIDInvalido() {
        codigo(resource.findDescuentos("invalid"), 400, "API-400");
        verifyNoInteractions(tipoDescuentoDAO, descuentoDAO);
    }

    @Test
    void descuentosRelacionadosRequiereTipoExistente() {
        UUID id = UUID.randomUUID();
        when(tipoDescuentoDAO.findById(id)).thenReturn(null);
        codigo(resource.findDescuentos(id.toString()), 404, "API-404");
        verifyNoInteractions(descuentoDAO);
    }

    @Test
    void descuentosRelacionadosDevuelveListaVacia() {
        UUID id = UUID.randomUUID();
        when(tipoDescuentoDAO.findById(id)).thenReturn(valido());
        when(descuentoDAO.findByTipoDescuento(id)).thenReturn(List.<Descuento>of());
        Response respuesta = resource.findDescuentos(id.toString());
        assertEquals(200, respuesta.getStatus());
        assertEquals(List.of(), respuesta.getEntity());
    }

    // ----- Excepciones SQL, no confundir errores internos con conflicto -----

    @Test
    void crearDevuelve409SoloEnConflictoDeIntegridad() {
        doThrow(new PersistenceException("integridad",
                new SQLException("duplicado", "23505")))
                .when(tipoDescuentoDAO).crear(any(TipoDescuento.class));

        codigo(resource.crear(valido()), 409, "API-409");
    }

    @Test
    void crearErrorDeConexionDevuelve500SinFiltrarSQL() {
        doThrow(new PersistenceException("SECRETOS INTERNOS",
                new SQLException("credenciales privadas", "08006")))
                .when(tipoDescuentoDAO).crear(any(TipoDescuento.class));

        Response respuesta = resource.crear(valido());
        codigo(respuesta, 500, "API-500");
        assertFalse(detalle(respuesta).contains("SECRETOS"));
        assertFalse(detalle(respuesta).contains("credenciales"));
    }

    @Test
    void eliminarConClaveForaneaDevuelve409() {
        UUID id = UUID.randomUUID();
        TipoDescuento tipo = valido();
        when(tipoDescuentoDAO.findById(id)).thenReturn(tipo);
        doThrow(new PersistenceException("referenciado",
                new SQLException("fk", "23503"))).when(tipoDescuentoDAO).eliminar(tipo);
        codigo(resource.eliminar(id.toString()), 409, "API-409");
    }

    @Test
    void falloInesperadoDeConsultaSeOcultaComo500() {
        when(tipoDescuentoDAO.findAll()).thenThrow(new IllegalStateException("Ruta privada"));
        Response respuesta = resource.findAll();
        codigo(respuesta, 500, "API-500");
        assertFalse(detalle(respuesta).contains("Ruta privada"));
    }
}
