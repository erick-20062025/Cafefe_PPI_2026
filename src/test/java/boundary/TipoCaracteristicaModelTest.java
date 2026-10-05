package boundary;

import control.TipoCaracteristicaDAO;
import entity.TipoCaracteristica;
import java.util.List;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TipoCaracteristicaModelTest {

    @Mock
    private TipoCaracteristicaDAO tipoCaracteristicaDAO;

    @InjectMocks
    private TipoCaracteristicaModel tipoCaracteristicaModel;

    @Test
    void debeCargarTiposCaracteristicaDesdeDAO() {
        TipoCaracteristica tamano = new TipoCaracteristica();
        tamano.setNombre("Tamaño");

        TipoCaracteristica color = new TipoCaracteristica();
        color.setNombre("Color");

        List<TipoCaracteristica> tiposCaracteristica = List.of(tamano, color);

        when(tipoCaracteristicaDAO.findAll()).thenReturn(tiposCaracteristica);

        tipoCaracteristicaModel.cargarDatos();

        assertEquals(2, tipoCaracteristicaModel.getModelo().size());
        assertEquals("Tamaño", tipoCaracteristicaModel.getModelo().get(0).getNombre());
        assertEquals("Color", tipoCaracteristicaModel.getModelo().get(1).getNombre());

        verify(tipoCaracteristicaDAO).findAll();
    }

    @Test
    void initDebeCargarTiposCaracteristica() throws Exception {
        TipoCaracteristicaModelPrueba model = crearModelPrueba();
        TipoCaracteristica tipo = new TipoCaracteristica();
        when(tipoCaracteristicaDAO.findAll()).thenReturn(List.of(tipo));

        model.init();

        assertEquals(List.of(tipo), model.getModelo());
        verify(tipoCaracteristicaDAO).findAll();
    }

    @Test
    void guardarSinRegistroNoDebeAccederAlDAO() throws Exception {
        TipoCaracteristicaModelPrueba model = crearModelPrueba();

        model.btnGuardarHandler(null);

        assertNull(model.advertencia);
        verifyNoInteractions(tipoCaracteristicaDAO);
    }

    @Test
    void expresionInvalidaDebeAdvertirYConservarNuevoRegistro() throws Exception {
        TipoCaracteristicaModelPrueba model = crearModelPrueba();
        model.btnNuevoHandler(null);
        TipoCaracteristica tipo = model.getRegistro();
        tipo.setExpresionRegular("[");

        model.btnGuardarHandler(null);

        assertEquals("La expresión regular ingresada no es válida", model.advertencia);
        assertSame(tipo, model.getRegistro());
        assertTrue(model.isNuevo());
        verifyNoInteractions(tipoCaracteristicaDAO);
    }

    @Test
    void expresionValidaDebeGuardarTipoNuevoActivo() throws Exception {
        TipoCaracteristicaModelPrueba model = crearModelPrueba();
        model.btnNuevoHandler(null);
        TipoCaracteristica tipo = model.getRegistro();
        tipo.setExpresionRegular("^[0-9]+(?:\\.[0-9]+)?$");

        model.btnGuardarHandler(null);

        assertNotNull(tipo.getIdTipoCaracteristica());
        assertEquals(Boolean.TRUE, tipo.getActivo());
        verify(tipoCaracteristicaDAO).crear(tipo);
        assertNull(model.getRegistro());
        assertFalse(model.isNuevo());
        assertNull(model.advertencia);
        assertEquals("Registro guardado correctamente", model.exito);
    }

    @Test
    void expresionNulaOEnBlancoDebePermitirGuardarSinRestriccion() throws Exception {
        TipoCaracteristicaModelPrueba model = crearModelPrueba();

        for (String expresion : new String[]{null, "", "   "}) {
            model.btnNuevoHandler(null);
            TipoCaracteristica tipo = model.getRegistro();
            tipo.setExpresionRegular(expresion);

            model.btnGuardarHandler(null);

            verify(tipoCaracteristicaDAO).crear(tipo);
            assertNull(model.getRegistro());
            assertNull(model.advertencia);
        }
    }

    private TipoCaracteristicaModelPrueba crearModelPrueba() throws Exception {
        TipoCaracteristicaModelPrueba model = new TipoCaracteristicaModelPrueba();
        Field campo = TipoCaracteristicaModel.class.getDeclaredField("tipoCaracteristicaDAO");
        campo.setAccessible(true);
        campo.set(model, tipoCaracteristicaDAO);
        return model;
    }

    private static class TipoCaracteristicaModelPrueba extends TipoCaracteristicaModel {
        private String advertencia;
        private String exito;

        @Override
        protected void mostrarAdvertencia(String detalle) {
            advertencia = detalle;
        }

        @Override
        protected void mostrarExito(String detalle) {
            exito = detalle;
        }

        @Override
        protected void mostrarError(String detalle) {
            fail("Error inesperado: " + detalle);
        }
    }
}
