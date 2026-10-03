package boundary;

import control.OrdenDAO;
import control.ProductoDAO;
import entity.Descuento;
import entity.DescuentoProducto;
import entity.EmpleadoRol;
import entity.Orden;
import entity.OrdenProducto;
import entity.Producto;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrdenModelTest {

    @Mock
    private OrdenDAO ordenDAO;

    @Mock
    private ProductoDAO productoDAO;

    private OrdenModel ordenModel;

    /*
     * =========================================================
     * MODEL DE PRUEBA
     * =========================================================
     *
     * Evitamos depender de FacesContext.
     *
     * En producción, AbstractModel utiliza FacesContext
     * para mostrar mensajes PrimeFaces.
     *
     * En una prueba unitaria únicamente nos interesa
     * comprobar la lógica de negocio.
     */
    private static class OrdenModelPrueba
            extends OrdenModel {

        @Override
        protected void mostrarExito(
                String detalle) {
            // No se necesita FacesContext en pruebas.
        }

        @Override
        protected void mostrarAdvertencia(
                String detalle) {
            // No se necesita FacesContext en pruebas.
        }

        @Override
        protected void mostrarError(
                String detalle) {
            // No se necesita FacesContext en pruebas.
        }
    }

    @BeforeEach
    void setUp() throws Exception {

        ordenModel
                = new OrdenModelPrueba();

        /*
         * Los campos están inyectados por CDI en producción.
         *
         * En la prueba los colocamos manualmente
         * utilizando reflexión.
         */
        inyectarCampo(
                ordenModel,
                "ordenDAO",
                ordenDAO
        );

        inyectarCampo(
                ordenModel,
                "productoDAO",
                productoDAO
        );

        /*
         * Datos utilizados durante init().
         */
        when(ordenDAO.findAll())
                .thenReturn(
                        new ArrayList<>()
                );

        when(ordenDAO.findCamarerosActivos())
                .thenReturn(
                        new ArrayList<>()
                );

        when(productoDAO.findAll())
                .thenReturn(
                        new ArrayList<>()
                );

        ordenModel.init();
    }

    /*
     * =========================================================
     * CAMAREROS
     * =========================================================
     */
    @Test
    void cargarEmpleadosDebeUsarCamarerosActivos() {

        EmpleadoRol camarero
                = new EmpleadoRol();

        camarero.setIdEmpleadoRol(
                UUID.randomUUID()
        );

        when(ordenDAO.findCamarerosActivos())
                .thenReturn(
                        List.of(camarero)
                );

        ordenModel.init();

        assertEquals(
                1,
                ordenModel
                        .getEmpleadosRol()
                        .size()
        );

        assertSame(
                camarero,
                ordenModel
                        .getEmpleadosRol()
                        .get(0)
        );

        verify(
                ordenDAO,
                atLeastOnce()
        ).findCamarerosActivos();
    }

    /*
     * =========================================================
     * PRECIO SUGERIDO
     * =========================================================
     */
    @Test
    void seleccionarProductoDebeCargarPrecioSugerido() {

        UUID idProducto
                = UUID.randomUUID();

        Producto producto
                = mock(Producto.class);

        when(producto.getActivo())
                .thenReturn(true);

        when(producto.getPrecioSugerido())
                .thenReturn(
                        new BigDecimal("5.00")
                );

        when(productoDAO.findById(idProducto))
                .thenReturn(producto);

        ordenModel
                .setIdProductoSeleccionado(
                        idProducto.toString()
                );

        assertEquals(
                new BigDecimal("5.00"),
                ordenModel.getPrecioProducto()
        );
    }

    /*
     * =========================================================
     * DESCUENTOS APLICABLES
     * =========================================================
     */
    @Test
    void seleccionarProductoDebeCargarDescuentosAplicables() {

        UUID idOrden
                = UUID.randomUUID();

        UUID idProducto
                = UUID.randomUUID();

        Date fechaOrden
                = new Date();

        Orden orden
                = new Orden();

        orden.setIdOrden(
                idOrden
        );

        orden.setFechaCreacion(
                fechaOrden
        );

        Producto producto
                = mock(Producto.class);

        when(producto.getIdProducto())
                .thenReturn(
                        idProducto
                );

        OrdenProducto ordenProducto
                = new OrdenProducto();

        ordenProducto.setIdOrdenProducto(
                UUID.randomUUID()
        );

        ordenProducto.setIdOrden(
                orden
        );

        ordenProducto.setIdProducto(
                producto
        );

        ordenProducto.setPrecio(
                new BigDecimal("5.00")
        );

        DescuentoProducto descuento
                = mock(
                        DescuentoProducto.class
                );

        when(
                ordenDAO.findDescuentosAplicables(
                        idProducto,
                        fechaOrden
                )
        ).thenReturn(
                List.of(descuento)
        );

        ordenModel.setRegistro(
                orden
        );

        ordenModel
                .seleccionarProductoParaDescuento(
                        ordenProducto
                );

        assertEquals(
                1,
                ordenModel
                        .getDescuentosAplicables()
                        .size()
        );

        assertSame(
                descuento,
                ordenModel
                        .getDescuentosAplicables()
                        .get(0)
        );
    }

    /*
     * =========================================================
     * DESCUENTOS ACUMULATIVOS
     * =========================================================
     */
    @Test
    void aplicarDosDescuentosDebeUsarPrecioActual() {

        UUID idOrden
                = UUID.randomUUID();

        UUID idProducto
                = UUID.randomUUID();

        UUID idDescuento10
                = UUID.randomUUID();

        UUID idDescuento20
                = UUID.randomUUID();

        Date fechaOrden
                = new Date();

        /*
         * Orden.
         */
        Orden orden
                = new Orden();

        orden.setIdOrden(
                idOrden
        );

        orden.setFechaCreacion(
                fechaOrden
        );

        /*
         * Producto.
         */
        Producto producto
                = mock(Producto.class);

        when(producto.getIdProducto())
                .thenReturn(
                        idProducto
                );

        /*
         * Producto dentro de la orden.
         *
         * Precio inicial: $5.00
         */
        OrdenProducto ordenProducto
                = new OrdenProducto();

        ordenProducto.setIdOrdenProducto(
                UUID.randomUUID()
        );

        ordenProducto.setIdOrden(
                orden
        );

        ordenProducto.setIdProducto(
                producto
        );

        ordenProducto.setPrecio(
                new BigDecimal("5.00")
        );

        /*
         * =====================================================
         * DESCUENTO 10 %
         * =====================================================
         */
        Descuento descuento10
                = mock(
                        Descuento.class
                );

        when(descuento10.getNombre())
                .thenReturn(
                        "Descuento 10"
                );

        DescuentoProducto descuentoProducto10
                = mock(
                        DescuentoProducto.class
                );

        when(
                descuentoProducto10
                        .getIdDescuentoProducto()
        ).thenReturn(
                idDescuento10
        );

        when(
                descuentoProducto10.getValor()
        ).thenReturn(
                10
        );

        when(
                descuentoProducto10
                        .getIdDescuento()
        ).thenReturn(
                descuento10
        );

        /*
         * =====================================================
         * DESCUENTO 20 %
         * =====================================================
         */
        Descuento descuento20
                = mock(
                        Descuento.class
                );

        when(descuento20.getNombre())
                .thenReturn(
                        "Descuento 20"
                );

        DescuentoProducto descuentoProducto20
                = mock(
                        DescuentoProducto.class
                );

        when(
                descuentoProducto20
                        .getIdDescuentoProducto()
        ).thenReturn(
                idDescuento20
        );

        when(
                descuentoProducto20.getValor()
        ).thenReturn(
                20
        );

        when(
                descuentoProducto20
                        .getIdDescuento()
        ).thenReturn(
                descuento20
        );

        List<DescuentoProducto> descuentos
                = List.of(
                        descuentoProducto10,
                        descuentoProducto20
                );

        /*
         * El Model vuelve a consultar los productos
         * después de aplicar cada descuento.
         */
        when(
                ordenDAO.findProductosByOrden(
                        idOrden
                )
        ).thenReturn(
                List.of(ordenProducto)
        );

        when(
                ordenDAO.findDescuentosAplicables(
                        idProducto,
                        fechaOrden
                )
        ).thenReturn(
                descuentos
        );

        /*
         * Entramos a edición.
         */
        ordenModel.setRegistro(
                orden
        );

        ordenModel.btnModificarHandler(
                null
        );

        /*
         * Seleccionamos el producto.
         */
        ordenModel
                .seleccionarProductoParaDescuento(
                        ordenProducto
                );

        /*
         * =====================================================
         * PRIMER DESCUENTO
         *
         * $5.00 - 10 % = $4.50
         * =====================================================
         */
        ordenModel
                .setIdDescuentoProductoSeleccionado(
                        idDescuento10.toString()
                );

        ordenModel.aplicarDescuento();

        assertEquals(
                new BigDecimal("4.50"),
                ordenProducto.getPrecio()
        );

        /*
         * =====================================================
         * SEGUNDO DESCUENTO
         *
         * Debe utilizar $4.50 como base:
         *
         * $4.50 - 20 % = $3.60
         *
         * NO debe utilizar nuevamente $5.00.
         * =====================================================
         */
        ordenModel
                .setIdDescuentoProductoSeleccionado(
                        idDescuento20.toString()
                );

        ordenModel.aplicarDescuento();

        assertEquals(
                new BigDecimal("3.60"),
                ordenProducto.getPrecio()
        );

        /*
         * =====================================================
         * HISTORIAL
         * =====================================================
         */
        assertNotNull(
                ordenProducto
                        .getObservaciones()
        );

        /*
         * Primer descuento:
         *
         * 10 % de $5.00 = $0.50
         */
        assertTrue(
                ordenProducto
                        .getObservaciones()
                        .contains(
                                "Monto: $0.50"
                        )
        );

        /*
         * Segundo descuento:
         *
         * 20 % de $4.50 = $0.90
         */
        assertTrue(
                ordenProducto
                        .getObservaciones()
                        .contains(
                                "Monto: $0.90"
                        )
        );

        assertTrue(
                ordenProducto
                        .getObservaciones()
                        .contains(
                                "Nuevo precio: $4.50"
                        )
        );

        assertTrue(
                ordenProducto
                        .getObservaciones()
                        .contains(
                                "Nuevo precio: $3.60"
                        )
        );

        /*
         * El producto debe actualizarse dos veces:
         * una por cada descuento.
         */
        verify(
                ordenDAO,
                times(2)
        ).actualizarProducto(
                ordenProducto
        );
    }

    /*
     * =========================================================
     * TOTAL
     * =========================================================
     */
    @Test
    void totalDebeUsarPreciosActuales() {

        UUID idOrden
                = UUID.randomUUID();

        Orden orden
                = new Orden();

        orden.setIdOrden(
                idOrden
        );

        OrdenProducto producto1
                = new OrdenProducto();

        producto1.setPrecio(
                new BigDecimal("3.60")
        );

        OrdenProducto producto2
                = new OrdenProducto();

        producto2.setPrecio(
                new BigDecimal("2.50")
        );

        when(
                ordenDAO.findProductosByOrden(
                        idOrden
                )
        ).thenReturn(
                List.of(
                        producto1,
                        producto2
                )
        );

        ordenModel.setRegistro(
                orden
        );

        ordenModel.btnModificarHandler(
                null
        );

        assertEquals(
                new BigDecimal("6.10"),
                ordenModel.getTotalOrden()
        );
    }

    /*
     * =========================================================
     * ELIMINACIÓN DE ORDEN
     * =========================================================
     */
    @Test
    void noDebeEliminarOrdenMientrasTengaProductos() {

        UUID idOrden
                = UUID.randomUUID();

        Orden orden
                = new Orden();

        orden.setIdOrden(
                idOrden
        );

        ordenModel.setRegistro(
                orden
        );

        when(
                ordenDAO.countProductosByOrden(
                        idOrden
                )
        ).thenReturn(
                2L
        );

        ordenModel.btnEliminarHandler(
                null
        );

        /*
         * Mientras tenga productos,
         * la orden no debe eliminarse.
         */
        verify(
                ordenDAO,
                never()
        ).eliminar(
                any(Orden.class)
        );

        /*
         * La selección debe mantenerse.
         */
        assertSame(
                orden,
                ordenModel.getRegistro()
        );
    }

    /*
     * =========================================================
     * FECHA PASADA
     * =========================================================
     */
    @Test
    void noDebeCrearOrdenConFechaPasada() {

        ordenModel.btnNuevoHandler(
                null
        );

        Calendar calendario
                = Calendar.getInstance(
                        TimeZone.getTimeZone(
                                "America/El_Salvador"
                        )
                );

        calendario.add(
                Calendar.DAY_OF_MONTH,
                -1
        );

        ordenModel
                .getRegistro()
                .setFechaCreacion(
                        calendario.getTime()
                );

        /*
         * La validación de fecha ocurre antes
         * de persistir la orden.
         */
        ordenModel.btnGuardarHandler(
                null
        );

        verify(
                ordenDAO,
                never()
        ).crear(
                any(Orden.class)
        );
    }

    /*
     * =========================================================
     * UTILIDAD PARA SIMULAR @Inject
     * =========================================================
     */
    private void inyectarCampo(
            Object objeto,
            String nombreCampo,
            Object valor)
            throws Exception {

        Field campo
                = objeto
                        .getClass()
                        .getSuperclass()
                        .getDeclaredField(
                                nombreCampo
                        );

        campo.setAccessible(
                true
        );

        campo.set(
                objeto,
                valor
        );
    }
}

