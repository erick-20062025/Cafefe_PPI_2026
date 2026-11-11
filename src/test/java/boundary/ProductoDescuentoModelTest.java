package boundary;

import control.DescuentoDAO;
import control.DescuentoProductoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.DescuentoProducto;
import entity.Producto;
import entity.TipoDescuento;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de la asignación de descuentos desde Producto.xhtml.
 * Cubre casos correctos, validaciones, fallas simuladas y mensajes de la UI.
 */
@ExtendWith(MockitoExtension.class)
class ProductoDescuentoModelTest {

    @Mock private DescuentoProductoDAO relacionDAO;
    @Mock private DescuentoDAO descuentoDAO;
    @Mock private TipoDescuentoDAO tipoDAO;

    private ModeloPrueba model;

    /** Permite probar mensajes sin iniciar Jakarta Faces ni Open Liberty. */
    private static class ModeloPrueba extends ProductoDescuentoModel {
        String exito;
        String advertencia;
        String error;

        @Override protected void mostrarExito(String mensaje) { exito = mensaje; }
        @Override protected void mostrarAdvertencia(String mensaje) { advertencia = mensaje; }
        @Override protected void mostrarError(String mensaje) { error = mensaje; }
    }

    @BeforeEach
    void preparar() throws Exception {
        model = new ModeloPrueba();
        inyectar("descuentoProductoDAO", relacionDAO);
        inyectar("descuentoDAO", descuentoDAO);
        inyectar("tipoDescuentoDAO", tipoDAO);
    }

    @Test
    void inicializacionCargaCatalogo() {
        List<Descuento> datos = List.of(descuento());
        when(descuentoDAO.findAll()).thenReturn(datos);
        model.init();
        assertSame(datos, model.getDescuentoList());
        verify(descuentoDAO).findAll();
    }

    @Test
    void catalogoNuloProduceListaVacia() {
        model.cargarDescuentos();
        assertNotNull(model.getDescuentoList());
        assertTrue(model.getDescuentoList().isEmpty());
    }

    @Test
    void seleccionDeProductoCargaRelaciones() {
        Producto producto = producto(true);
        List<DescuentoProducto> datos = List.of(relacion(producto));
        when(relacionDAO.findByIdProducto(producto.getIdProducto())).thenReturn(datos);
        model.cargarProducto(producto);
        assertSame(producto, model.getProducto());
        assertSame(datos, model.getModelo());
        verify(relacionDAO).findByIdProducto(producto.getIdProducto());
    }

    @Test
    void relacionesNulasSeConviertenEnListaVacia() {
        model.cargarProducto(producto(true));
        assertNotNull(model.getModelo());
        assertTrue(model.getModelo().isEmpty());
    }

    @Test
    void productoNuloLimpiaFormularioYSinConsultarDAO() {
        model.setIdDescuentoSeleccionado(UUID.randomUUID().toString());
        model.setValor(15);
        model.setObservaciones("Temporal");
        model.cargarProducto(null);
        assertNull(model.getProducto());
        assertTrue(model.getModelo().isEmpty());
        assertNull(model.getIdDescuentoSeleccionado());
        assertNull(model.getValor());
        assertNull(model.getObservaciones());
        verifyNoInteractions(relacionDAO);
    }

    @Test
    void productoSinIdNoConsultaRelaciones() {
        model.cargarProducto(new Producto());
        assertTrue(model.getModelo().isEmpty());
        verifyNoInteractions(relacionDAO);
    }

    @Test
    void seleccionDeDescuentoCargaFechasYMaximo() {
        Descuento descuento = descuento();
        when(descuentoDAO.findById(descuento.getIdDescuento())).thenReturn(descuento);
        when(tipoDAO.findById(descuento.getIdTipoDescuento())).thenReturn(tipo(35));
        model.setIdDescuentoSeleccionado(descuento.getIdDescuento().toString());
        model.cambiarDescuento();
        assertEquals(35, model.getDescuentoMaximo());
        assertEquals(descuento.getFechaDesde(), model.getFechaDesde());
        assertEquals(descuento.getFechaHasta(), model.getFechaHasta());
    }

    @Test
    void descuentoInvalidoMuestraAdvertenciaAlCambiarSeleccion() {
        model.setIdDescuentoSeleccionado("UUID-invalido");
        model.cambiarDescuento();
        assertEquals("El descuento seleccionado no existe", model.advertencia);
        assertNull(model.getDescuentoMaximo());
        verifyNoInteractions(descuentoDAO, tipoDAO);
    }

