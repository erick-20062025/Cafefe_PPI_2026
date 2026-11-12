package control;

import entity.Descuento;
import entity.DescuentoProducto;
import entity.Producto;
import entity.TipoDescuento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de DescuentoProductoDAO desde el flujo de Producto. No requieren
 * PostgreSQL: EntityManager y las consultas se simulan con Mockito.
 */
@ExtendWith(MockitoExtension.class)
class DescuentoProductoDesdeProductoDAOTest {

    @Mock
    private EntityManager em;
    @InjectMocks
    private DescuentoProductoDAO dao;

    @Test
    void buscarPorProductoUsaNamedQueryYDevuelveResultado() {
        UUID idProducto = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        TypedQuery<DescuentoProducto> query = mock(TypedQuery.class);
        List<DescuentoProducto> esperado = List.of(new DescuentoProducto());
        when(em.createNamedQuery("DescuentoProducto.findByIdProducto", DescuentoProducto.class))
                .thenReturn(query);
        when(query.setParameter("idProducto", idProducto)).thenReturn(query);
        when(query.getResultList()).thenReturn(esperado);

        assertSame(esperado, dao.findByIdProducto(idProducto));
        verify(query).setParameter("idProducto", idProducto);
    }

    @Test
    void buscarPorDescuentoSigueFuncionando() {
        UUID idDescuento = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        TypedQuery<DescuentoProducto> query = mock(TypedQuery.class);
        when(em.createNamedQuery("DescuentoProducto.findByIdDescuento", DescuentoProducto.class))
                .thenReturn(query);
        when(query.setParameter("idDescuento", idDescuento)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertTrue(dao.findByIdDescuento(idDescuento).isEmpty());
        verify(query).setParameter("idDescuento", idDescuento);
    }

    @Test
    void consultasConIdentificadoresNulosNoAccedenALaBase() {
        assertThrows(IllegalArgumentException.class, () -> dao.findByIdProducto(null));
        assertThrows(IllegalArgumentException.class, () -> dao.findByIdDescuento(null));
        assertThrows(IllegalArgumentException.class,
                () -> dao.existeRelacion(null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> dao.existeRelacion(UUID.randomUUID(), null));
        verifyNoInteractions(em);
    }

    @Test
    void contarAsociacionesUsaNamedQueryYDetectaDuplicado() {
        UUID idProducto = UUID.randomUUID();
        UUID idDescuento = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createNamedQuery("DescuentoProducto.countByProductoAndDescuento", Long.class))
                .thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L, 1L);

        assertFalse(dao.existeRelacion(idProducto, idDescuento));
        assertTrue(dao.existeRelacion(idProducto, idDescuento));
        verify(query, times(2)).setParameter("idProducto", idProducto);
        verify(query, times(2)).setParameter("idDescuento", idDescuento);
    }

