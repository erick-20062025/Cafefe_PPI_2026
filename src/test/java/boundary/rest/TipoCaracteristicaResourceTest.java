package boundary.rest;

import boundary.rest.dto.TipoCaracteristicaDTO;
import control.TipoCaracteristicaDAO;
import entity.TipoCaracteristica;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import validation.TipoCaracteristicaValidator;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TipoCaracteristicaResourceTest {
    @Mock private TipoCaracteristicaDAO dao;
    @InjectMocks private TipoCaracteristicaResource recurso;

    @BeforeEach void iniciar() { RestTestRuntime.instalar(); }
    @AfterEach void cerrar() { RestTestRuntime.limpiar(); }

    @Test
    void creaYDevuelveElPatronSinModificarlo() {
        var respuesta = recurso.crear("""
                {"nombre":"Tamaño","activo":true,"expresionRegular":" ^[0-9]+$ "}
                """);
        assertEquals(201, respuesta.getStatus());
        var dto = assertInstanceOf(TipoCaracteristicaDTO.class, respuesta.getEntity());
        assertNotNull(dto.getIdTipoCaracteristica());
        assertEquals(" ^[0-9]+$ ", dto.getExpresionRegular());
    }

    @Test
    void rechazoPorFormatoYExpresionInvalida() {
        doAnswer(i -> {
            TipoCaracteristicaValidator.validar(i.getArgument(0));
            return null;
        }).when(dao).crear(any());
        assertEquals(422, recurso.crear("""
                {"nombre":"Tamaño","activo":true,"expresionRegular":"["}
                """).getStatus());
        assertEquals(400, recurso.crear("""
                {"nombre":"Tamaño","activo":"true"}
                """).getStatus());
    }

    @Test
    void devuelveDtoYTraduceLaRestriccionDeBorrado() {
        UUID id = UUID.randomUUID();
        TipoCaracteristica tipo = new TipoCaracteristica(id);
        when(dao.findById(id)).thenReturn(tipo);
        when(dao.findAll()).thenReturn(List.of(tipo));
        var lista = (List<?>) recurso.listar().getEntity();
        assertInstanceOf(TipoCaracteristicaDTO.class, lista.getFirst());
        assertEquals(200, recurso.obtener(id.toString()).getStatus());
        doThrow(new RuntimeException(new java.sql.SQLException("detalle interno", "23503")))
                .when(dao).eliminar(tipo);
        assertEquals(409, recurso.eliminar(id.toString()).getStatus());
    }
}
