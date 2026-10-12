/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary.rest;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 *
 * @author erick
 */

/**
 * Configura la entrada principal de la API REST de la aplicación.
 * Todas las rutas REST comenzarán con el prefijo /api.
 */
@ApplicationPath("/api")
public class RestApplication extends Application {

}
