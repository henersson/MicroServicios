package co.edu.unicauca.saberpro.banco.infraestructura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA de la tabla {@code eventos_procesados}: la memoria de qué eventos
 * entrantes ya se aplicaron.
 *
 * <p>Es lo que hace idempotente al consumidor. La clave primaria sobre
 * {@code event_id} es la garantía de verdad: aunque dos hilos procesaran el
 * mismo evento a la vez, el segundo fallaría al insertar y su transacción se
 * desharía entera, cambio incluido.
 */
@Entity
@Table(name = "eventos_procesados")
public class EventoProcesadoEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    protected EventoProcesadoEntity() {
    }

    public EventoProcesadoEntity(UUID eventId, String eventType, Instant procesadoEn) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.procesadoEn = procesadoEn;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }
}
