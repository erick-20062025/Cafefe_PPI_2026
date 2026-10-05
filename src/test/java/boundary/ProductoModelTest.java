package boundary;

import control.ProductoDAO;
import entity.Producto;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.component.tabview.Tab;
import org.primefaces.event.TabChangeEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoModelTest {

    @Mock private ProductoDAO productoDAO;
    @Mock private ProductoTipoProductoModel tiposModel;
    @Mock private ProductoCaracteristicaModel caracteristicasModel;
    private ProductoModelPrueba model;

    private static class ProductoModelPrueba extends ProductoModel {
        private String exito;
        private String error;

        @Override protected void mostrarExito(String detalle) { exito = detalle; }
        @Override protected void mostrarAdvertencia(String detalle) { }
        @Override protected void mostrarError(String detalle) { error = detalle; }
    }

    @BeforeEach
    void setUp() throws Exception {
        model = new ProductoModelPrueba();
        inyectar("productoDAO", productoDAO);
        inyectar("productoTipoProductoModel", tiposModel);
        inyectar("productoCaracteristicaModel", caracteristicasModel);
    }

    @Test
    void debeInicializarCatalogoDesdeDAO() {
        List<Producto> productos = List.of(productoGuardado());
        when(productoDAO.findAll()).thenReturn(productos);

        model.init();

        assertSame(productos, model.getModelo());
        verify(productoDAO).findAll();
    }

    @Test
    void nuevoDebeCrearProductoActivoYLimpiarRelaciones() {
        model.setIndiceTab(1);
        model.editando = true;
        model.consultando = true;

        model.btnNuevoHandler(null);

        assertNotNull(model.getRegistro().getIdProducto());
        assertTrue(model.getRegistro().getActivo());
        assertTrue(model.isNuevo());
        assertFalse(model.isEditando());
        assertFalse(model.isConsultando());
        assertFalse(model.isPuedeGestionarRelaciones());
        assertFalse(model.isPuedeGestionarTipos());
        assertEquals(0, model.getIndiceTab());
        verificarLimpiezaRelaciones();
        verifyNoInteractions(productoDAO);
    }

    @Test
    void seleccionarDebeCargarAmbasRelacionesYHabilitarGestion() {
        Producto producto = productoGuardado();
        model.setRegistro(producto);
        model.setIndiceTab(1);
        model.nuevo = true;
        model.editando = true;
        model.consultando = true;

        model.onRowSelect();

        assertSame(producto, model.getRegistro());
        assertFalse(model.isNuevo());
        assertFalse(model.isEditando());
        assertFalse(model.isConsultando());
        assertTrue(model.isPuedeGestionarRelaciones());
        assertTrue(model.isPuedeGestionarTipos());
        assertTrue(model.isPuedeEditar());
        assertTrue(model.isPuedeEliminar());
        assertEquals(0, model.getIndiceTab());
        verify(tiposModel).cargarProducto(same(producto));
        verify(caracteristicasModel).cargarProducto(same(producto));
    }

    @Test
    void noDebeGestionarRelacionesSinProductoPersistido() {
        assertFalse(model.isPuedeGestionarRelaciones());
        assertFalse(model.isPuedeEditar());
        assertFalse(model.isPuedeEliminar());
        model.setRegistro(new Producto());
        assertFalse(model.isPuedeGestionarRelaciones());
    }

    @Test
    void deseleccionarDebeLimpiarRegistroEstadosYRelaciones() {
        prepararEstadoSeleccionado();

        model.onRowUnselect();

        verificarEstadoLimpio();
        assertEquals(0, model.getIndiceTab());
        verificarLimpiezaRelaciones();
    }

    @Test
    void cancelarDebeLimpiarRegistroEstadosYRelaciones() {
        prepararEstadoSeleccionado();

        model.btnCancelarHandler(null);

        verificarEstadoLimpio();
        assertEquals(0, model.getIndiceTab());
        verificarLimpiezaRelaciones();
        verifyNoInteractions(productoDAO);
    }

    @Test
    void debeCambiarIndiceSegunLaPestana() {
        TabChangeEvent evento = mock(TabChangeEvent.class);
        Tab tab = mock(Tab.class);
        when(evento.getTab()).thenReturn(tab);
        when(tab.getTitle()).thenReturn("Relaciones", "Producto");

        model.onTabChange(evento);
        assertEquals(1, model.getIndiceTab());
        model.onTabChange(evento);
        assertEquals(0, model.getIndiceTab());
    }

    @Test
    void guardarNuevoDebeCrearRecargarYLimpiarEstado() {
        model.btnNuevoHandler(null);
        Producto producto = model.getRegistro();
        List<Producto> recargados = List.of(producto);
        when(productoDAO.findAll()).thenReturn(recargados);

        model.btnGuardarHandler(null);

        verify(productoDAO).crear(same(producto));
        verify(productoDAO, never()).modificar(any());
        assertSame(recargados, model.getModelo());
        verificarEstadoLimpio();
        assertEquals("Registro guardado correctamente", model.exito);
    }

    @Test
    void modificarDebeActivarEdicionYPersistirLaSeleccion() {
        Producto producto = productoGuardado();
        model.setRegistro(producto);
        model.nuevo = true;
        model.consultando = true;

        model.btnModificarHandler(null);

        assertTrue(model.isEditando());
        assertFalse(model.isNuevo());
        assertFalse(model.isConsultando());
        model.btnGuardarHandler(null);
        verify(productoDAO).modificar(same(producto));
        verify(productoDAO, never()).crear(any());
        verify(productoDAO).findAll();
        verificarEstadoLimpio();
    }

    @Test
    void accionesSinRegistroNoDebenPersistirNiActivarEdicion() {
        model.btnModificarHandler(null);
        model.btnGuardarHandler(null);
        model.btnEliminarHandler(null);

        verificarEstadoLimpio();
        verifyNoInteractions(productoDAO);
    }

    @Test
    void guardarSeleccionSinEdicionNoDebeCrearNiModificar() {
        model.setRegistro(productoGuardado());
        model.onRowSelect();

        model.btnGuardarHandler(null);

        verify(productoDAO, never()).crear(any());
        verify(productoDAO, never()).modificar(any());
        verify(productoDAO).findAll();
        verificarEstadoLimpio();
    }

    @Test
    void falloAlGuardarDebeConservarRegistroYModoNuevo() {
        model.btnNuevoHandler(null);
        Producto producto = model.getRegistro();
        doThrow(new IllegalStateException("Fallo simulado"))
                .when(productoDAO).crear(same(producto));

        model.btnGuardarHandler(null);

        assertSame(producto, model.getRegistro());
        assertTrue(model.isNuevo());
        assertEquals("No se pudo guardar el registro", model.error);
        assertNull(model.exito);
        verify(productoDAO, never()).findAll();
    }

    @Test
    void eliminarDebeBorrarSeleccionRecargarYLimpiarEstado() {
        Producto producto = productoGuardado();
        model.setRegistro(producto);
        model.editando = true;
        List<Producto> restantes = List.of();
        when(productoDAO.findAll()).thenReturn(restantes);

        model.btnEliminarHandler(null);

        verify(productoDAO).eliminar(same(producto));
        assertSame(restantes, model.getModelo());
        verificarEstadoLimpio();
        assertEquals("Registro eliminado correctamente", model.exito);
    }

    @Test
    void falloAlEliminarDebeMantenerLaSeleccion() {
        Producto producto = productoGuardado();
        model.setRegistro(producto);
        doThrow(new IllegalStateException("Fallo simulado"))
                .when(productoDAO).eliminar(same(producto));

        model.btnEliminarHandler(null);

        assertSame(producto, model.getRegistro());
        assertEquals("No se pudo eliminar el registro", model.error);
        assertNull(model.exito);
        verify(productoDAO, never()).findAll();
    }

    private Producto productoGuardado() {
        Producto producto = new Producto();
        producto.setIdProducto(UUID.randomUUID());
        return producto;
    }

    private void prepararEstadoSeleccionado() {
        model.setRegistro(productoGuardado());
        model.setIndiceTab(1);
        model.nuevo = true;
        model.editando = true;
        model.consultando = true;
    }

    private void verificarEstadoLimpio() {
        assertNull(model.getRegistro());
        assertFalse(model.isNuevo());
        assertFalse(model.isEditando());
        assertFalse(model.isConsultando());
    }

    private void verificarLimpiezaRelaciones() {
        verify(tiposModel).cargarProducto(null);
        verify(caracteristicasModel).cargarProducto(null);
    }

    private void inyectar(String nombre, Object valor) throws Exception {
        Field campo = ProductoModel.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(model, valor);
    }
}
