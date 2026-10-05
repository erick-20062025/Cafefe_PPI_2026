package boundary;

import control.DescuentoDAO;
import control.TipoDescuentoDAO;
import entity.Descuento;
import entity.TipoDescuento;
import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
public class DescuentoModelTest {

    @Mock
    private DescuentoDAO descuentoDAO;

    @Mock
    private TipoDescuentoDAO tipoDescuentoDAO;

    @Mock
    private DescuentoProductoModel descuentoProductoModel;

    @InjectMocks
    private DescuentoModel descuentoModel;

    @Test
    void debeCargarDescuentosDesdeDAO() {

        Descuento estudiantes = new Descuento();
        estudiantes.setNombre("Descuento estudiantes");

        Descuento empleados = new Descuento();
        empleados.setNombre("Descuento empleados");

        List<Descuento> descuentos =
                List.of(estudiantes, empleados);

        when(descuentoDAO.findAll()).thenReturn(descuentos);

        descuentoModel.cargarDatos();

        assertEquals(2, descuentoModel.getModelo().size());

        assertEquals(
                "Descuento estudiantes",
                descuentoModel.getModelo().get(0).getNombre()
        );

        assertEquals(
                "Descuento empleados",
                descuentoModel.getModelo().get(1).getNombre()
        );

        verify(descuentoDAO).findAll();
    }

    @Test
    void initDebeCargarDescuentosYTiposIncluyendoInactivos() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        Descuento descuento = new Descuento(UUID.randomUUID());
        TipoDescuento activo = tipoDescuento(true);
        TipoDescuento inactivo = tipoDescuento(false);
        when(descuentoDAO.findAll()).thenReturn(List.of(descuento));
        when(tipoDescuentoDAO.findAll()).thenReturn(List.of(activo, inactivo));

        model.init();

