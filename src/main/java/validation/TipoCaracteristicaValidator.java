
package validation;

import entity.TipoCaracteristica;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class TipoCaracteristicaValidator {

    private TipoCaracteristicaValidator() {
    }

    public static void validar(TipoCaracteristica tipo) {

        if (tipo == null) {
            throw new IllegalArgumentException(
                "El tipo de característica no puede ser nulo"
            );
        }

        String nombre = tipo.getNombre();

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                "El nombre es obligatorio"
            );
        }

        if (nombre.length() > 155) {
            throw new IllegalArgumentException(
                "El nombre no puede superar 155 caracteres"
            );
        }

        if (tipo.getActivo() == null) {
            throw new IllegalArgumentException(
                "Debe indicar si el tipo está activo o inactivo"
            );
        }

        String expresion = tipo.getExpresionRegular();

        if (expresion != null && !expresion.isBlank()) {
            try {
                Pattern.compile(expresion);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(
                    "La expresión regular ingresada no es válida", e
                );
            }
        }
    }
}
