package control;
import entity.*;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class CancelacionOrdenTest {
 @Mock EntityManager em; @InjectMocks OrdenDAO dao;
 UUID propietario=UUID.randomUUID(), id=UUID.randomUUID();
 Orden orden(String estado) {
  Orden o=new Orden(id);o.setEstadoPreparacion(estado);EmpleadoRol er=new EmpleadoRol(UUID.randomUUID());er.setIdEmpleado(new Empleado(propietario));o.setIdEmpleadoRol(er);
  when(em.find(Orden.class,id,LockModeType.PESSIMISTIC_WRITE)).thenReturn(o);return o;
 }
 void facturada(long cantidad){TypedQuery<Long> q=mock(TypedQuery.class,RETURNS_SELF);when(em.createQuery(startsWith("SELECT COUNT(fop)"),eq(Long.class))).thenReturn(q);when(q.getSingleResult()).thenReturn(cantidad);}
 @Test void propietarioCancelaConHistorial(){Orden o=orden("PENDIENTE");facturada(0);assertSame(o,dao.cancelar(id,propietario,false,true));assertEquals("CANCELADA",o.getEstadoPreparacion());assertNotNull(o.getCanceladaEn());assertEquals(propietario.toString(),o.getCanceladaPor());verify(em,never()).remove(any());}
 @Test void administradorPuedeCancelarAjena(){orden("PENDIENTE");facturada(0);assertEquals("CANCELADA",dao.cancelar(id,UUID.randomUUID(),true,false).getEstadoPreparacion());}
 @Test void camareroAjeno403(){orden("PENDIENTE");assertEquals(403,assertThrows(OrdenDAO.CancelacionRechazada.class,()->dao.cancelar(id,UUID.randomUUID(),false,true)).getEstado());verify(em,never()).flush();}
 @Test void rolSinPermiso403(){orden("PENDIENTE");assertEquals(403,assertThrows(OrdenDAO.CancelacionRechazada.class,()->dao.cancelar(id,propietario,false,false)).getEstado());}
 @Test void anonimo401(){assertEquals(401,assertThrows(OrdenDAO.CancelacionRechazada.class,()->dao.cancelar(id,null,false,false)).getEstado());verifyNoInteractions(em);}
 @ParameterizedTest @ValueSource(strings={"EN_PREPARACION","LISTA","DESPACHADA","CANCELADA"}) void rechazaEstados(String estado){orden(estado);assertEquals(409,assertThrows(OrdenDAO.CancelacionRechazada.class,()->dao.cancelar(id,propietario,false,true)).getEstado());verify(em,never()).flush();}
 @Test void facturadaNoSeCancela(){orden("PENDIENTE");facturada(1);assertThrows(OrdenDAO.CancelacionRechazada.class,()->dao.cancelar(id,propietario,false,true));verify(em,never()).flush();}
 @Test void segundaCancelacionRechazada(){orden("PENDIENTE");facturada(0);dao.cancelar(id,propietario,false,true);assertThrows(OrdenDAO.CancelacionRechazada.class,()->dao.cancelar(id,propietario,false,true));verify(em,times(1)).flush();}
}
