package entity;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class DescripcionAtencionOrdenTest {
 @Test void nombreYNotasSeConservan() {
  assertEquals("peña",DescripcionOrden.cliente(DescripcionOrden.conCliente("peña","Sin azúcar")));
  assertEquals("Cliente: peña | Sin azúcar",DescripcionOrden.conCliente("peña","Sin azúcar"));
  assertNull(DescripcionOrden.cliente("Nota antigua"));
 }
 @Test void virtualSeIdentificaPorAsignacionConfigurada() {
  var o=new Orden();var er=new EmpleadoRol(UUID.randomUUID());o.setIdEmpleadoRol(er);
  assertEquals("Pedido web",AtencionOrden.describir(o,er.getIdEmpleadoRol().toString()));
  assertNull(AtencionOrden.describir(o,null));
 }
 @Test void pedidoAnonimoNoInventaCamarero() {
  var o = new Orden(UUID.randomUUID());
  assertEquals("Pedido web", AtencionOrden.describir(o, null));
 }
 @Test void camareroRealSeObtieneDeOrden() {
  var o=new Orden();var er=new EmpleadoRol(UUID.randomUUID());o.setIdEmpleadoRol(er);
  var e=new Empleado();e.setNombre("Ana");e.setApellido("Peña");er.setIdEmpleado(e);
  var rol=new Rol();rol.setNombre("camarero");er.setIdRol(rol);
  assertEquals("Ana Peña",AtencionOrden.describir(o,null));
  rol.setNombre("cajero");assertNull(AtencionOrden.describir(o,null));
 }
}
