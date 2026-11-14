package boundary.rest;

import control.ProductoCaracteristicaDAO;
import entity.ProductoCaracteristica;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoCaracteristicaResourceTest {
    @Mock ProductoCaracteristicaDAO dao;
    @InjectMocks ProductoCaracteristicaResource recurso;
    UUID id = UUID.randomUUID();

    @BeforeEach void iniciar() { RestTestRuntime.instalar(); }
    @AfterEach void cerrar() { RestTestRuntime.limpiar(); }

    String json() {
        return """
                {"idProducto":"%s","idCaracteristica":"%s","valor":" 600 "}
                """.formatted(id, id);
    }

    @Test void creaAsociacionYConservaValorParaElValidador() {
        assertEquals(201, recurso.crear(json()).getStatus());
        verify(dao).crear(argThat(r -> id.equals(r.getIdProducto().getIdProducto())
                && id.equals(r.getIdCaracteristica().getIdCaracteristica()) && " 600 ".equals(r.getValor())));
    }

    @Test void consultaFiltroVacioYEliminaSoloAsociacion() {
        ProductoCaracteristica actual = new ProductoCaracteristica(id);
        when(dao.findById(id)).thenReturn(actual);
        when(dao.findByIdProducto(id)).thenReturn(List.of());
        assertEquals(List.of(), recurso.listarPorProducto(id.toString()).getEntity());
        assertEquals(200, recurso.obtener(id.toString()).getStatus());
        assertEquals(200, recurso.eliminar(id.toString()).getStatus());
        verify(dao).eliminar(same(actual));
    }

    @Test void erroresDeFormatoDuplicadoYValorInvalido() {
        assertEquals(400, recurso.listarPorProducto("mal").getStatus());
        assertEquals(422, recurso.crear(json().replace(" 600 ", "")).getStatus());
        assertEquals(404, recurso.eliminar(id.toString()).getStatus());
        doThrow(new IllegalArgumentException("La característica ya está asociada al producto")).when(dao).crear(any());
        assertEquals(409, recurso.crear(json()).getStatus());
        doThrow(new IllegalArgumentException("El valor ingresado no cumple con el formato requerido")).when(dao).crear(any());
        assertEquals(422, recurso.crear(json()).getStatus());
    }
}
