package boundary.rest;

import control.CaracteristicaDAO;
import control.TipoCaracteristicaDAO;
import entity.Caracteristica;
import entity.TipoCaracteristica;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaracteristicaResourceTest {
    @Mock CaracteristicaDAO dao;
    @Mock TipoCaracteristicaDAO tipoDAO;
    @InjectMocks CaracteristicaResource recurso;
    UUID id = UUID.randomUUID();

    @BeforeEach void iniciar() { RestTestRuntime.instalar(); }
    @AfterEach void cerrar() { RestTestRuntime.limpiar(); }

    String json() {
        return """
                {"idTipoCaracteristica":"%s","nombre":"Tamaño","activo":true}
                """.formatted(id);
    }

    @Test void crudDelegaAlDaoConTipoReal() {
        TipoCaracteristica tipo = new TipoCaracteristica(id);
        Caracteristica actual = new Caracteristica(id);
        when(tipoDAO.findById(id)).thenReturn(tipo);
        when(dao.findById(id)).thenReturn(actual);
        when(dao.findAll()).thenReturn(List.of(actual));
        assertEquals(201, recurso.crear(json()).getStatus());
        verify(dao).crear(argThat(c -> c.getIdTipoCaracteristica() == tipo && c.getIdCaracteristica() != null));
        assertEquals(200, recurso.listar().getStatus());
        assertEquals(200, recurso.obtener(id.toString()).getStatus());
        assertEquals(200, recurso.modificar(id.toString(), json()).getStatus());
        verify(dao).modificar(same(actual));
        assertEquals(200, recurso.eliminar(id.toString()).getStatus());
        verify(dao).eliminar(same(actual));
    }

    @Test void validaEntradaYReferencias() {
        assertEquals(400, recurso.crear("{").getStatus());
        assertEquals(400, recurso.crear(json().replace("true", "\"true\"")).getStatus());
        assertEquals(422, recurso.crear(json().replace("Tamaño", " ")).getStatus());
        assertEquals(422, recurso.crear(json()).getStatus());
        assertEquals(404, recurso.obtener(id.toString()).getStatus());
        verify(dao, never()).crear(any());
    }

    @Test void conflictoDeIntegridadNoExponeSql() {
        when(dao.findById(id)).thenReturn(new Caracteristica(id));
        doThrow(new RuntimeException(new java.sql.SQLException("sql interno", "23503"))).when(dao).eliminar(any());
        assertEquals(409, recurso.eliminar(id.toString()).getStatus());
    }
}