    @Test
    void errorAlConsultarDescuentoSeRegistraYNotifica() {
        UUID id = UUID.randomUUID();
        IllegalStateException fallo = new IllegalStateException("Conexión caída");
        when(descuentoDAO.findById(id)).thenThrow(fallo);
        model.setIdDescuentoSeleccionado(id.toString());
        List<LogRecord> registros = registrarLogDurante(() -> model.cambiarDescuento());
        assertEquals("No se pudo consultar el descuento", model.error);
        assertTrue(registros.stream().anyMatch(r -> r.getLevel() == Level.SEVERE
                && r.getThrown() == fallo));
    }

    @Test
    void errorAlConsultarTipoSeRegistraYNotifica() {
        Descuento descuento = descuento();
        when(descuentoDAO.findById(descuento.getIdDescuento())).thenReturn(descuento);
        IllegalStateException fallo = new IllegalStateException("Consulta fallida");
        when(tipoDAO.findById(descuento.getIdTipoDescuento())).thenThrow(fallo);
        model.setIdDescuentoSeleccionado(descuento.getIdDescuento().toString());
        List<LogRecord> registros = registrarLogDurante(() -> model.cambiarDescuento());
        assertEquals("No se pudo consultar el límite del descuento", model.error);
        assertNull(model.getDescuentoMaximo());
        assertTrue(registros.stream().anyMatch(r -> r.getLevel() == Level.SEVERE
                && r.getThrown() == fallo));
    }

    @Test
    void rechazaAsociacionSinProductoGuardado() {
        model.agregarDescuento();
        assertEquals("Debe seleccionar un producto guardado", model.advertencia);
        verifyNoInteractions(relacionDAO, descuentoDAO, tipoDAO);
    }

    @Test
    void rechazaProductoInactivo() {
        model.cargarProducto(producto(false));
        model.agregarDescuento();
        assertEquals("No se pueden asociar descuentos a un producto inactivo", model.advertencia);
        verify(relacionDAO, never()).crear(any());
    }

    @Test
    void rechazaAsociacionSinSeleccionarDescuento() {
        model.cargarProducto(producto(true));
        model.agregarDescuento();
        assertEquals("Debe seleccionar un descuento", model.advertencia);
        verifyNoInteractions(descuentoDAO);
        verify(relacionDAO, never()).crear(any());
    }

    @Test
    void rechazaDescuentoConIdentificadorMalformado() {
        model.cargarProducto(producto(true));
        model.setIdDescuentoSeleccionado("no-es-uuid");
        model.agregarDescuento();
        assertEquals("El descuento seleccionado no existe", model.advertencia);
        verifyNoInteractions(descuentoDAO, tipoDAO);
        verify(relacionDAO, never()).crear(any());
    }

    @Test
    void rechazaDescuentoQueYaNoExiste() {
        model.cargarProducto(producto(true));
        model.setIdDescuentoSeleccionado(UUID.randomUUID().toString());
        model.agregarDescuento();
        assertEquals("El descuento seleccionado no existe", model.advertencia);
        verify(relacionDAO, never()).crear(any());
    }

    @Test
    void rechazaCuandoNoSePudoConocerElLimite() {
        model.cargarProducto(producto(true));
        Descuento descuento = descuento();
        when(descuentoDAO.findById(descuento.getIdDescuento())).thenReturn(descuento);
        model.setIdDescuentoSeleccionado(descuento.getIdDescuento().toString());
        model.agregarDescuento();
        assertEquals("No se pudo determinar el límite del tipo de descuento", model.advertencia);
        verify(relacionDAO, never()).crear(any());
    }

    @Test
    void rechazaPorcentajeCero() {
        model.cargarProducto(producto(true));
        prepararDescuento(30);
        model.setValor(0);
        model.agregarDescuento();
        assertEquals("El descuento debe ser mayor que 0 %", model.advertencia);
        verify(relacionDAO, never()).crear(any());
    }

    @Test
    void rechazaPorcentajeSuperiorAlMaximo() {
        model.cargarProducto(producto(true));
        prepararDescuento(20);
        model.setValor(21);
        model.agregarDescuento();
        assertEquals("El descuento aplicado no puede superar 20 %", model.advertencia);
        verify(relacionDAO, never()).crear(any());
    }

    @Test
    void rechazaFechaDesdeAusente() {
        model.cargarProducto(producto(true));
        prepararDescuento(30);
        model.setValor(10);
        model.setFechaDesde(null);
        model.agregarDescuento();
        assertEquals("Debe indicar la fecha desde", model.advertencia);
        verify(relacionDAO, never()).crear(any());
    }

    @Test
    void rechazaFechaAnteriorAlInicioDelDescuento() {
        model.cargarProducto(producto(true));
        prepararDescuento(30);
        model.setValor(10);
        model.setFechaDesde(dia(-1));
        model.agregarDescuento();
        assertEquals("La fecha desde no puede ser anterior a la fecha inicial del descuento", model.advertencia);
        verify(relacionDAO, never()).crear(any());
    }

