package co.edu.unicauca.saberpro.banco.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repositorio de Spring Data para la tabla de idempotencia
 * {@code eventos_procesados}.
 */
public interface EventoProcesadoJpaRepository extends JpaRepository<EventoProcesadoEntity, UUID> {
}
