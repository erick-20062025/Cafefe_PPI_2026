/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary.rest.messages;


import java.io.Serializable;


/**
 *
 * @author erick
 */

/**
 * Representa una respuesta estándar de la API REST.
 * Permite reutilizar la misma estructura de mensajes
 * en diferentes recursos de la aplicación.
 */
public class ApiResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String codigo;
    private String mensaje;
    private String detalle;

    public ApiResponse() {
    }

    public ApiResponse(String codigo, String mensaje, String detalle) {
        this.codigo = codigo;
        this.mensaje = mensaje;
        this.detalle = detalle;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }
}