        assertEquals(List.of(descuento), model.getModelo());
        assertEquals(List.of(activo, inactivo), model.getTiposDescuento());
    }

    @Test
    void nuevoDebeLimpiarTipoYProductosDelDescuentoAnterior() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        model.btnNuevoHandler(null);
        Descuento anterior = model.getRegistro();
        model.setIdTipoDescuentoSeleccionado(UUID.randomUUID().toString());
        model.btnModificarHandler(null);

        model.btnNuevoHandler(null);

        assertNotNull(model.getRegistro().getIdDescuento());
        assertNotEquals(anterior.getIdDescuento(), model.getRegistro().getIdDescuento());
        assertNull(model.getIdTipoDescuentoSeleccionado());
        assertTrue(model.isNuevo());
        assertFalse(model.isEditando());
        assertFalse(model.isPuedeGestionarProductos());
        verify(descuentoProductoModel, times(2)).cargarDescuento(null);
    }

    @Test
    void seleccionarDescuentoDebeSincronizarTipoYCargarProductos() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        Descuento descuento = new Descuento(UUID.randomUUID());
        UUID idTipo = UUID.randomUUID();
        descuento.setIdTipoDescuento(idTipo);
        model.setRegistro(descuento);
        model.btnModificarHandler(null);

        model.onRowSelect();

        assertEquals(idTipo.toString(), model.getIdTipoDescuentoSeleccionado());
        assertFalse(model.isEditando());
        assertFalse(model.isNuevo());
        assertTrue(model.isPuedeGestionarProductos());
        verify(descuentoProductoModel).cargarDescuento(descuento);
    }

    @Test
    void seleccionarSinTipoOSinRegistroDebeLimpiarTipoAnterior() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        model.setIdTipoDescuentoSeleccionado(UUID.randomUUID().toString());
        Descuento sinTipo = new Descuento();
        model.setRegistro(sinTipo);

        model.onRowSelect();

        assertNull(model.getIdTipoDescuentoSeleccionado());
        assertFalse(model.isPuedeGestionarProductos());
        verify(descuentoProductoModel).cargarDescuento(sinTipo);

        model.setRegistro(null);
        model.onRowSelect();

        assertNull(model.getIdTipoDescuentoSeleccionado());
        assertFalse(model.isPuedeGestionarProductos());
        verify(descuentoProductoModel).cargarDescuento(null);
    }

    @Test
    void deseleccionarDebeLimpiarRegistroTipoYProductos() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        model.btnNuevoHandler(null);
        model.setIdTipoDescuentoSeleccionado(UUID.randomUUID().toString());

        model.onRowUnselect();

        assertNull(model.getRegistro());
        assertNull(model.getIdTipoDescuentoSeleccionado());
        assertFalse(model.isNuevo());
        assertFalse(model.isPuedeGestionarProductos());
        verify(descuentoProductoModel, times(2)).cargarDescuento(null);
    }

    @Test
    void cancelarEdicionDebeLimpiarRegistroTipoYProductos() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        Descuento descuento = new Descuento(UUID.randomUUID());
        descuento.setIdTipoDescuento(UUID.randomUUID());
        model.setRegistro(descuento);
        model.onRowSelect();
        model.btnModificarHandler(null);

        model.btnCancelarHandler(null);

        assertNull(model.getRegistro());
        assertNull(model.getIdTipoDescuentoSeleccionado());
        assertFalse(model.isEditando());
        assertFalse(model.isPuedeGestionarProductos());
        verify(descuentoProductoModel).cargarDescuento(null);
        verifyNoInteractions(descuentoDAO);
    }

    @Test
    void guardarSinRegistroNoDebeConsultarTiposNiPersistir() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();

        model.btnGuardarHandler(null);

        assertNull(model.advertencia);
        verifyNoInteractions(tipoDescuentoDAO, descuentoDAO);
    }

    @Test
    void guardarSinTipoDebeAdvertirYConservarNuevoDescuento() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        model.btnNuevoHandler(null);
        Descuento descuento = model.getRegistro();

        model.btnGuardarHandler(null);

        assertEquals("Debe seleccionar un tipo de descuento", model.advertencia);
        assertSame(descuento, model.getRegistro());
        assertTrue(model.isNuevo());
        verifyNoInteractions(tipoDescuentoDAO, descuentoDAO);
    }

    @Test
    void guardarConTipoEliminadoDebeAdvertirSinPersistir() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        model.btnNuevoHandler(null);
        UUID idTipo = UUID.randomUUID();
        model.setIdTipoDescuentoSeleccionado(idTipo.toString());
        when(tipoDescuentoDAO.findById(idTipo)).thenReturn(null);

        model.btnGuardarHandler(null);

        assertEquals("El tipo de descuento seleccionado no existe", model.advertencia);
        assertTrue(model.isNuevo());
        verifyNoInteractions(descuentoDAO);
    }

    @Test
    void nuevoNoDebePermitirTipoInactivoOSinEstado() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        TipoDescuento tipo = prepararNuevoConTipo(model, false);

        for (Boolean activo : new Boolean[]{false, null}) {
            tipo.setActivo(activo);
            model.advertencia = null;
            model.btnGuardarHandler(null);
            assertEquals("No se puede asignar un tipo de descuento inactivo", model.advertencia);
            assertTrue(model.isNuevo());
        }

        verifyNoInteractions(descuentoDAO);
    }

    @Test
    void guardarSinFechaInicialDebeAdvertirSinPersistir() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        prepararNuevoConTipo(model, true);

        model.btnGuardarHandler(null);

        assertEquals("La fecha desde es obligatoria", model.advertencia);
        verifyNoInteractions(descuentoDAO);
    }

    @Test
    void nuevoNoDebeAceptarFechaInicialAnteriorAHoy() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        prepararNuevoConTipo(model, true);
        model.getRegistro().setFechaDesde(fechaRelativa(-1, 23));

        model.btnGuardarHandler(null);

        assertEquals("La fecha desde no puede ser anterior a hoy", model.advertencia);
        assertTrue(model.isNuevo());
        verifyNoInteractions(descuentoDAO);
    }

    @Test
    void fechasDelMismoDiaDebenIgnorarLaHoraYPermitirGuardar() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        prepararNuevoConTipo(model, true);
        Descuento descuento = model.getRegistro();
        descuento.setFechaDesde(fechaRelativa(0, 18));
        descuento.setFechaHasta(fechaRelativa(0, 8));
        when(descuentoDAO.findAll()).thenReturn(List.of(descuento));

        model.btnGuardarHandler(null);

        verify(descuentoDAO).crear(descuento);
        assertEquals(List.of(descuento), model.getModelo());
        assertNull(model.advertencia);
        assertNull(model.getRegistro());
        assertFalse(model.isNuevo());
        assertEquals("Registro guardado correctamente", model.exito);
    }

    @Test
    void nuevoConFechaFuturaYPeriodoAbiertoDebeGuardar() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        prepararNuevoConTipo(model, true);
        Descuento descuento = model.getRegistro();
        descuento.setFechaDesde(fechaRelativa(2, 10));

        model.btnGuardarHandler(null);

        verify(descuentoDAO).crear(descuento);
        assertNull(descuento.getFechaHasta());
        assertNull(model.advertencia);
        assertNull(model.getRegistro());
    }

    @Test
    void fechaFinalAnteriorALaInicialDebeImpedirGuardar() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        prepararNuevoConTipo(model, true);
        model.getRegistro().setFechaDesde(fechaRelativa(3, 8));
        model.getRegistro().setFechaHasta(fechaRelativa(2, 23));

        model.btnGuardarHandler(null);

        assertEquals("La fecha hasta no puede ser anterior a la fecha desde", model.advertencia);
        assertTrue(model.isNuevo());
        verifyNoInteractions(descuentoDAO);
    }

    @Test
    void editarHistoricoDebePermitirTipoInactivoYFechaInicialPasada() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        TipoDescuento tipo = tipoDescuento(false);
        Descuento descuento = new Descuento(UUID.randomUUID());
        descuento.setIdTipoDescuento(tipo.getIdTipoDescuento());
        descuento.setFechaDesde(fechaRelativa(-20, 10));
        descuento.setFechaHasta(fechaRelativa(-10, 10));
        model.setRegistro(descuento);
        model.btnModificarHandler(null);
        when(tipoDescuentoDAO.findById(tipo.getIdTipoDescuento())).thenReturn(tipo);

        model.btnGuardarHandler(null);

        verify(descuentoDAO).modificar(descuento);
        verify(descuentoDAO, never()).crear(any());
        assertNull(model.advertencia);
        assertNull(model.getRegistro());
        assertFalse(model.isEditando());
    }

    @Test
    void seleccionarTipoEnNuevoDebeConvertirUUIDYLimpiarValoresInvalidos() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        model.btnNuevoHandler(null);
        UUID idTipo = UUID.randomUUID();
        model.setIdTipoDescuentoSeleccionado(idTipo.toString());
        assertEquals(idTipo, model.getRegistro().getIdTipoDescuento());

        for (String seleccion : new String[]{null, "", "   ", "uuid-invalido"}) {
            model.setIdTipoDescuentoSeleccionado(idTipo.toString());
            model.setIdTipoDescuentoSeleccionado(seleccion);
            assertNull(model.getRegistro().getIdTipoDescuento());
        }

        verifyNoInteractions(tipoDescuentoDAO, descuentoDAO);
    }

    @Test
    void editarNoDebePermitirCambiarTipoDelDescuento() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        UUID idOriginal = UUID.randomUUID();
        Descuento descuento = new Descuento(UUID.randomUUID());
        descuento.setIdTipoDescuento(idOriginal);
        model.setRegistro(descuento);
        model.btnModificarHandler(null);

        model.setIdTipoDescuentoSeleccionado(UUID.randomUUID().toString());

        assertEquals(idOriginal, descuento.getIdTipoDescuento());
        assertEquals(idOriginal.toString(), model.getIdTipoDescuentoSeleccionado());
        assertTrue(model.isEditando());
        verifyNoInteractions(tipoDescuentoDAO, descuentoDAO);
    }

    @Test
    void resolverNombreDebeDistinguirTipoAusenteDisponibleYEliminado() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        UUID idDesconocido = UUID.randomUUID();
        assertEquals("Sin tipo", model.obtenerNombreTipoDescuento(idDesconocido));
        TipoDescuento tipo = tipoDescuento(true);
        tipo.setNombre("Estudiantes");
        when(descuentoDAO.findAll()).thenReturn(List.of());
        when(tipoDescuentoDAO.findAll()).thenReturn(List.of(tipo));
        model.init();

        assertEquals("Sin tipo", model.obtenerNombreTipoDescuento(null));
        assertEquals("Estudiantes", model.obtenerNombreTipoDescuento(tipo.getIdTipoDescuento()));
        assertEquals("Tipo no disponible", model.obtenerNombreTipoDescuento(idDesconocido));
    }

    @Test
    void etiquetaDebeIdentificarTiposInactivosOSinEstado() throws Exception {
        DescuentoModelPrueba model = crearModelPrueba();
        TipoDescuento tipo = tipoDescuento(true);
        tipo.setNombre("Promocional");

        assertEquals("", model.obtenerEtiquetaTipoDescuento(null));
        assertEquals("Promocional", model.obtenerEtiquetaTipoDescuento(tipo));
        tipo.setActivo(false);
        assertEquals("Promocional (INACTIVO)", model.obtenerEtiquetaTipoDescuento(tipo));
        tipo.setActivo(null);
        assertEquals("Promocional (INACTIVO)", model.obtenerEtiquetaTipoDescuento(tipo));
    }

    private DescuentoModelPrueba crearModelPrueba() throws Exception {
        DescuentoModelPrueba model = new DescuentoModelPrueba();
        inyectarCampo(model, "descuentoDAO", descuentoDAO);
        inyectarCampo(model, "tipoDescuentoDAO", tipoDescuentoDAO);
        inyectarCampo(model, "descuentoProductoModel", descuentoProductoModel);
        return model;
    }

    private void inyectarCampo(Object model, String nombre, Object valor) throws Exception {
        Field campo = DescuentoModel.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(model, valor);
    }

    private TipoDescuento tipoDescuento(Boolean activo) {
        TipoDescuento tipo = new TipoDescuento(UUID.randomUUID());
        tipo.setActivo(activo);
        return tipo;
    }

    private TipoDescuento prepararNuevoConTipo(DescuentoModelPrueba model, Boolean activo) {
        model.btnNuevoHandler(null);
        TipoDescuento tipo = tipoDescuento(activo);
        model.setIdTipoDescuentoSeleccionado(tipo.getIdTipoDescuento().toString());
        when(tipoDescuentoDAO.findById(tipo.getIdTipoDescuento())).thenReturn(tipo);
        return tipo;
    }

    private Date fechaRelativa(int dias, int hora) {
        Calendar calendario = Calendar.getInstance();
        calendario.add(Calendar.DAY_OF_MONTH, dias);
        calendario.set(Calendar.HOUR_OF_DAY, hora);
        calendario.set(Calendar.MINUTE, 0);
        calendario.set(Calendar.SECOND, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        return calendario.getTime();
    }

    private static class DescuentoModelPrueba extends DescuentoModel {
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
