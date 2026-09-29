package co.edu.unicauca.saberpro.banco.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * Repositorio de Spring Data para {@link PreguntaEntity}.
 *
 * <p>Es un detalle de infraestructura: el dominio solo conoce
 * {@code PreguntaRepository}, y {@link PreguntaRepositoryAdapter} hace de puente
 * entre los dos. Esa indirección es lo que permite cambiar de JPA a otra cosa
 * sin tocar una sola línea del dominio.
 *
 * <p>Extiende {@code JpaSpecificationExecutor} porque los filtros del listado
 * son opcionales y se combinan: hacerlo con JPQL obligaría a escribir consultas
 * del tipo {@code (:estado is null or ...)}, que en PostgreSQL además obligan a
 * castear cada parámetro para que el driver pueda inferir su tipo.
 */
public interface PreguntaJpaRepository
        extends JpaRepository<PreguntaEntity, UUID>, JpaSpecificationExecutor<PreguntaEntity> {
}