    @Test
    void conteoNuloNoSeConsideraDuplicado() {
        UUID idProducto = UUID.randomUUID();
        UUID idDescuento = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createNamedQuery("DescuentoProducto.countByProductoAndDescuento", Long.class))
                .thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);

        assertFalse(dao.existeRelacion(idProducto, idDescuento));
    }

    @Test
    void crearRelacionValidaGuardaYHaceFlush() {
        DescuentoProducto relacion = relacionValida();
        prepararEntidades(relacion, true, true, 35, null);
        prepararConteo(relacion, 0L);

        dao.crear(relacion);

        verify(em).persist(same(relacion));
        verify(em).flush();
        verify(em, never()).remove(any());
    }

    @Test
    void crearNuloEsRechazadoSinConsultarBD() {
        assertThrows(IllegalArgumentException.class, () -> dao.crear(null));
        verifyNoInteractions(em);
    }

    @Test
    void rechazaRelacionSinIdentificador() {
        DescuentoProducto relacion = relacionValida();
        relacion.setIdDescuentoProducto(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verifyNoInteractions(em);
    }

    @Test
    void rechazaRelacionSinProducto() {
        DescuentoProducto relacion = relacionValida();
        relacion.setIdProducto(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verifyNoInteractions(em);
    }

    @Test
    void rechazaRelacionSinDescuento() {
        DescuentoProducto relacion = relacionValida();
        relacion.setIdDescuento(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verifyNoInteractions(em);
    }

    @Test
    void rechazaPorcentajeNulo() {
        DescuentoProducto relacion = relacionValida();
        relacion.setValor(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verifyNoInteractions(em);
    }

    @Test
    void rechazaPorcentajeNegativoOCero() {
        DescuentoProducto relacion = relacionValida();
        relacion.setValor(0);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        relacion.setValor(-10);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verifyNoInteractions(em);
    }

    @Test
    void rechazaPorcentajeSuperiorACienSinConsultarBD() {
        DescuentoProducto relacion = relacionValida();
        relacion.setValor(101);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> dao.crear(relacion)
        );

        assertEquals(
                "El descuento no puede superar 100 %",
                error.getMessage()
        );

        verifyNoInteractions(em);
    }

    @Test
    void rechazaFechaDesdeNula() {
        DescuentoProducto relacion = relacionValida();
        relacion.setFechaDesde(null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verifyNoInteractions(em);
    }

    @Test
    void rechazaProductoInexistenteSinGuardar() {
        DescuentoProducto relacion = relacionValida();
        // Un EntityManager simulado devuelve null cuando no existe el registro.
        IllegalArgumentException fallo = assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));
        assertEquals("El producto seleccionado no existe", fallo.getMessage());
        verify(em).find(Producto.class, relacion.getIdProducto().getIdProducto());
        verify(em, never()).persist(any());
        verify(em, never()).flush();
    }

    @Test
    void rechazaDescuentoInexistenteSinGuardar() {
        DescuentoProducto relacion = relacionValida();
        prepararEntidades(relacion, true, false, null, null);
        IllegalArgumentException fallo = assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));
        assertEquals("El descuento seleccionado no existe", fallo.getMessage());
        verify(em, never()).persist(any());
    }

    @Test
    void rechazaProductoInactivoSinGuardar() {
        DescuentoProducto relacion = relacionValida();
        relacion.getIdProducto().setActivo(false);
        prepararEntidades(relacion, true, true, null, null);
        IllegalArgumentException fallo = assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));
        assertEquals("No se pueden asociar descuentos a un producto inactivo", fallo.getMessage());
        verify(em, never()).persist(any());
    }

    @Test
    void rechazaFechaAnteriorALaVigencia() {
        DescuentoProducto relacion = relacionValida();
        relacion.setFechaDesde(dia(-2));
        prepararEntidades(relacion, true, true, null, null);
        assertThrows(IllegalArgumentException.class, () -> dao.crear(relacion));
        verify(em, never()).persist(any());
    }

    @Test
    void rechazaFechaFinalAnteriorALaInicial() {
        DescuentoProducto relacion = relacionValida();
        relacion.setFechaHasta(dia(-1));
        prepararEntidades(relacion, true, true, null, null);
        IllegalArgumentException fallo = assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));
        assertEquals("La fecha hasta no puede ser anterior a la fecha desde", fallo.getMessage());
        verify(em, never()).persist(any());
    }

    @Test
    void rechazaPromocionCerradaSinFechaFinal() {
        DescuentoProducto relacion = relacionValida();
        relacion.setFechaHasta(null);
        prepararEntidades(relacion, true, true, null, null);
        IllegalArgumentException fallo = assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));
        assertEquals("Debe indicar la fecha hasta para este descuento", fallo.getMessage());
        verify(em, never()).persist(any());
    }

    @Test
    void rechazaFechaFinalPosteriorALimiteDelDescuento() {
        DescuentoProducto relacion = relacionValida();
        relacion.setFechaHasta(dia(20));
        prepararEntidades(relacion, true, true, null, null);
        IllegalArgumentException fallo = assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));
        assertEquals("Las fechas están fuera de la vigencia del descuento", fallo.getMessage());
        verify(em, never()).persist(any());
    }

    @Test
    void permiteFechaFinalAbiertaSiDescuentoPrincipalEsAbierto() {
        DescuentoProducto relacion = relacionValida();
        relacion.getIdDescuento().setFechaHasta(null);
        relacion.setFechaHasta(null);
        prepararEntidades(relacion, true, true, 35, null);
        prepararConteo(relacion, 0L);

        dao.crear(relacion);

        verify(em).persist(same(relacion));
        verify(em).flush();
    }

    @Test
    void rechazaDescuentoSinTipo() {
        DescuentoProducto relacion = relacionValida();
        relacion.getIdDescuento().setIdTipoDescuento(null);
        prepararEntidades(relacion, true, true, null, null);
        IllegalArgumentException fallo = assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));
        assertEquals("El descuento no tiene un tipo válido", fallo.getMessage());
        verify(em, never()).persist(any());
    }

    @Test
    void rechazaTipoSinLimite() {
        DescuentoProducto relacion = relacionValida();
        prepararEntidades(relacion, true, true, null, null);
        IllegalArgumentException fallo = assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));
        assertEquals("No se pudo determinar el límite del tipo de descuento", fallo.getMessage());
        verify(em, never()).persist(any());
    }

    @Test
    void rechazaPorcentajeSuperiorAlMaximo() {
        DescuentoProducto relacion = relacionValida();
        relacion.setValor(40);
        prepararEntidades(relacion, true, true, 30, null);
        IllegalArgumentException fallo = assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));
        assertEquals("El descuento aplicado no puede superar 30 %", fallo.getMessage());
        verify(em, never()).persist(any());
    }

    @Test
    void rechazaIdDeAsociacionDuplicado() {
        DescuentoProducto relacion = relacionValida();
        DescuentoProducto existente = new DescuentoProducto(relacion.getIdDescuentoProducto());
        prepararEntidades(relacion, true, true, 35, existente);
        IllegalArgumentException fallo = assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));
        assertEquals("La asociación ya existe", fallo.getMessage());
        verify(em, never()).persist(any());
    }

    @Test
    void rechazaProductoYDescuentoDuplicados() {
        DescuentoProducto relacion = relacionValida();
        prepararEntidades(relacion, true, true, 35, null);
        prepararConteo(relacion, 1L);
        IllegalArgumentException fallo = assertThrows(IllegalArgumentException.class,
                () -> dao.crear(relacion));
        assertEquals("Este descuento ya está asociado al producto", fallo.getMessage());
        verify(em, never()).persist(any());
        verify(em, never()).flush();
    }

    @Test
    void errorDePersistenciaSePropagaYNoConfirmaEscritura() {
        DescuentoProducto relacion = relacionValida();
        prepararEntidades(relacion, true, true, 35, null);
        prepararConteo(relacion, 0L);
        IllegalStateException fallo = new IllegalStateException("Persistencia fallida");
        doThrow(fallo).when(em).persist(same(relacion));

        assertSame(fallo, assertThrows(IllegalStateException.class,
                () -> dao.crear(relacion)));
        verify(em, never()).flush();
    }

    @Test
    void eliminarNuloNoBorraDatos() {
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(null));
        verifyNoInteractions(em);
    }

    @Test
    void eliminarSinIdNoBorraDatos() {
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(new DescuentoProducto()));
        verifyNoInteractions(em);
    }

    @Test
    void eliminarRelacionInexistenteNoBorraNada() {
        DescuentoProducto relacion = relacionValida();
        // Sin stub: em.find() devuelve null.
        IllegalArgumentException fallo = assertThrows(IllegalArgumentException.class,
                () -> dao.eliminar(relacion));
        assertEquals("La asociación ya no existe", fallo.getMessage());
        verify(em, never()).remove(any());
        verify(em, never()).flush();
    }

    @Test
    void eliminarSoloLaAsociacionExistente() {
        DescuentoProducto relacion = relacionValida();
        when(em.find(DescuentoProducto.class, relacion.getIdDescuentoProducto()))
                .thenReturn(relacion);

        dao.eliminar(relacion);

        verify(em).remove(same(relacion));
        verify(em).flush();
        verify(em, never()).remove(same(relacion.getIdProducto()));
        verify(em, never()).remove(same(relacion.getIdDescuento()));
    }

    @Test
    void errorAlEliminarSePropaga() {
        DescuentoProducto relacion = relacionValida();
        when(em.find(DescuentoProducto.class, relacion.getIdDescuentoProducto()))
                .thenReturn(relacion);
        IllegalStateException fallo = new IllegalStateException("Error al borrar");
        doThrow(fallo).when(em).remove(same(relacion));

        assertSame(fallo, assertThrows(IllegalStateException.class,
                () -> dao.eliminar(relacion)));
        verify(em, never()).flush();
    }

    /**
     * Un único stub de EntityManager.find() resuelve todas las entidades. Esto
     * evita PotentialStubbingProblem al consultar distintas clases con find().
     */
    private void prepararEntidades(DescuentoProducto relacion,
            boolean productoExiste,
            boolean descuentoExiste,
            Integer maximo,
            DescuentoProducto asociacionExistente) {
        Producto producto = relacion.getIdProducto();
        Descuento descuento = relacion.getIdDescuento();
        TipoDescuento tipo = new TipoDescuento();
        tipo.setDescuentoMaximo(maximo);
        doAnswer(invocacion -> {
            Class<?> clase = invocacion.getArgument(0);
            Object identificador = invocacion.getArgument(1);
            if (clase == Producto.class
                    && productoExiste && producto.getIdProducto().equals(identificador)) {
                return producto;
            }
            if (clase == Descuento.class
                    && descuentoExiste && descuento.getIdDescuento().equals(identificador)) {
                return descuento;
            }
            if (clase == TipoDescuento.class
                    && descuento.getIdTipoDescuento() != null
                    && descuento.getIdTipoDescuento().equals(identificador)) {
                return tipo;
            }
            if (clase == DescuentoProducto.class
                    && asociacionExistente != null
                    && asociacionExistente.getIdDescuentoProducto().equals(identificador)) {
                return asociacionExistente;
            }
            return null;
        }).when(em).find(any(Class.class), any());
    }

    private void prepararConteo(DescuentoProducto relacion, long cantidad) {
        @SuppressWarnings("unchecked")
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createNamedQuery("DescuentoProducto.countByProductoAndDescuento", Long.class))
                .thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(cantidad);
    }

    private DescuentoProducto relacionValida() {
        Producto producto = new Producto(UUID.randomUUID());
        producto.setActivo(true);
        Descuento descuento = new Descuento(UUID.randomUUID());
        descuento.setIdTipoDescuento(UUID.randomUUID());
        descuento.setFechaDesde(dia(0));
        descuento.setFechaHasta(dia(5));
        DescuentoProducto relacion = new DescuentoProducto(UUID.randomUUID());
        relacion.setIdProducto(producto);
        relacion.setIdDescuento(descuento);
        relacion.setValor(15);
        relacion.setFechaDesde(dia(0));
        relacion.setFechaHasta(dia(2));
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
}
