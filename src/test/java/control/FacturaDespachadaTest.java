package control;
import entity.*;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class FacturaDespachadaTest {
 @Mock EntityManager em; @InjectMocks FacturaDAO dao;
 @ParameterizedTest @ValueSource(strings={"PENDIENTE","EN_PREPARACION","LISTA","CANCELADA"}) void rechazaFacturaSinOrdenExistente(String estado){
  Caja caja=new Caja(UUID.randomUUID());caja.setActivo(true);EmpleadoRol er=new EmpleadoRol(UUID.randomUUID());er.setActivo(true);
  Factura f=new Factura(UUID.randomUUID());f.setIdCaja(caja);f.setIdEmpleadoRol(er);
  when(em.find(Caja.class,caja.getIdCaja())).thenReturn(caja);when(em.find(EmpleadoRol.class,er.getIdEmpleadoRol())).thenReturn(er);
  Orden o=new Orden(UUID.randomUUID());OrdenProducto op=new OrdenProducto(UUID.randomUUID());op.setIdOrden(o);
  when(em.find(OrdenProducto.class,op.getIdOrdenProducto())).thenReturn(op);when(em.find(Orden.class,o.getIdOrden(),LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);
  FacturaOrdenProducto d=new FacturaOrdenProducto();d.setIdOrdenProducto(op);
  assertThrows(FacturaDAO.Conflicto.class,()->dao.crearFacturaCompleta(f,List.of(d)));verify(em,never()).persist(any());
 }
}
