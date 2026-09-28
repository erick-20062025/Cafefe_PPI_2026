package boundary;

import control.DescuentoProductoDAO;
import control.ProductoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.DescuentoProducto;
import entity.Producto;
import entity.TipoDescuento;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DescuentoProductoModelTest {

    @Mock
    private DescuentoProductoDAO descuentoProductoDAO;

    @Mock
    private ProductoDAO productoDAO;

    @Mock
    private TipoDescuentoDAO tipoDescuentoDAO;

    @InjectMocks
    private DescuentoProductoModelPrueba descuentoProductoModel;

    /*
     * Esta clase evita depender de FacesContext durante
     * las pruebas unitarias.
     */
    static class DescuentoProductoModelPrueba
            extends DescuentoProductoModel {

        private String ultimoMensaje;

        @Override
        protected void mostrarAdvertencia(String mensaje) {
            ultimoMensaje = mensaje;
        }

        @Override
        protected void mostrarExito(String mensaje) {
            ultimoMensaje = mensaje;
        }

        public String getUltimoMensaje() {
            return ultimoMensaje;
        }
    }

    @Test
    void debeCargarDescuentoMaximoYFechasDelDescuento() {

        UUID idDescuento = UUID.randomUUID();
        UUID idTipoDescuento = UUID.randomUUID();

        Date fechaDesde = new Date(1000);
        Date fechaHasta = new Date(2000);

        Descuento descuento = new Descuento();
        descuento.setIdDescuento(idDescuento);
        descuento.setIdTipoDescuento(idTipoDescuento);
        descuento.setFechaDesde(fechaDesde);
        descuento.setFechaHasta(fechaHasta);

        TipoDescuento tipoDescuento = new TipoDescuento();
        tipoDescuento.setIdTipoDescuento(idTipoDescuento);
        tipoDescuento.setDescuentoMaximo(10);

        when(
                descuentoProductoDAO.findByIdDescuento(
                        idDescuento
                )
        ).thenReturn(List.of());

        when(
                tipoDescuentoDAO.findById(
                        idTipoDescuento
                )
        ).thenReturn(tipoDescuento);

        descuentoProductoModel.cargarDescuento(
                descuento
        );

        assertEquals(
                10,
                descuentoProductoModel.getDescuentoMaximo()
        );

        assertEquals(
                fechaDesde,
                descuentoProductoModel.getFechaDesde()
        );

        assertEquals(
                fechaHasta,
                descuentoProductoModel.getFechaHasta()
        );

        verify(
                descuentoProductoDAO
        ).findByIdDescuento(idDescuento);

        verify(
                tipoDescuentoDAO
        ).findById(idTipoDescuento);
    }

    @Test
    void debePermitirRepetirProductoEnUnDescuento() {

        assertTrue(
                descuentoProductoModel
                        .permiteRelacionDuplicada()
        );
    }

    @Test
    void debeConstruirRelacionConTodosLosDatos() {

        Descuento descuento = new Descuento();
        descuento.setIdDescuento(
                UUID.randomUUID()
        );

        Producto producto = new Producto();
        producto.setIdProducto(
                UUID.randomUUID()
        );

        Date fechaDesde = new Date(1000);
        Date fechaHasta = new Date(2000);

        descuentoProductoModel.setFechaDesde(
                fechaDesde
        );

        descuentoProductoModel.setFechaHasta(
                fechaHasta
        );

        descuentoProductoModel.setValor(8);

        descuentoProductoModel.setObservaciones(
                "Descuento especial"
        );

        DescuentoProducto relacion =
                descuentoProductoModel.construirRelacion(
                        descuento,
                        producto
                );

        assertNotNull(
                relacion.getIdDescuentoProducto()
        );

        assertSame(
                descuento,
                relacion.getIdDescuento()
        );

        assertSame(
                producto,
                relacion.getIdProducto()
        );

        assertEquals(
                8,
                relacion.getValor()
        );

        assertEquals(
                fechaDesde,
                relacion.getFechaDesde()
        );

        assertEquals(
                fechaHasta,
                relacion.getFechaHasta()
        );

        assertEquals(
                "Descuento especial",
                relacion.getObservaciones()
        );
    }

    @Test
    void debePermitirDescuentoMenorAlMaximo() {

        Descuento descuento =
                prepararDescuentoConMaximo(10);

        Producto producto = new Producto();

        UUID idProducto = UUID.randomUUID();

        producto.setIdProducto(idProducto);
        producto.setNombre("Orchata");

        when(
                productoDAO.findById(idProducto)
        ).thenReturn(producto);

        descuentoProductoModel
                .setIdProductoSeleccionado(
                        idProducto.toString()
                );

        descuentoProductoModel.setValor(5);

        descuentoProductoModel.agregarProducto();

        ArgumentCaptor<DescuentoProducto> captor =
                ArgumentCaptor.forClass(
                        DescuentoProducto.class
                );

        verify(
                descuentoProductoDAO
        ).crear(captor.capture());

        DescuentoProducto relacion =
                captor.getValue();

        assertEquals(
                5,
                relacion.getValor()
        );

        assertSame(
                producto,
                relacion.getIdProducto()
        );
    }

    @Test
    void debePermitirDescuentoIgualAlMaximo() {

        prepararDescuentoConMaximo(10);

        Producto producto = new Producto();

        UUID idProducto = UUID.randomUUID();

        producto.setIdProducto(idProducto);

        when(
                productoDAO.findById(idProducto)
        ).thenReturn(producto);

        descuentoProductoModel
                .setIdProductoSeleccionado(
                        idProducto.toString()
                );

        descuentoProductoModel.setValor(10);

        descuentoProductoModel.agregarProducto();

        verify(
                descuentoProductoDAO
        ).crear(any(DescuentoProducto.class));
    }

    @Test
    void debeRechazarDescuentoMayorAlMaximo() {

        prepararDescuentoConMaximo(10);

        descuentoProductoModel.setValor(11);

        descuentoProductoModel.agregarProducto();

        verify(
                descuentoProductoDAO,
                never()
        ).crear(any(DescuentoProducto.class));

        assertEquals(
                "El descuento aplicado no puede superar 10 %",
                descuentoProductoModel.getUltimoMensaje()
        );
    }

    @Test
    void debeRechazarDescuentoIgualACero() {

        prepararDescuentoConMaximo(10);

        descuentoProductoModel.setValor(0);

        descuentoProductoModel.agregarProducto();

        verify(
                descuentoProductoDAO,
                never()
        ).crear(any(DescuentoProducto.class));

        assertEquals(
                "El descuento debe ser mayor que 0 %",
                descuentoProductoModel.getUltimoMensaje()
        );
    }

    @Test
    void debeRechazarFechaHastaAnteriorAFechaDesde() {

        prepararDescuentoConMaximo(10);

        descuentoProductoModel.setValor(5);

        descuentoProductoModel.setFechaDesde(
                new Date(2000)
        );

        descuentoProductoModel.setFechaHasta(
                new Date(1000)
        );

        descuentoProductoModel.agregarProducto();

        verify(
                descuentoProductoDAO,
                never()
        ).crear(any(DescuentoProducto.class));

        assertEquals(
                "La fecha hasta no puede ser anterior a la fecha desde",
                descuentoProductoModel.getUltimoMensaje()
        );
    }

    /*
     * Prepara un descuento válido para reutilizarlo
     * en las pruebas de porcentajes.
     */
    private Descuento prepararDescuentoConMaximo(
            int maximo) {

        UUID idDescuento = UUID.randomUUID();
        UUID idTipoDescuento = UUID.randomUUID();

        Descuento descuento = new Descuento();

        descuento.setIdDescuento(
                idDescuento
        );

        descuento.setIdTipoDescuento(
                idTipoDescuento
        );

        descuento.setFechaDesde(
                new Date(1000)
        );

        descuento.setFechaHasta(
                new Date(2000)
        );

        TipoDescuento tipoDescuento =
                new TipoDescuento();

        tipoDescuento.setIdTipoDescuento(
                idTipoDescuento
        );

        tipoDescuento.setDescuentoMaximo(
                maximo
        );

        when(
                descuentoProductoDAO
                        .findByIdDescuento(idDescuento)
        ).thenReturn(List.of());

        when(
                tipoDescuentoDAO
                        .findById(idTipoDescuento)
        ).thenReturn(tipoDescuento);

        descuentoProductoModel
                .cargarDescuento(descuento);

        return descuento;
    }
}
