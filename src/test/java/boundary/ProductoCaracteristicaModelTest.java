package boundary;

import control.CaracteristicaDAO;
import control.ProductoCaracteristicaDAO;
import entity.Caracteristica;
import entity.Producto;
import entity.ProductoCaracteristica;
import entity.TipoCaracteristica;
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
class ProductoCaracteristicaModelTest {

    @Mock
    private ProductoCaracteristicaDAO productoCaracteristicaDAO;

    @Mock
    private CaracteristicaDAO caracteristicaDAO;

    @InjectMocks
    private ProductoCaracteristicaModelPrueba productoCaracteristicaModel;

    private static class ProductoCaracteristicaModelPrueba
            extends ProductoCaracteristicaModel {

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
    void debeCargarYActualizarLasCaracteristicasDisponibles() {
        Caracteristica caracteristica = new Caracteristica(UUID.randomUUID());
        when(caracteristicaDAO.findAll())
                .thenReturn(List.of(caracteristica), List.of());

        productoCaracteristicaModel.init();

        assertEquals(List.of(caracteristica),
                productoCaracteristicaModel.getCaracteristicaList());

        productoCaracteristicaModel.cargarCaracteristicas();

        assertTrue(productoCaracteristicaModel.getCaracteristicaList().isEmpty());
        verify(caracteristicaDAO, times(2)).findAll();
    }

    @Test
    void cambiarProductoDebeCargarSusRelacionesYLimpiarLosDatosPendientes() {
        Producto producto = producto();
        ProductoCaracteristica relacion = new ProductoCaracteristica(UUID.randomUUID());
        when(productoCaracteristicaDAO.findByIdProducto(producto.getIdProducto()))
                .thenReturn(List.of(relacion));
        productoCaracteristicaModel.setIdCaracteristicaSeleccionada(UUID.randomUUID().toString());
        productoCaracteristicaModel.setValorCaracteristica("valor pendiente");

        productoCaracteristicaModel.cargarProducto(producto);

        assertSame(producto, productoCaracteristicaModel.getPrincipal());
        assertEquals(List.of(relacion), productoCaracteristicaModel.getModelo());
        assertNull(productoCaracteristicaModel.getIdCaracteristicaSeleccionada());
        assertNull(productoCaracteristicaModel.getValorCaracteristica());
        verify(productoCaracteristicaDAO).findByIdProducto(producto.getIdProducto());
    }

    @Test
    void quitarProductoDebeLimpiarRelacionesSeleccionYValor() {
        for (Producto producto : new Producto[]{null, new Producto()}) {
            productoCaracteristicaModel.setModelo(
                    List.of(new ProductoCaracteristica(UUID.randomUUID())));
            productoCaracteristicaModel.setIdCaracteristicaSeleccionada(UUID.randomUUID().toString());
            productoCaracteristicaModel.setValorCaracteristica("600 ml");

            productoCaracteristicaModel.cargarProducto(producto);

            assertTrue(productoCaracteristicaModel.getModelo().isEmpty());
            assertNull(productoCaracteristicaModel.getIdCaracteristicaSeleccionada());
            assertNull(productoCaracteristicaModel.getValorCaracteristica());
        }
        verifyNoInteractions(productoCaracteristicaDAO);
    }

    @Test
    void debeCrearCaracteristicaConValorQueCoincideConLaExpresionYRecargar() {
        Producto producto = producto();
        Caracteristica caracteristica = prepararSeleccion(producto, "[0-9]+ ml", "600 ml");
        ProductoCaracteristica guardada = new ProductoCaracteristica(UUID.randomUUID());
        when(productoCaracteristicaDAO.findByIdProducto(producto.getIdProducto()))
                .thenReturn(List.of(guardada));

        productoCaracteristicaModel.agregarCaracteristica();

        ArgumentCaptor<ProductoCaracteristica> captor
                = ArgumentCaptor.forClass(ProductoCaracteristica.class);
        verify(productoCaracteristicaDAO).crear(captor.capture());
        ProductoCaracteristica relacion = captor.getValue();
        assertNotNull(relacion.getIdProductoCaracteristica());
        assertSame(producto, relacion.getIdProducto());
        assertSame(caracteristica, relacion.getIdCaracteristica());
        assertEquals("600 ml", relacion.getValor());
        assertEquals(List.of(guardada), productoCaracteristicaModel.getModelo());
        assertNull(productoCaracteristicaModel.getIdCaracteristicaSeleccionada());
        assertNull(productoCaracteristicaModel.getValorCaracteristica());
        assertEquals("Registro asociado correctamente", productoCaracteristicaModel.ultimoMensaje);
        verify(productoCaracteristicaDAO).existeRelacion(
                producto.getIdProducto(), caracteristica.getIdCaracteristica());
    }

    @Test
    void debeRechazarCaracteristicaInactivaSinPersistir() {
        Caracteristica caracteristica = prepararSeleccion(producto(), "[0-9]+ ml", "600 ml");
        caracteristica.setActivo(false);

        productoCaracteristicaModel.agregarCaracteristica();

        assertEquals("No se puede asignar una característica inactiva",
                productoCaracteristicaModel.ultimoMensaje);
        assertEquals(caracteristica.getIdCaracteristica().toString(),
                productoCaracteristicaModel.getIdCaracteristicaSeleccionada());
        assertEquals("600 ml", productoCaracteristicaModel.getValorCaracteristica());
        verify(caracteristicaDAO).findById(caracteristica.getIdCaracteristica());
        verifyNoInteractions(productoCaracteristicaDAO);
    }

    @Test
    void debeRechazarValorQueNoCoincideConLaExpresionRegular() {
        prepararSeleccion(producto(), "[0-9]+ ml", "600 gramos");

        productoCaracteristicaModel.agregarCaracteristica();

        assertEquals("El valor ingresado no cumple con el formato requerido",
                productoCaracteristicaModel.ultimoMensaje);
        assertEquals("600 gramos", productoCaracteristicaModel.getValorCaracteristica());
        verifyNoInteractions(productoCaracteristicaDAO);
    }

    @Test
    void debeInformarExpresionRegularMalConfiguradaSinPersistir() {
        prepararSeleccion(producto(), "[", "600 ml");

        productoCaracteristicaModel.agregarCaracteristica();

        assertEquals("La expresión regular configurada no es válida",
                productoCaracteristicaModel.ultimoMensaje);
        verifyNoInteractions(productoCaracteristicaDAO);
    }

    @Test
    void debePermitirValorCuandoLaCaracteristicaNoTieneTipo() {
        Caracteristica caracteristica = prepararSeleccion(producto(), null, "grande");
        caracteristica.setIdTipoCaracteristica(null);

        productoCaracteristicaModel.agregarCaracteristica();

        verificarValorPersistido("grande");
    }

    @Test
    void debePermitirValorCuandoLaExpresionRegularEsNula() {
        prepararSeleccion(producto(), null, "grande");

        productoCaracteristicaModel.agregarCaracteristica();

        verificarValorPersistido("grande");
    }

    @Test
    void debePermitirValorCuandoLaExpresionRegularEstaEnBlanco() {
        prepararSeleccion(producto(), "  ", "grande");

        productoCaracteristicaModel.agregarCaracteristica();

        verificarValorPersistido("grande");
    }

    @Test
    void debeExigirCaracteristicaSeleccionadaAntesDeConsultar() {
        for (String seleccion : new String[]{null, "", "  "}) {
            productoCaracteristicaModel.setIdCaracteristicaSeleccionada(seleccion);

            productoCaracteristicaModel.ultimoMensaje = null;
            productoCaracteristicaModel.agregarCaracteristica();

            assertEquals("Debe seleccionar una característica",
                    productoCaracteristicaModel.ultimoMensaje);
        }
        verifyNoInteractions(caracteristicaDAO, productoCaracteristicaDAO);
    }

    @Test
    void debeExigirValorNoVacioAntesDeConsultarLaCaracteristica() {
        productoCaracteristicaModel.setIdCaracteristicaSeleccionada(UUID.randomUUID().toString());
        for (String valor : new String[]{null, "", "  "}) {
            productoCaracteristicaModel.setValorCaracteristica(valor);

            productoCaracteristicaModel.ultimoMensaje = null;
            productoCaracteristicaModel.agregarCaracteristica();

            assertEquals("Debe ingresar un valor para la característica",
                    productoCaracteristicaModel.ultimoMensaje);
        }
        verifyNoInteractions(caracteristicaDAO, productoCaracteristicaDAO);
    }

    @Test
    void debeRechazarIdentificadorMalFormadoSinConsultarElDAO() {
        productoCaracteristicaModel.setIdCaracteristicaSeleccionada("no-es-un-uuid");
        productoCaracteristicaModel.setValorCaracteristica("600 ml");

        productoCaracteristicaModel.agregarCaracteristica();

        assertEquals("La característica seleccionada no existe",
                productoCaracteristicaModel.ultimoMensaje);
        verifyNoInteractions(caracteristicaDAO, productoCaracteristicaDAO);
    }

    @Test
    void buscarCaracteristicaSinIdentificadorNoDebeConsultarElDAO() {
        assertNull(productoCaracteristicaModel.buscarOpcion(null));
        assertNull(productoCaracteristicaModel.buscarOpcion("  "));
        verifyNoInteractions(caracteristicaDAO);
    }

    @Test
    void debeRechazarCaracteristicaQueYaNoExiste() {
        UUID idCaracteristica = UUID.randomUUID();
        productoCaracteristicaModel.setIdCaracteristicaSeleccionada(idCaracteristica.toString());
        productoCaracteristicaModel.setValorCaracteristica("600 ml");

        productoCaracteristicaModel.agregarCaracteristica();

        assertEquals("La característica seleccionada no existe",
                productoCaracteristicaModel.ultimoMensaje);
        verify(caracteristicaDAO).findById(idCaracteristica);
        verifyNoInteractions(productoCaracteristicaDAO);
    }

    @Test
    void debeRechazarCaracteristicaYaAsociadaYConservarElValorPendiente() {
        Producto producto = producto();
        Caracteristica caracteristica = prepararSeleccion(producto, ".+", "grande");
        when(productoCaracteristicaDAO.existeRelacion(
                producto.getIdProducto(), caracteristica.getIdCaracteristica()))
                .thenReturn(true);

        productoCaracteristicaModel.agregarCaracteristica();

        assertEquals("La relación ya existe", productoCaracteristicaModel.ultimoMensaje);
        assertEquals("grande", productoCaracteristicaModel.getValorCaracteristica());
        assertEquals(caracteristica.getIdCaracteristica().toString(),
                productoCaracteristicaModel.getIdCaracteristicaSeleccionada());
        verify(productoCaracteristicaDAO, never()).crear(any(ProductoCaracteristica.class));
    }

    @Test
    void quitarCaracteristicaDebeRecargarYLimpiarElValorPendiente() {
        Producto producto = producto();
        ProductoCaracteristica relacion = new ProductoCaracteristica(UUID.randomUUID());
        productoCaracteristicaModel.setPrincipal(producto);
        productoCaracteristicaModel.setModelo(List.of(relacion));
        productoCaracteristicaModel.setValorCaracteristica("valor pendiente");
        productoCaracteristicaModel.setIdCaracteristicaSeleccionada(UUID.randomUUID().toString());
        when(productoCaracteristicaDAO.findByIdProducto(producto.getIdProducto()))
                .thenReturn(List.of());

        productoCaracteristicaModel.quitarCaracteristica(relacion);

        verify(productoCaracteristicaDAO).eliminar(relacion);
        verify(productoCaracteristicaDAO).findByIdProducto(producto.getIdProducto());
        assertTrue(productoCaracteristicaModel.getModelo().isEmpty());
        assertNull(productoCaracteristicaModel.getValorCaracteristica());
        assertNull(productoCaracteristicaModel.getIdCaracteristicaSeleccionada());
        assertEquals("Relación eliminada correctamente", productoCaracteristicaModel.ultimoMensaje);
    }

    private Producto producto() {
        Producto producto = new Producto();
        producto.setIdProducto(UUID.randomUUID());
        return producto;
    }

    private Caracteristica prepararSeleccion(Producto producto, String expresion, String valor) {
        TipoCaracteristica tipo = new TipoCaracteristica(UUID.randomUUID());
        tipo.setExpresionRegular(expresion);
        Caracteristica caracteristica = new Caracteristica(UUID.randomUUID());
        caracteristica.setIdTipoCaracteristica(tipo);
        caracteristica.setActivo(true);
        when(caracteristicaDAO.findById(caracteristica.getIdCaracteristica()))
                .thenReturn(caracteristica);
        productoCaracteristicaModel.setPrincipal(producto);
        productoCaracteristicaModel.setIdCaracteristicaSeleccionada(
                caracteristica.getIdCaracteristica().toString());
        productoCaracteristicaModel.setValorCaracteristica(valor);
        return caracteristica;
    }

    private void verificarValorPersistido(String valor) {
        ArgumentCaptor<ProductoCaracteristica> captor
                = ArgumentCaptor.forClass(ProductoCaracteristica.class);
        verify(productoCaracteristicaDAO).crear(captor.capture());
        assertEquals(valor, captor.getValue().getValor());
        assertEquals("Registro asociado correctamente", productoCaracteristicaModel.ultimoMensaje);
    }
}
