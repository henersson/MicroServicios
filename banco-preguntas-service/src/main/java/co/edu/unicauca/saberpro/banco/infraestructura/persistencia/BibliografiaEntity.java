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
 * Entidad JPA de la tabla {@code pregunta_bibliografia}: una referencia del
 * Value Object {@code Bibliografia}.
 *
 * <p>Se guarda en su propia tabla en vez de como array o JSON para que las
 * referencias sean consultables con SQL corriente, que es lo que hará falta el
 * día que alguien pregunte "¿qué preguntas cita a Newman?".
 */
@Entity
@Table(name = "pregunta_bibliografia")
public class BibliografiaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pregunta_id", nullable = false)
    private PreguntaEntity pregunta;

    @Column(name = "orden", nullable = false)
    private int orden;

    @Column(name = "referencia", nullable = false, columnDefinition = "text")
    private String referencia;

    protected BibliografiaEntity() {
    }

    public BibliografiaEntity(PreguntaEntity pregunta, int orden, String referencia) {
        this.pregunta = pregunta;
        this.orden = orden;
        this.referencia = referencia;
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }
}
