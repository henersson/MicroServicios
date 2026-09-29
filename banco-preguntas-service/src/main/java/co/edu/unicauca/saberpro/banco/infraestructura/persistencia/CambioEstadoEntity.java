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

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de la tabla {@code pregunta_historial_estados}: una entrada del
 * historial del agregado.
 *
 * <p>Esta tabla solo crece. No hay ningún camino en el código que actualice o
 * borre una entrada, porque el historial es la evidencia de qué pasó con la
 * pregunta y para qué sirve si se puede reescribir.
 */
@Entity
@Table(name = "pregunta_historial_estados")
public class CambioEstadoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pregunta_id", nullable = false)
    private PreguntaEntity pregunta;

    @Column(name = "orden", nullable = false)
    private int orden;

    /** Null en la entrada de creación: antes de existir no había estado previo. */
    @Column(name = "estado_anterior", length = 30)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", nullable = false, length = 30)
    private String estadoNuevo;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "fecha", nullable = false)
    private Instant fecha;

    @Column(name = "motivo", columnDefinition = "text")
    private String motivo;

    protected CambioEstadoEntity() {
    }

    public CambioEstadoEntity(PreguntaEntity pregunta, int orden, String estadoAnterior,
                              String estadoNuevo, UUID usuarioId, Instant fecha, String motivo) {
        this.pregunta = pregunta;
        this.orden = orden;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.usuarioId = usuarioId;
        this.fecha = fecha;
        this.motivo = motivo;
    }

    public int getOrden() {
        return orden;
    }

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public String getEstadoNuevo() {
        return estadoNuevo;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public Instant getFecha() {
        return fecha;
    }

    public String getMotivo() {
        return motivo;
    }
}
