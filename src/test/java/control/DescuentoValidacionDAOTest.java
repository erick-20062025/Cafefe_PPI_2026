package control;

import entity.Descuento;
import entity.TipoDescuento;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DescuentoValidacionDAOTest {

    private DescuentoDAO dao;
    private EntityManager em;

    private UUID idTipo;
    private UUID idDescuento;

    @BeforeEach
    void configurar() throws Exception {
        dao = new DescuentoDAO();
        em = mock(EntityManager.class);

        Field campo = DescuentoDAO.class.getDeclaredField("em");
        campo.setAccessible(true);
        campo.set(dao, em);

        idTipo = UUID.randomUUID();
        idDescuento = UUID.randomUUID();
    }

    private Date fecha(int anio, int mes, int dia) {
        Calendar calendario = Calendar.getInstance();
        calendario.clear();
        calendario.set(anio, mes - 1, dia);
        return calendario.getTime();
    }

    private Descuento descuentoValido() {
        Descuento descuento = new Descuento();
        descuento.setIdDescuento(idDescuento);
        descuento.setIdTipoDescuento(idTipo);
        descuento.setNombre("Promoción especial");
        descuento.setFechaDesde(new Date());
        descuento.setFechaHasta(null);
        return descuento;
    }

    private TipoDescuento tipoValido(boolean activo) {
        TipoDescuento tipo = new TipoDescuento();
        tipo.setIdTipoDescuento(idTipo);
        tipo.setNombre("Promocional");
        tipo.setActivo(activo);
        tipo.setDescuentoMaximo(50);
        return tipo;
    }

    @Test
    void crearDescuentoValidoDebePersistir() {
        Descuento descuento = descuentoValido();

        when(em.find(TipoDescuento.class, idTipo))
                .thenReturn(tipoValido(true));

        dao.crear(descuento);

        verify(em).persist(descuento);
    }

    @Test
    void crearDescuentoNuloDebeSerRechazado() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(null));

        verifyNoInteractions(em);
    }

    @Test
    void crearDescuentoSinIdDebeSerRechazado() {
        Descuento descuento = descuentoValido();
        descuento.setIdDescuento(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(descuento));

        verifyNoInteractions(em);
    }

    @Test
    void crearDescuentoSinTipoDebeSerRechazado() {
        Descuento descuento = descuentoValido();
        descuento.setIdTipoDescuento(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(descuento));

        verifyNoInteractions(em);
    }

    @Test
    void crearDescuentoSinNombreDebeSerRechazado() {
        Descuento descuento = descuentoValido();
        descuento.setNombre(" ");

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(descuento));

        verifyNoInteractions(em);
    }

    @Test
    void crearDescuentoSinFechaDesdeDebeSerRechazado() {
        Descuento descuento = descuentoValido();
        descuento.setFechaDesde(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(descuento));

        verifyNoInteractions(em);
    }

    @Test
    void crearDescuentoConFechasInvertidasDebeSerRechazado() {
        Descuento descuento = descuentoValido();
        descuento.setFechaHasta(fecha(2026, 11, 10));

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(descuento));

        verifyNoInteractions(em);
    }

    @Test
    void crearConTipoInexistenteDebeSerRechazado() {
        Descuento descuento = descuentoValido();

        when(em.find(TipoDescuento.class, idTipo))
                .thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(descuento));

        verify(em, never()).persist(any(Descuento.class));
    }

    @Test
    void crearConTipoInactivoDebeSerRechazado() {
        Descuento descuento = descuentoValido();

        when(em.find(TipoDescuento.class, idTipo))
                .thenReturn(tipoValido(false));

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(descuento));

        verify(em, never()).persist(any(Descuento.class));
    }

    @Test
    void crearSinFechaHastaDebeSerPermitido() {
        Descuento descuento = descuentoValido();
        descuento.setFechaHasta(null);

        when(em.find(TipoDescuento.class, idTipo))
                .thenReturn(tipoValido(true));

        dao.crear(descuento);

        verify(em).persist(descuento);
    }

    @Test
    void modificarDescuentoValidoDebeEjecutarMerge() {
        Descuento descuento = descuentoValido();

        when(em.find(Descuento.class, idDescuento))
                .thenReturn(descuentoValido());

        when(em.find(TipoDescuento.class, idTipo))
                .thenReturn(tipoValido(true));

        dao.modificar(descuento);

        verify(em).merge(descuento);
    }

    @Test
    void modificarDescuentoInexistenteDebeSerRechazado() {
        Descuento descuento = descuentoValido();

        when(em.find(Descuento.class, idDescuento))
                .thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.modificar(descuento));

        verify(em, never()).merge(any(Descuento.class));
    }

    @Test
    void modificarCambiandoTipoDebeSerRechazado() {
        Descuento descuento = descuentoValido();
        descuento.setIdTipoDescuento(UUID.randomUUID());

        when(em.find(Descuento.class, idDescuento))
                .thenReturn(descuentoValido());

        assertThrows(IllegalArgumentException.class,
                () -> dao.modificar(descuento));

        verify(em, never()).merge(any(Descuento.class));
    }

    @Test
    void modificarConTipoInactivoDebeSerPermitido() {
        Descuento descuento = descuentoValido();

        when(em.find(Descuento.class, idDescuento))
                .thenReturn(descuentoValido());

        when(em.find(TipoDescuento.class, idTipo))
                .thenReturn(tipoValido(false));

        dao.modificar(descuento);

        verify(em).merge(descuento);
    }

    @Test
    void modificarConTipoInexistenteDebeSerRechazado() {
        Descuento descuento = descuentoValido();

        when(em.find(Descuento.class, idDescuento))
                .thenReturn(descuentoValido());

        when(em.find(TipoDescuento.class, idTipo))
                .thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.modificar(descuento));

        verify(em, never()).merge(any(Descuento.class));
    }

    @Test
    void modificarDescuentoHistoricoDebeSerPermitido() {
        Descuento descuento = descuentoValido();
        descuento.setFechaDesde(fecha(2020, 1, 1));
        descuento.setFechaHasta(fecha(2020, 12, 31));

        when(em.find(Descuento.class, idDescuento))
                .thenReturn(descuentoValido());

        when(em.find(TipoDescuento.class, idTipo))
                .thenReturn(tipoValido(true));

        dao.modificar(descuento);

        verify(em).merge(descuento);
    }

    @Test
    void crearDescuentoConFechaPasadaDebeSerRechazado() {
        Descuento descuento = descuentoValido();

        Calendar calendario = Calendar.getInstance();
        calendario.add(Calendar.DAY_OF_MONTH, -1);

        descuento.setFechaDesde(calendario.getTime());
        descuento.setFechaHasta(null);

        assertThrows(IllegalArgumentException.class,
                () -> dao.crear(descuento));

        verify(em, never()).persist(any(Descuento.class));
    }

    @Test
    void crearDescuentoConFechaDeHoyDebeSerPermitido() {
        Descuento descuento = descuentoValido();
        descuento.setFechaDesde(new Date());
        descuento.setFechaHasta(null);

        when(em.find(TipoDescuento.class, idTipo))
                .thenReturn(tipoValido(true));

        dao.crear(descuento);

        verify(em).persist(descuento);
    }

}
