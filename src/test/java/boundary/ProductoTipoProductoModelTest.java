package boundary;

import control.ProductoTipoProductoDAO;
import control.TipoProductoDAO;
import entity.Producto;
import entity.ProductoTipoProducto;
import entity.TipoProducto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoTipoProductoModelTest {

    @Mock
    private ProductoTipoProductoDAO productoTipoProductoDAO;

    @Mock
    private TipoProductoDAO tipoProductoDAO;

    @InjectMocks
    private ProductoTipoProductoModelPrueba productoTipoProductoModel;

    private static class ProductoTipoProductoModelPrueba
            extends ProductoTipoProductoModel {

        private String ultimoMensaje;

        @Override
        protected void mostrarExito(String detalle) {
            ultimoMensaje = detalle;
        }

        @Override
        protected void mostrarAdvertencia(String detalle) {
            ultimoMensaje = detalle;
        }

        @Override
        protected void mostrarError(String detalle) {
            ultimoMensaje = detalle;
        }
    }

    @Test
    void debeCargarTiposActivosEInactivosYNormalizarUnaRecargaNula() {
        TipoProducto activo = tipoProducto(true);
        TipoProducto inactivo = tipoProducto(false);
        when(tipoProductoDAO.findAll())
                .thenReturn(List.of(activo, inactivo))
                .thenReturn(null);

        productoTipoProductoModel.init();

        assertEquals(List.of(activo, inactivo), productoTipoProductoModel.getTipoProductoList());

        productoTipoProductoModel.cargarTiposProducto();

        assertNotNull(productoTipoProductoModel.getTipoProductoList());
        assertTrue(productoTipoProductoModel.getTipoProductoList().isEmpty());
        verify(tipoProductoDAO, times(2)).findAll();
    }

    @Test
    void debeAsociarTipoActivoConIdentificadorFechaYRecargarRelaciones() {
        Producto producto = producto();
        TipoProducto tipo = prepararSeleccion(producto, true);
        ProductoTipoProducto guardada = new ProductoTipoProducto(UUID.randomUUID());
        when(productoTipoProductoDAO.findByIdProducto(producto.getIdProducto()))
                .thenReturn(List.of(guardada));
        long inicio = System.currentTimeMillis();

        productoTipoProductoModel.agregarTipo();

        ArgumentCaptor<ProductoTipoProducto> captor
                = ArgumentCaptor.forClass(ProductoTipoProducto.class);
        verify(productoTipoProductoDAO).crear(captor.capture());
        ProductoTipoProducto relacion = captor.getValue();
        assertNotNull(relacion.getIdProductoTipoProducto());
        assertSame(producto, relacion.getProducto());
        assertSame(tipo, relacion.getIdTipoProducto());
        assertNotNull(relacion.getFechaCreacion());
        assertTrue(relacion.getFechaCreacion().getTime() >= inicio);
        assertTrue(relacion.getFechaCreacion().getTime() <= System.currentTimeMillis());
        assertEquals(List.of(guardada), productoTipoProductoModel.getModelo());
        assertNull(productoTipoProductoModel.getIdTipoProductoSeleccionado());
        assertEquals("Registro asociado correctamente", productoTipoProductoModel.ultimoMensaje);
        verify(productoTipoProductoDAO).existeRelacion(producto.getIdProducto(), tipo.getIdTipoProducto());
    }

    @Test
    void debeRechazarTipoInactivoSinPersistir() {
        Producto producto = producto();
        TipoProducto tipo = prepararSeleccion(producto, false);

        productoTipoProductoModel.agregarTipo();

        assertEquals("No se puede asignar un tipo de producto inactivo",
                productoTipoProductoModel.ultimoMensaje);
        assertEquals(tipo.getIdTipoProducto().toString(),
                productoTipoProductoModel.getIdTipoProductoSeleccionado());
        verify(tipoProductoDAO).findById(tipo.getIdTipoProducto());
        verifyNoInteractions(productoTipoProductoDAO);
    }

    @Test
    void debeRechazarTipoDuplicadoSinPerderLaSeleccion() {
        Producto producto = producto();
        TipoProducto tipo = prepararSeleccion(producto, true);
        when(productoTipoProductoDAO.existeRelacion(producto.getIdProducto(), tipo.getIdTipoProducto()))
                .thenReturn(true);

        productoTipoProductoModel.agregarTipo();

        assertEquals("La relación ya existe", productoTipoProductoModel.ultimoMensaje);
        assertEquals(tipo.getIdTipoProducto().toString(),
                productoTipoProductoModel.getIdTipoProductoSeleccionado());
        verify(productoTipoProductoDAO, never()).crear(any(ProductoTipoProducto.class));
        verify(productoTipoProductoDAO, never()).findByIdProducto(any(UUID.class));
    }

    @Test
    void debeExigirProductoPersistidoParaAgregarTipo() {
        TipoProducto tipo = prepararSeleccion(null, true);
        for (Producto producto : new Producto[]{null, new Producto()}) {
            productoTipoProductoModel.setProducto(producto);

            productoTipoProductoModel.ultimoMensaje = null;
            productoTipoProductoModel.agregarTipo();

            assertEquals("Debe seleccionar un registro principal",
                    productoTipoProductoModel.ultimoMensaje);
        }
        verify(tipoProductoDAO, times(2)).findById(tipo.getIdTipoProducto());
        verifyNoInteractions(productoTipoProductoDAO);
    }

    @Test
    void debeExigirSeleccionNoVaciaAntesDeConsultarTipo() {
        productoTipoProductoModel.setProducto(producto());
        for (String seleccion : new String[]{null, "", "  "}) {
            productoTipoProductoModel.setIdTipoProductoSeleccionado(seleccion);

            productoTipoProductoModel.ultimoMensaje = null;
            productoTipoProductoModel.agregarTipo();

            assertEquals("Debe seleccionar un tipo de producto", productoTipoProductoModel.ultimoMensaje);
        }
        verifyNoInteractions(productoTipoProductoDAO, tipoProductoDAO);
    }

    @Test
    void debeRechazarIdentificadorMalFormadoSinConsultarTipo() {
        productoTipoProductoModel.setProducto(producto());
        productoTipoProductoModel.setIdTipoProductoSeleccionado("tipo-invalido");

        productoTipoProductoModel.agregarTipo();

        assertEquals("El tipo de producto seleccionado no existe", productoTipoProductoModel.ultimoMensaje);
        verifyNoInteractions(productoTipoProductoDAO, tipoProductoDAO);
    }

    @Test
    void buscarTipoSinIdentificadorNoDebeConsultarElDAO() {
        assertNull(productoTipoProductoModel.buscarOpcion(null));
        assertNull(productoTipoProductoModel.buscarOpcion("  "));
        verifyNoInteractions(tipoProductoDAO);
    }

    @Test
    void debeRechazarTipoQueYaNoExiste() {
        UUID idTipo = UUID.randomUUID();
        productoTipoProductoModel.setProducto(producto());
        productoTipoProductoModel.setIdTipoProductoSeleccionado(idTipo.toString());

        productoTipoProductoModel.agregarTipo();

        assertEquals("El tipo de producto seleccionado no existe", productoTipoProductoModel.ultimoMensaje);
        verify(tipoProductoDAO).findById(idTipo);
        verifyNoInteractions(productoTipoProductoDAO);
    }

    @Test
    void cargarProductoDebeReemplazarRelacionesYLimpiarSeleccion() {
        Producto producto = producto();
        ProductoTipoProducto relacion = new ProductoTipoProducto(UUID.randomUUID());
        when(productoTipoProductoDAO.findByIdProducto(producto.getIdProducto()))
                .thenReturn(List.of(relacion));
        productoTipoProductoModel.setIdTipoProductoSeleccionado(UUID.randomUUID().toString());

        productoTipoProductoModel.cargarProducto(producto);

        assertSame(producto, productoTipoProductoModel.getProducto());
        assertEquals(List.of(relacion), productoTipoProductoModel.getModelo());
        assertNull(productoTipoProductoModel.getIdTipoProductoSeleccionado());

        productoTipoProductoModel.cargarProducto(null);

        assertNull(productoTipoProductoModel.getProducto());
        assertTrue(productoTipoProductoModel.getModelo().isEmpty());
        verify(productoTipoProductoDAO).findByIdProducto(producto.getIdProducto());
        verifyNoMoreInteractions(productoTipoProductoDAO);
    }

    @Test
    void debeNormalizarRelacionesNulasAlCargarUnProducto() {
        Producto producto = producto();
        when(productoTipoProductoDAO.findByIdProducto(producto.getIdProducto())).thenReturn(null);
        productoTipoProductoModel.setModelo(List.of(new ProductoTipoProducto(UUID.randomUUID())));

        productoTipoProductoModel.cargarProducto(producto);

        assertNotNull(productoTipoProductoModel.getModelo());
        assertTrue(productoTipoProductoModel.getModelo().isEmpty());
        verify(productoTipoProductoDAO).findByIdProducto(producto.getIdProducto());
    }

    @Test
    void nuevoDebeConstruirRelacionSeleccionadaSinPersistirTodavia() {
        Producto producto = producto();
        TipoProducto tipo = prepararSeleccion(producto, true);

        productoTipoProductoModel.btnNuevoHandler(null);

        ProductoTipoProducto registro = productoTipoProductoModel.getRegistro();
        assertNotNull(registro);
        assertNotNull(registro.getIdProductoTipoProducto());
        assertNotNull(registro.getFechaCreacion());
        assertSame(producto, registro.getProducto());
        assertSame(tipo, registro.getIdTipoProducto());
        assertTrue(productoTipoProductoModel.isNuevo());
        verifyNoInteractions(productoTipoProductoDAO);
    }

    @Test
    void nuevoNoDebeConstruirRelacionCuandoFaltanProductoUOpcion() {
        productoTipoProductoModel.btnNuevoHandler(null);
        assertNull(productoTipoProductoModel.getRegistro());

        productoTipoProductoModel.setProducto(producto());
        productoTipoProductoModel.btnNuevoHandler(null);
        assertNull(productoTipoProductoModel.getRegistro());

        productoTipoProductoModel.setIdTipoProductoSeleccionado("  ");
        productoTipoProductoModel.btnNuevoHandler(null);
        assertNull(productoTipoProductoModel.getRegistro());

        UUID idInexistente = UUID.randomUUID();
        productoTipoProductoModel.setIdTipoProductoSeleccionado(idInexistente.toString());
        productoTipoProductoModel.btnNuevoHandler(null);
        assertNull(productoTipoProductoModel.getRegistro());

        verify(tipoProductoDAO).findById(idInexistente);
        verifyNoMoreInteractions(tipoProductoDAO);
        verifyNoInteractions(productoTipoProductoDAO);
    }

    @Test
    void quitarTipoDebeIgnorarNullYEliminarSoloLaRelacionSeleccionada() {
        productoTipoProductoModel.quitarTipo(null);
        verifyNoInteractions(productoTipoProductoDAO);

        Producto producto = producto();
        ProductoTipoProducto relacion = new ProductoTipoProducto(UUID.randomUUID());
        productoTipoProductoModel.setProducto(producto);
        productoTipoProductoModel.setModelo(List.of(relacion));
        productoTipoProductoModel.setIdTipoProductoSeleccionado(UUID.randomUUID().toString());
        when(productoTipoProductoDAO.findByIdProducto(producto.getIdProducto())).thenReturn(List.of());

        productoTipoProductoModel.quitarTipo(relacion);

        verify(productoTipoProductoDAO).eliminar(relacion);
        verify(productoTipoProductoDAO).findByIdProducto(producto.getIdProducto());
        verifyNoInteractions(tipoProductoDAO);
        assertTrue(productoTipoProductoModel.getModelo().isEmpty());
        assertNull(productoTipoProductoModel.getIdTipoProductoSeleccionado());
        assertEquals("Relación eliminada correctamente", productoTipoProductoModel.ultimoMensaje);
    }

    @Test
    void errorAlCrearDebeConservarSeleccionYRelacionesParaCorregir() {
        Producto producto = producto();
        TipoProducto tipo = prepararSeleccion(producto, true);
        List<ProductoTipoProducto> anteriores = List.of(new ProductoTipoProducto(UUID.randomUUID()));
        productoTipoProductoModel.setModelo(anteriores);
        doThrow(new IllegalStateException("Fallo simulado al crear"))
                .when(productoTipoProductoDAO).crear(any(ProductoTipoProducto.class));

        productoTipoProductoModel.agregarTipo();

        assertEquals("No se pudo crear la relación", productoTipoProductoModel.ultimoMensaje);
        assertSame(anteriores, productoTipoProductoModel.getModelo());
        assertEquals(tipo.getIdTipoProducto().toString(),
                productoTipoProductoModel.getIdTipoProductoSeleccionado());
        verify(productoTipoProductoDAO).crear(any(ProductoTipoProducto.class));
        verify(productoTipoProductoDAO, never()).findByIdProducto(any(UUID.class));
    }

    @Test
    void errorAlEliminarDebeConservarLaRelacionYLaSeleccion() {
        Producto producto = producto();
        ProductoTipoProducto relacion = new ProductoTipoProducto(UUID.randomUUID());
        String seleccion = UUID.randomUUID().toString();
        productoTipoProductoModel.setProducto(producto);
        productoTipoProductoModel.setModelo(List.of(relacion));
        productoTipoProductoModel.setIdTipoProductoSeleccionado(seleccion);
        doThrow(new IllegalStateException("Fallo simulado al eliminar"))
                .when(productoTipoProductoDAO).eliminar(relacion);

        productoTipoProductoModel.quitarTipo(relacion);

        assertEquals("No se pudo eliminar la relación", productoTipoProductoModel.ultimoMensaje);
        assertEquals(List.of(relacion), productoTipoProductoModel.getModelo());
        assertEquals(seleccion, productoTipoProductoModel.getIdTipoProductoSeleccionado());
        verify(productoTipoProductoDAO).eliminar(relacion);
        verify(productoTipoProductoDAO, never()).findByIdProducto(any(UUID.class));
    }

    private Producto producto() {
        Producto producto = new Producto();
        producto.setIdProducto(UUID.randomUUID());
        return producto;
    }

    private TipoProducto tipoProducto(boolean activo) {
        TipoProducto tipo = new TipoProducto(UUID.randomUUID());
        tipo.setActivo(activo);
        return tipo;
    }

    private TipoProducto prepararSeleccion(Producto producto, boolean activo) {
        TipoProducto tipo = tipoProducto(activo);
        productoTipoProductoModel.setProducto(producto);
        productoTipoProductoModel.setIdTipoProductoSeleccionado(tipo.getIdTipoProducto().toString());
        when(tipoProductoDAO.findById(tipo.getIdTipoProducto())).thenReturn(tipo);
        return tipo;
    }
}
