package boundary;

import control.CaracteristicaDAO;
import control.TipoCaracteristicaDAO;
import entity.Caracteristica;
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
public class CaracteristicaModelTest {

    @Mock
    private CaracteristicaDAO caracteristicaDAO;

    @Mock
    private TipoCaracteristicaDAO tipoCaracteristicaDAO;

    @InjectMocks
    private CaracteristicaModel caracteristicaModel;

    @Test
    void debeCargarCaracteristicasDesdeDAO() {

        Caracteristica kilogramos = new Caracteristica();
        kilogramos.setNombre("Kilogramos");

        Caracteristica libras = new Caracteristica();
        libras.setNombre("Libras");

        List<Caracteristica> caracteristicas
                = List.of(kilogramos, libras);

        when(caracteristicaDAO.findAll())
                .thenReturn(caracteristicas);

        caracteristicaModel.cargarDatos();

        assertEquals(2, caracteristicaModel.getModelo().size());
        assertEquals("Kilogramos",
                caracteristicaModel.getModelo().get(0).getNombre());
        assertEquals("Libras",
                caracteristicaModel.getModelo().get(1).getNombre());

        verify(caracteristicaDAO).findAll();
    }

    @Test
    void initDebeCargarCaracteristicasYTiposDisponibles() throws Exception {
        CaracteristicaModelPrueba model = crearModelPrueba();
        Caracteristica caracteristica = new Caracteristica();
        TipoCaracteristica tipo = new TipoCaracteristica();
        when(caracteristicaDAO.findAll()).thenReturn(List.of(caracteristica));
        when(tipoCaracteristicaDAO.findAll()).thenReturn(List.of(tipo));

        model.init();

        assertEquals(List.of(caracteristica), model.getModelo());
        assertEquals(List.of(tipo), model.getTiposCaracteristica());
    }

    @Test
    void nuevoDebeCrearCaracteristicaActivaYReiniciarModoEdicion() throws Exception {
        CaracteristicaModelPrueba model = crearModelPrueba();
        model.setRegistro(new Caracteristica());
        model.btnModificarHandler(null);

        model.btnNuevoHandler(null);

        assertNotNull(model.getRegistro().getIdCaracteristica());
        assertEquals(Boolean.TRUE, model.getRegistro().getActivo());
        assertTrue(model.isNuevo());
        assertFalse(model.isEditando());
        assertFalse(model.isConsultando());
        verifyNoInteractions(caracteristicaDAO);
    }

    @Test
    void guardarSinRegistroNoDebeConsultarNiPersistir() throws Exception {
        CaracteristicaModelPrueba model = crearModelPrueba();

        model.btnGuardarHandler(null);
        assertNull(model.advertencia);
        verifyNoInteractions(caracteristicaDAO);
    }

    @Test
    void noDebeGuardarCaracteristicaSinTipo() throws Exception {
        CaracteristicaModelPrueba model = crearModelPrueba();
        model.btnNuevoHandler(null);
        Caracteristica registro = model.getRegistro();
        registro.setNombre("Grande");

        model.btnGuardarHandler(null);

        assertEquals("Debe seleccionar un tipo de característica", model.advertencia);
        assertSame(registro, model.getRegistro());
        assertTrue(model.isNuevo());
        verifyNoInteractions(caracteristicaDAO);
    }

    @Test
    void noDebeGuardarCaracteristicaConTipoInactivoOSinEstado() throws Exception {
        CaracteristicaModelPrueba model = crearModelPrueba();
        model.btnNuevoHandler(null);
        model.getRegistro().setNombre("Grande");

        TipoCaracteristica tipo = new TipoCaracteristica();
        model.getRegistro().setIdTipoCaracteristica(tipo);

        for (Boolean activo : new Boolean[]{false, null}) {
            tipo.setActivo(activo);
            model.advertencia = null;
            model.btnGuardarHandler(null);
            assertEquals("No se puede asignar un tipo de característica inactivo", model.advertencia);
            assertTrue(model.isNuevo());
        }

        verifyNoInteractions(caracteristicaDAO);
    }

    @Test
    void guardarCaracteristicaValidaDebeCrearRecargarYLimpiarEstado() throws Exception {
        CaracteristicaModelPrueba model = crearModelPrueba();
        model.btnNuevoHandler(null);
        Caracteristica registro = model.getRegistro();
        registro.setNombre("Grande");
        registro.setIdTipoCaracteristica(tipoActivo());
        when(caracteristicaDAO.findAll()).thenReturn(List.of(registro));

        model.btnGuardarHandler(null);

        verify(caracteristicaDAO).crear(registro);
        verify(caracteristicaDAO, never()).modificar(any());
        assertEquals(List.of(registro), model.getModelo());
        assertNull(model.getRegistro());
        assertFalse(model.isNuevo());
        assertFalse(model.isEditando());
        assertEquals("Registro guardado correctamente", model.exito);
    }

    private TipoCaracteristica tipoActivo() {
        TipoCaracteristica tipo = new TipoCaracteristica();
        tipo.setActivo(true);
        return tipo;
    }

    private CaracteristicaModelPrueba crearModelPrueba() throws Exception {
        CaracteristicaModelPrueba model = new CaracteristicaModelPrueba();
        inyectarCampo(model, "caracteristicaDAO", caracteristicaDAO);
        inyectarCampo(model, "tipoCaracteristicaDAO", tipoCaracteristicaDAO);
        return model;
    }

    private void inyectarCampo(Object model, String nombre, Object valor) throws Exception {
        Field campo = CaracteristicaModel.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(model, valor);
    }

    private static class CaracteristicaModelPrueba extends CaracteristicaModel {

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