    @Test
    void rechazaFechaHastaAntesDeFechaDesde() {
        model.cargarProducto(producto(true));
        prepararDescuento(30);
        model.setValor(10);
        model.setFechaHasta(dia(-1));
        model.agregarDescuento();
        assertEquals("La fecha hasta no puede ser anterior a la fecha desde", model.advertencia);
        verify(relacionDAO, never()).crear(any());
    }

    @Test
    void rechazaFechaHastaFueraDeLaPromocion() {
        model.cargarProducto(producto(true));
        prepararDescuento(30);
        model.setValor(10);
        model.setFechaHasta(dia(20));
        model.agregarDescuento();
        assertEquals("Las fechas están fuera de la vigencia del descuento", model.advertencia);
        verify(relacionDAO, never()).crear(any());
    }

    @Test
    void rechazaPromocionCerradaSinFechaFinal() {
        model.cargarProducto(producto(true));
        prepararDescuento(30);
        model.setValor(10);
        model.setFechaHasta(null);
        model.agregarDescuento();
        assertEquals("Debe indicar la fecha hasta para este descuento", model.advertencia);
        verify(relacionDAO, never()).crear(any());
    }

    @Test
    void asociacionCorrectaGuardaYRecarga() {
        Producto producto = producto(true);
        model.cargarProducto(producto);
        Descuento descuento = prepararDescuento(35);
        model.setValor(10);
        model.setObservaciones("Promoción válida");
        List<DescuentoProducto> recargadas = List.of(relacion(producto));
        when(relacionDAO.findByIdProducto(producto.getIdProducto())).thenReturn(recargadas);

        model.agregarDescuento();

        ArgumentCaptor<DescuentoProducto> captor = ArgumentCaptor.forClass(DescuentoProducto.class);
        verify(relacionDAO).crear(captor.capture());
        DescuentoProducto guardada = captor.getValue();
        assertNotNull(guardada.getIdDescuentoProducto());
        assertSame(producto, guardada.getIdProducto());
        assertSame(descuento, guardada.getIdDescuento());
        assertEquals(10, guardada.getValor());
        assertEquals("Promoción válida", guardada.getObservaciones());
        assertSame(recargadas, model.getModelo());
        assertNull(model.getIdDescuentoSeleccionado());
        assertEquals("Descuento asociado correctamente", model.exito);
    }

    @Test
    void asociacionDuplicadaConservaSeleccionYAdvierte() {
        Producto producto = producto(true);
        model.cargarProducto(producto);
        Descuento descuento = prepararDescuento(30);
        model.setValor(10);
        doThrow(new IllegalArgumentException("Este descuento ya está asociado al producto"))
                .when(relacionDAO).crear(any(DescuentoProducto.class));

        model.agregarDescuento();

        assertEquals("Este descuento ya está asociado al producto", model.advertencia);
        assertEquals(descuento.getIdDescuento().toString(), model.getIdDescuentoSeleccionado());
        verify(relacionDAO, never()).findByIdDescuento(any());
    }

    @Test
    void falloInesperadoAlGuardarRegistraErrorYConservaFormulario() {
        Producto producto = producto(true);
        model.cargarProducto(producto);
        Descuento descuento = prepararDescuento(30);
        model.setValor(10);
        IllegalStateException fallo = new IllegalStateException("BD desconectada");
        doThrow(fallo).when(relacionDAO).crear(any(DescuentoProducto.class));

        List<LogRecord> registros = registrarLogDurante(() -> model.agregarDescuento());

        assertEquals("No se pudo asociar el descuento", model.error);
        assertEquals(10, model.getValor());
        assertEquals(descuento.getIdDescuento().toString(), model.getIdDescuentoSeleccionado());
        assertTrue(registros.stream().anyMatch(r -> r.getLevel() == Level.SEVERE
                && r.getThrown() == fallo));
    }

    @Test
    void siFallaRecargaLuegoDeGuardarSeNotificaSinReintentarGuardado() {
        Producto producto = producto(true);
        model.cargarProducto(producto);
        prepararDescuento(30);
        model.setValor(10);
        when(relacionDAO.findByIdProducto(producto.getIdProducto()))
                .thenThrow(new IllegalStateException("Error al recargar"));

        model.agregarDescuento();

        verify(relacionDAO, times(1)).crear(any(DescuentoProducto.class));
        assertEquals("El descuento se asoció, pero no pudo actualizarse la lista", model.advertencia);
    }

    @Test
    void rechazaQuitarRelacionAjena() {
        model.cargarProducto(producto(true));
        model.quitarDescuento(relacion(producto(true)));
        assertEquals("La asociación no pertenece al producto seleccionado", model.advertencia);
        verify(relacionDAO, never()).eliminar(any());
    }

