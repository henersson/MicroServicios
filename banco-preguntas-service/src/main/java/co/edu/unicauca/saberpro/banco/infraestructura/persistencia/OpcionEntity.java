package co.edu.unicauca.saberpro.banco.infraestructura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la tabla {@code pregunta_opciones}: el reflejo persistente del
 * Value Object {@code Opcion}.
 *
 * <p>Tiene identificador propio porque JPA lo necesita, pero eso es un detalle
 * de infraestructura: en el dominio una opción no tiene identidad, se compara
 * por su valor. El {@code orden} sí es significativo, porque es el que ve el
 * estudiante y el que citan las observaciones del revisor.
 */
@Entity
@Table(name = "pregunta_opciones")
public class OpcionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pregunta_id", nullable = false)
    private PreguntaEntity pregunta;

    @Column(name = "orden", nullable = false)
    private int orden;

    @Column(name = "texto", nullable = false, columnDefinition = "text")
    private String texto;

    @Column(name = "es_correcta", nullable = false)
    private boolean esCorrecta;

    protected OpcionEntity() {
    }

    public OpcionEntity(PreguntaEntity pregunta, int orden, String texto, boolean esCorrecta) {
        this.pregunta = pregunta;
        this.orden = orden;
        this.texto = texto;
        this.esCorrecta = esCorrecta;
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public boolean isEsCorrecta() {
        return esCorrecta;
    }

    public void setEsCorrecta(boolean esCorrecta) {
        this.esCorrecta = esCorrecta;
    }
}
