package co.edu.unicauca.saberpro.banco.infraestructura.persistencia;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA de la tabla {@code preguntas}.
 *
 * <p><strong>No es el agregado.</strong> Es su reflejo en la base de datos y
 * vive solo en la capa de infraestructura: {@code PreguntaMapper} traduce entre
 * las dos. Mantenerlas separadas cuesta un mapeador, pero evita que las
 * necesidades del ORM (constructor vacío, setters, colecciones mutables,
 * anotaciones por todas partes) deformen el modelo de dominio, y garantiza que
 * nunca se filtre una entidad de persistencia a la API.
 */
@Entity
@Table(name = "preguntas")
public class PreguntaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private UUID autorId;

    @Column(name = "contexto", nullable = false, columnDefinition = "text")
    private String contexto;

    @Column(name = "pregunta_directa", nullable = false, columnDefinition = "text")
    private String preguntaDirecta;

    @Column(name = "justificacion", nullable = false, columnDefinition = "text")
    private String justificacion;

    @Column(name = "competencia_codigo", nullable = false, length = 50)
    private String competenciaCodigo;

    @Column(name = "competencia_nombre", nullable = false, length = 200)
    private String competenciaNombre;

    @Column(name = "tema", nullable = false, length = 200)
    private String tema;

    @Column(name = "subtema", nullable = false, length = 200)
    private String subtema;

    /**
     * Los enums se guardan como texto y no con {@code @Enumerated(ORDINAL)}:
     * un ordinal se rompe en silencio el día que alguien reordene el enum.
     */
    @Column(name = "nivel_dificultad", nullable = false, length = 20)
    private String nivelDificultad;

    @Column(name = "estado", nullable = false, length = 30)
    private String estado;

    @Column(name = "observaciones_ultima_revision", columnDefinition = "text")
    private String observacionesUltimaRevision;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    // Las colecciones van en EAGER porque el agregado siempre se carga entero:
    // una Pregunta sin sus opciones no es una pregunta válida (invariante 1), y
    // el mapeador al dominio las necesita todas. Así se evita de raíz cualquier
    // LazyInitializationException fuera de la transacción.
    //
    // Contrapartida asumida: al listar N preguntas, Hibernate emite una consulta
    // adicional por colección y por fila (el clásico N+1). Es aceptable con el
    // volumen de un banco de preguntas de una asignatura y con el tope de 100
    // resultados por página; si creciera, la solución es una consulta con
    // @EntityGraph o una proyección de solo lectura para el listado.
    @OneToMany(mappedBy = "pregunta", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("orden ASC")
    private List<OpcionEntity> opciones = new ArrayList<>();

    @OneToMany(mappedBy = "pregunta", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("orden ASC")
    private List<BibliografiaEntity> bibliografia = new ArrayList<>();

    @OneToMany(mappedBy = "pregunta", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("orden ASC")
    private List<CambioEstadoEntity> historialEstados = new ArrayList<>();

    /** Constructor sin argumentos que exige JPA. No usar desde el código. */
    protected PreguntaEntity() {
    }

    public PreguntaEntity(UUID id, UUID autorId) {
        this.id = id;
        this.autorId = autorId;
    }

    @PrePersist
    void alInsertar() {
        Instant ahora = Instant.now();
        if (creadaEn == null) {
            creadaEn = ahora;
        }
        actualizadaEn = ahora;
    }

    @PreUpdate
    void alActualizar() {
        actualizadaEn = Instant.now();
    }

    // ── Accesores ───────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getAutorId() {
        return autorId;
    }

    public String getContexto() {
        return contexto;
    }

    public void setContexto(String contexto) {
        this.contexto = contexto;
    }

    public String getPreguntaDirecta() {
        return preguntaDirecta;
    }

    public void setPreguntaDirecta(String preguntaDirecta) {
        this.preguntaDirecta = preguntaDirecta;
    }

    public String getJustificacion() {
        return justificacion;
    }

    public void setJustificacion(String justificacion) {
        this.justificacion = justificacion;
    }

    public String getCompetenciaCodigo() {
        return competenciaCodigo;
    }

    public void setCompetenciaCodigo(String competenciaCodigo) {
        this.competenciaCodigo = competenciaCodigo;
    }

    public String getCompetenciaNombre() {
        return competenciaNombre;
    }

    public void setCompetenciaNombre(String competenciaNombre) {
        this.competenciaNombre = competenciaNombre;
    }

    public String getTema() {
        return tema;
    }

    public void setTema(String tema) {
        this.tema = tema;
    }

    public String getSubtema() {
        return subtema;
    }

    public void setSubtema(String subtema) {
        this.subtema = subtema;
    }

    public String getNivelDificultad() {
        return nivelDificultad;
    }

    public void setNivelDificultad(String nivelDificultad) {
        this.nivelDificultad = nivelDificultad;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getObservacionesUltimaRevision() {
        return observacionesUltimaRevision;
    }

    public void setObservacionesUltimaRevision(String observacionesUltimaRevision) {
        this.observacionesUltimaRevision = observacionesUltimaRevision;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public List<OpcionEntity> getOpciones() {
        return opciones;
    }

    public List<BibliografiaEntity> getBibliografia() {
        return bibliografia;
    }

    public List<CambioEstadoEntity> getHistorialEstados() {
        return historialEstados;
    }
}