    @Test
    void rechazaQuitarRelacionNula() {
        model.cargarProducto(producto(true));
        model.quitarDescuento(null);
        assertEquals("La asociación no pertenece al producto seleccionado", model.advertencia);
        verify(relacionDAO, never()).eliminar(any());
    }

    @Test
    void quitarRelacionPropiaBorraYActualizaTabla() {
        Producto producto = producto(true);
        model.cargarProducto(producto);
        DescuentoProducto relacion = relacion(producto);
        when(relacionDAO.findByIdProducto(producto.getIdProducto())).thenReturn(List.of());

        model.quitarDescuento(relacion);

        verify(relacionDAO).eliminar(same(relacion));
        assertTrue(model.getModelo().isEmpty());
        assertEquals("Descuento quitado correctamente", model.exito);
    }

    @Test
    void rechazoDeEliminacionMuestraAdvertencia() {
        Producto producto = producto(true);
        model.cargarProducto(producto);
        DescuentoProducto relacion = relacion(producto);
        doThrow(new IllegalArgumentException("No puede quitarse esta asociación"))
                .when(relacionDAO).eliminar(same(relacion));

        model.quitarDescuento(relacion);

        assertEquals("No puede quitarse esta asociación", model.advertencia);
        assertSame(producto, model.getProducto());
    }

    @Test
    void falloInesperadoAlEliminarRegistraError() {
        Producto producto = producto(true);
        model.cargarProducto(producto);
        DescuentoProducto relacion = relacion(producto);
        IllegalStateException fallo = new IllegalStateException("Fallo simulado");
        doThrow(fallo).when(relacionDAO).eliminar(same(relacion));

        List<LogRecord> registros = registrarLogDurante(() -> model.quitarDescuento(relacion));

        assertEquals("No se pudo quitar el descuento", model.error);
        assertSame(producto, model.getProducto());
        assertTrue(registros.stream().anyMatch(r -> r.getLevel() == Level.SEVERE
                && r.getThrown() == fallo));
    }

    @Test
    void siFallaRecargaLuegoDeEliminarSeInformaSinEliminarDosVeces() {
        Producto producto = producto(true);
        model.cargarProducto(producto);
        DescuentoProducto relacion = relacion(producto);
        when(relacionDAO.findByIdProducto(producto.getIdProducto()))
                .thenThrow(new IllegalStateException("Error de recarga"));

        model.quitarDescuento(relacion);

        verify(relacionDAO, times(1)).eliminar(same(relacion));
        assertEquals("Se quitó el descuento, pero no pudo actualizarse la lista", model.advertencia);
    }

    private Descuento prepararDescuento(int maximo) {
        Descuento descuento = descuento();
        when(descuentoDAO.findById(descuento.getIdDescuento())).thenReturn(descuento);
        when(tipoDAO.findById(descuento.getIdTipoDescuento())).thenReturn(tipo(maximo));
        model.setIdDescuentoSeleccionado(descuento.getIdDescuento().toString());
        model.cambiarDescuento();
        return descuento;
    }

    private List<LogRecord> registrarLogDurante(Runnable accion) {
        Logger logger = Logger.getLogger(ProductoDescuentoModel.class.getName());
        List<LogRecord> registros = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord registro) { registros.add(registro); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try {
            accion.run();
        } finally {
            logger.removeHandler(handler);
            handler.close();
        }
        return registros;
    }

    private Producto producto(boolean activo) {
        Producto producto = new Producto(UUID.randomUUID());
        producto.setActivo(activo);
        return producto;
    }

    private Descuento descuento() {
        Descuento descuento = new Descuento(UUID.randomUUID());
        descuento.setIdTipoDescuento(UUID.randomUUID());
        descuento.setFechaDesde(dia(0));
        descuento.setFechaHasta(dia(10));
        return descuento;
    }

    private TipoDescuento tipo(int maximo) {
        TipoDescuento tipo = new TipoDescuento();
        tipo.setDescuentoMaximo(maximo);
        return tipo;
    }

    private DescuentoProducto relacion(Producto producto) {
        DescuentoProducto relacion = new DescuentoProducto(UUID.randomUUID());
        relacion.setIdProducto(producto);
        return relacion;
    }

    private static Date dia(int dias) {
        Calendar calendario = Calendar.getInstance();
        calendario.set(Calendar.HOUR_OF_DAY, 12);
        calendario.set(Calendar.MINUTE, 0);
        calendario.set(Calendar.SECOND, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        calendario.add(Calendar.DAY_OF_MONTH, dias);
        return calendario.getTime();
    }

    private void inyectar(String campo, Object valor) throws Exception {
        Field atributo = ProductoDescuentoModel.class.getDeclaredField(campo);
        atributo.setAccessible(true);
        atributo.set(model, valor);
    }
}
