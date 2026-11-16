package control;
import entity.Orden;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class PreparacionOrdenTest {
 @org.mockito.Mock EntityManager em;
 @org.mockito.InjectMocks OrdenDAO dao;
 @Test void transicionesNoModificanFacturas() {
  UUID id=UUID.randomUUID();Orden orden=new Orden(id);
  when(em.find(Orden.class,id,LockModeType.PESSIMISTIC_WRITE)).thenReturn(orden);
  dao.cambiarPreparacion(id,"PENDIENTE","EN_PREPARACION");
  assertEquals("EN_PREPARACION",orden.getEstadoPreparacion());
  dao.cambiarPreparacion(id,"EN_PREPARACION","LISTA");dao.cambiarPreparacion(id,"LISTA","DESPACHADA");
  assertEquals("DESPACHADA",orden.getEstadoPreparacion());verify(em,times(3)).flush();verify(em,never()).persist(any());
 }
 @Test void rechazaSaltoYOpcionObsoleta() {
  UUID id=UUID.randomUUID();Orden orden=new Orden(id);
  when(em.find(Orden.class,id,LockModeType.PESSIMISTIC_WRITE)).thenReturn(orden);
  assertThrows(IllegalStateException.class,()->dao.cambiarPreparacion(id,"PENDIENTE","DESPACHADA"));
  assertThrows(IllegalStateException.class,()->dao.cambiarPreparacion(id,"LISTA","DESPACHADA"));verify(em,never()).flush();
 }
}
