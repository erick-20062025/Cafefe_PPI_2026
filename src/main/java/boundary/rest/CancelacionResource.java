package boundary.rest;
import boundary.rest.dto.OrdenDTO;
import boundary.rest.seguridad.AccesoPersonal;
import control.OrdenDAO;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.UUID;
@jakarta.enterprise.context.RequestScoped
public class CancelacionResource {
 @Inject AccesoPersonal acceso;
 @Inject OrdenDAO ordenes;
 @Context HttpServletRequest request;
 @GET @Path("/gestionables")
 public Response gestionables() {
  var identidad=acceso.recuperar(request);
  if(identidad==null)return SesionResource.error(401,"Inicia sesión.");
  boolean admin=identidad.getRoles().contains("administrador");
  if(!admin && !identidad.getRoles().contains("camarero"))return SesionResource.error(403,"Rol no autorizado.");
  UUID empleado=(UUID)request.getSession(false).getAttribute(AccesoPersonal.ATRIBUTO);
  return Response.ok(ordenes.findGestionables(empleado,admin).stream().map(OrdenDTO::resumen).toList()).header("Cache-Control","no-store").build();
 }
 @POST @Path("/{id}/cancelacion")
 public Response cancelar(@PathParam("id") String id) {
  var identidad=acceso.recuperar(request);
  if(identidad==null)return SesionResource.error(401,"Inicia sesión.");
  if(!SesionResource.ORIGEN.equals(request.getHeader("Origin")))return SesionResource.error(403,"Origen no permitido.");
  boolean admin=identidad.getRoles().contains("administrador"), camarero=identidad.getRoles().contains("camarero");
  if(!admin && !camarero)return SesionResource.error(403,"Rol no autorizado.");
  try {
   UUID empleado=(UUID)request.getSession(false).getAttribute(AccesoPersonal.ATRIBUTO);
   var orden=ordenes.cancelar(UUID.fromString(id),empleado,admin,camarero);
   return Response.ok(OrdenDTO.resumen(orden)).header("Cache-Control","no-store").build();
  } catch(IllegalArgumentException ex) {return SesionResource.error(400,"Identificador inválido.");}
  catch(RuntimeException ex) {
   for(Throwable causa=ex;causa!=null;causa=causa.getCause())if(causa instanceof OrdenDAO.CancelacionRechazada c)return SesionResource.error(c.getEstado(),c.getMessage());
   return SesionResource.error(503,"No fue posible cancelar. Actualiza y verifica el estado antes de reintentar.");
  }
 }
}
