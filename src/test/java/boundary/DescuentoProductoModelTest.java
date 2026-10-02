package boundary;

import control.DescuentoProductoDAO;
import control.ProductoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.DescuentoProducto;
import entity.Producto;
import entity.TipoDescuento;
import java.util.Calendar;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

        @Override
        protected void mostrarError(String mensaje) {
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

        Date fechaDesde = fecha(2026, 10, 1);
        Date fechaHasta = fecha(2026, 10, 15);

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
    void noDebePermitirRepetirProductoEnUnDescuento() {

        assertFalse(
                descuentoProductoModel
                        .permiteRelacionDuplicada()
        );
    }

    @Test
    void debeRechazarProductoYaAsociadoAlMismoDescuento() {

        UUID idDescuento = UUID.randomUUID();
        UUID idTipoDescuento = UUID.randomUUID();
        UUID idProducto = UUID.randomUUID();

        Descuento descuento = new Descuento();
        descuento.setIdDescuento(idDescuento);
        descuento.setIdTipoDescuento(idTipoDescuento);
        descuento.setFechaDesde(
                fecha(2026, 10, 1)
        );
        descuento.setFechaHasta(
                fecha(2026, 10, 15)
        );

        TipoDescuento tipoDescuento
                = new TipoDescuento();

        tipoDescuento.setIdTipoDescuento(
                idTipoDescuento
        );

        tipoDescuento.setDescuentoMaximo(10);

        Producto producto = new Producto();
        producto.setIdProducto(idProducto);
        producto.setNombre("Café");
        producto.setActivo(true);

        DescuentoProducto existente
                = new DescuentoProducto();

        existente.setIdDescuentoProducto(
                UUID.randomUUID()
        );

        existente.setIdDescuento(descuento);
        existente.setIdProducto(producto);
        existente.setValor(5);

        when(
                descuentoProductoDAO
                        .findByIdDescuento(idDescuento)
        ).thenReturn(
                List.of(existente)
        );

        when(
                tipoDescuentoDAO
                        .findById(idTipoDescuento)
        ).thenReturn(tipoDescuento);

        when(
                productoDAO.findById(idProducto)
        ).thenReturn(producto);

        descuentoProductoModel
                .cargarDescuento(descuento);

        descuentoProductoModel
                .setIdProductoSeleccionado(
                        idProducto.toString()
                );

        descuentoProductoModel.setValor(5);

        descuentoProductoModel.agregarProducto();

        verify(
                descuentoProductoDAO,
                never()
        ).crear(any(DescuentoProducto.class));

        assertEquals(
                "La relación ya existe",
                descuentoProductoModel.getUltimoMensaje()
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

        Date fechaDesde
                = fecha(2026, 10, 1);

        Date fechaHasta
                = fecha(2026, 10, 15);

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

        DescuentoProducto relacion
                = descuentoProductoModel.construirRelacion(
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

        prepararDescuentoConMaximo(10);

        Producto producto
                = prepararProductoSeleccionado(
                        "Horchata"
                );

        descuentoProductoModel.setValor(5);

        descuentoProductoModel.agregarProducto();

        ArgumentCaptor<DescuentoProducto> captor
                = ArgumentCaptor.forClass(
                        DescuentoProducto.class
                );

        verify(
                descuentoProductoDAO
        ).crear(captor.capture());

        DescuentoProducto relacion
                = captor.getValue();

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

        prepararProductoSeleccionado(
                "Café americano"
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

        prepararProductoSeleccionado(
                "Capuchino"
        );

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

        prepararProductoSeleccionado(
                "Latte"
        );

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
    void debeRechazarProductoInactivo() {

        prepararDescuentoConMaximo(20);

        UUID idProducto = UUID.randomUUID();

        Producto producto = new Producto();
        producto.setIdProducto(idProducto);
        producto.setNombre("Producto inactivo");
        producto.setActivo(false);

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
                descuentoProductoDAO,
                never()
        ).crear(any(DescuentoProducto.class));

        assertEquals(
                "El producto seleccionado está inactivo",
                descuentoProductoModel.getUltimoMensaje()
        );
    }

    @Test
    void debeRechazarFechaHastaAnteriorAFechaDesde() {

        prepararDescuentoConMaximo(10);

        prepararProductoSeleccionado(
                "Café"
        );

        descuentoProductoModel.setValor(5);

        descuentoProductoModel.setFechaDesde(
                fecha(2026, 10, 10)
        );

        descuentoProductoModel.setFechaHasta(
                fecha(2026, 10, 9)
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

    @Test
    void debeRechazarFechaDesdeAnteriorAlDescuento() {

        prepararDescuentoConMaximo(10);

        prepararProductoSeleccionado(
                "Café"
        );

        descuentoProductoModel.setValor(5);

        descuentoProductoModel.setFechaDesde(
                fecha(2026, 9, 30)
        );

        descuentoProductoModel.setFechaHasta(
                fecha(2026, 10, 10)
        );

        descuentoProductoModel.agregarProducto();

        verify(
                descuentoProductoDAO,
                never()
        ).crear(any(DescuentoProducto.class));

        assertEquals(
                "La fecha desde no puede ser anterior "
                + "a la fecha inicial del descuento",
                descuentoProductoModel.getUltimoMensaje()
        );
    }

    @Test
    void debeRechazarFechaHastaPosteriorAlDescuento() {

        prepararDescuentoConMaximo(10);

        prepararProductoSeleccionado(
                "Café"
        );

        descuentoProductoModel.setValor(5);

        descuentoProductoModel.setFechaDesde(
                fecha(2026, 10, 5)
        );

        descuentoProductoModel.setFechaHasta(
                fecha(2026, 10, 20)
        );

        descuentoProductoModel.agregarProducto();

        verify(
                descuentoProductoDAO,
                never()
        ).crear(any(DescuentoProducto.class));

        assertEquals(
                "La fecha hasta no puede superar "
                + "la fecha final del descuento",
                descuentoProductoModel.getUltimoMensaje()
        );
    }

    @Test
    void debePermitirRangoAbiertoCuandoDescuentoEsAbierto() {

        prepararDescuentoAbiertoConMaximo(20);

        prepararProductoSeleccionado(
                "Café especial"
        );

        descuentoProductoModel.setValor(15);

        descuentoProductoModel.setFechaHasta(null);

        descuentoProductoModel.agregarProducto();

        verify(
                descuentoProductoDAO
        ).crear(any(DescuentoProducto.class));
    }

    @Test
    void descuentoCerradoDebeExigirFechaHasta() {

        prepararDescuentoConMaximo(10);

        prepararProductoSeleccionado(
                "Café cerrado"
        );

        descuentoProductoModel.setValor(5);
        descuentoProductoModel.setFechaHasta(null);

        descuentoProductoModel.agregarProducto();

        verify(
                descuentoProductoDAO,
                never()
        ).crear(any(DescuentoProducto.class));

        assertEquals(
                "Este descuento tiene fecha de finalización; "
                + "debe indicar una fecha hasta",
                descuentoProductoModel.getUltimoMensaje()
        );
    }

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
                fecha(2026, 10, 1)
        );

        descuento.setFechaHasta(
                fecha(2026, 10, 15)
        );

        TipoDescuento tipoDescuento
                = new TipoDescuento();

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

    private Descuento prepararDescuentoAbiertoConMaximo(
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
                fecha(2026, 10, 1)
        );

        descuento.setFechaHasta(null);

        TipoDescuento tipoDescuento
                = new TipoDescuento();

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

    private Producto prepararProductoSeleccionado(
            String nombre) {

        UUID idProducto = UUID.randomUUID();

        Producto producto = new Producto();

        producto.setIdProducto(
                idProducto
        );

        producto.setNombre(
                nombre
        );

        producto.setActivo(true);

        when(
                productoDAO.findById(idProducto)
        ).thenReturn(producto);

        descuentoProductoModel
                .setIdProductoSeleccionado(
                        idProducto.toString()
                );

        return producto;
    }

    private Date fecha(
            int anio,
            int mes,
            int dia) {

        Calendar calendario
                = Calendar.getInstance();

        calendario.clear();

        calendario.set(
                anio,
                mes - 1,
                dia
        );

        return calendario.getTime();
    }
}