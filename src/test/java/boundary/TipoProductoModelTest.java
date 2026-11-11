/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary;

import control.TipoProductoDAO;
import entity.TipoProducto;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Field;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 *
 * @author erick
 */
@ExtendWith(MockitoExtension.class)
public class TipoProductoModelTest {

    @Mock
    private TipoProductoDAO tipoProductoDAO;

    @InjectMocks
    private TipoProductoModel tipoProductoModel;

    @Test
    void debeCargarTipoProductosDesdeDAO() {

        TipoProducto tipoProducto1 = new TipoProducto();
        tipoProducto1.setNombre("Bebidas");

        TipoProducto tipoProducto2 = new TipoProducto();
        tipoProducto2.setNombre("Postres");

        List<TipoProducto> tipoProductos = List.of(tipoProducto1, tipoProducto2);

        when(tipoProductoDAO.findAll()).thenReturn(tipoProductos);

        tipoProductoModel.cargarDatos();

        assertEquals(2, tipoProductoModel.getModelo().size());
        assertEquals("Bebidas",
                tipoProductoModel.getModelo().get(0).getNombre());
        assertEquals("Postres",
                tipoProductoModel.getModelo().get(1).getNombre());

        verify(tipoProductoDAO).findAll();
    }

    @Test
    void debeCrearTipoProductoNuevoConUuidYActivo() {

        TipoProducto tipoProducto = tipoProductoModel.crearNuevoRegistro();

        assertNotNull(tipoProducto);
        assertNotNull(tipoProducto.getIdTipoProducto());
        assertInstanceOf(UUID.class, tipoProducto.getIdTipoProducto());
        assertTrue(tipoProducto.getActivo());
    }

    @Test
    void guardarSinRegistroNoDebeAccederAlDAO() throws Exception {
        TipoProductoModelPrueba model = crearModelPrueba();

        model.btnGuardarHandler(null);

        assertNull(model.advertencia);
        assertNull(model.exito);
        verifyNoInteractions(tipoProductoDAO);
    }

    @Test
    void tipoValidoDebeGuardarseCorrectamente() throws Exception {
        TipoProductoModelPrueba model = crearModelPrueba();

        model.btnNuevoHandler(null);
        TipoProducto tipo = model.getRegistro();
        tipo.setNombre("Bebidas");

        model.btnGuardarHandler(null);

        verify(tipoProductoDAO).crear(tipo);
        verify(tipoProductoDAO).findAll();

        assertNull(model.getRegistro());
        assertFalse(model.isNuevo());
        assertNull(model.advertencia);
        assertEquals("Registro guardado correctamente", model.exito);
    }

    @Test
    void nombreInvalidoDebeAdvertirYConservarRegistro() throws Exception {
        TipoProductoModelPrueba model = crearModelPrueba();

        model.btnNuevoHandler(null);
        TipoProducto tipo = model.getRegistro();
        tipo.setNombre("");

        doThrow(new IllegalArgumentException(
                "Debe ingresar el nombre del tipo de producto"))
                .when(tipoProductoDAO).crear(tipo);

        model.btnGuardarHandler(null);

        assertEquals(
                "Debe ingresar el nombre del tipo de producto",
                model.advertencia);

        assertSame(tipo, model.getRegistro());
        assertTrue(model.isNuevo());
        assertNull(model.exito);

        verify(tipoProductoDAO).crear(tipo);
        verify(tipoProductoDAO, never()).findAll();
    }

    @Test
    void modificarTipoValidoDebeLlamarAlDAO() throws Exception {
        TipoProductoModelPrueba model = crearModelPrueba();

        model.btnNuevoHandler(null);
        TipoProducto tipo = model.getRegistro();
        tipo.setNombre("Alimentos");

        model.btnModificarHandler(null);
        model.btnGuardarHandler(null);

        verify(tipoProductoDAO).modificar(tipo);
        verify(tipoProductoDAO, never()).crear(any());

        assertNull(model.getRegistro());
        assertFalse(model.isEditando());
        assertEquals("Registro guardado correctamente", model.exito);
    }

    @Test
    void errorInesperadoDebeMostrarMensajeGenerico() throws Exception {
        TipoProductoModelPrueba model = crearModelPrueba();

        model.btnNuevoHandler(null);
        TipoProducto tipo = model.getRegistro();
        tipo.setNombre("Postres");

        doThrow(new RuntimeException("Error simulado"))
                .when(tipoProductoDAO).crear(tipo);

        model.btnGuardarHandler(null);

        assertEquals("No se pudo guardar el registro",
                model.error);

        assertSame(tipo, model.getRegistro());
        assertTrue(model.isNuevo());
        assertNull(model.exito);
    }

    private TipoProductoModelPrueba crearModelPrueba()
            throws Exception {

        TipoProductoModelPrueba model
                = new TipoProductoModelPrueba();

        Field campo = TipoProductoModel.class
                .getDeclaredField("tipoProductoDAO");

        campo.setAccessible(true);
        campo.set(model, tipoProductoDAO);

        return model;
    }

    private static class TipoProductoModelPrueba
            extends TipoProductoModel {

        private String advertencia;
        private String exito;
        private String error;

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
            error = detalle;
        }
    }

}
