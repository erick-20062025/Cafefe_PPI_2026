package control;

import entity.DescuentoProducto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DescuentoProductoDAOTest {

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<DescuentoProducto> query;

    @InjectMocks
    private DescuentoProductoDAO descuentoProductoDAO;

    @Test
    void debeBuscarProductosPorIdDescuento() {

        UUID idDescuento = UUID.randomUUID();

        DescuentoProducto relacion1 =
                new DescuentoProducto();

        DescuentoProducto relacion2 =
                new DescuentoProducto();

        List<DescuentoProducto> relaciones =
                List.of(relacion1, relacion2);

        String jpql =
                "SELECT dp FROM DescuentoProducto dp "
                + "WHERE dp.idDescuento.idDescuento = :idDescuento";

        when(
                em.createQuery(
                        jpql,
                        DescuentoProducto.class
                )
        ).thenReturn(query);

        when(
                query.setParameter(
                        "idDescuento",
                        idDescuento
                )
        ).thenReturn(query);

        when(
                query.getResultList()
        ).thenReturn(relaciones);

        List<DescuentoProducto> resultado =
                descuentoProductoDAO
                        .findByIdDescuento(idDescuento);

        assertSame(
                relaciones,
                resultado
        );

        verify(em).createQuery(
                jpql,
                DescuentoProducto.class
        );

        verify(query).setParameter(
                "idDescuento",
                idDescuento
        );

        verify(query).getResultList();
    }
}
