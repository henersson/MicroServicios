package co.edu.unicauca.saberpro.banco.infraestructura.persistencia;

import co.edu.unicauca.saberpro.banco.dominio.modelo.Pregunta;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.FiltroPreguntas;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.PaginaPreguntas;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.PreguntaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador que implementa el {@code PreguntaRepository} del dominio sobre JPA y
 * PostgreSQL.
 *
 * <p>Aquí se cumple la inversión de dependencias de la Clean Architecture: la
 * interfaz la declara el dominio y la implementa la infraestructura, así que la
 * flecha de dependencia apunta hacia adentro y no al revés.
 *
 * <p>También es el punto donde las excepciones de Spring y JPA se traducen a
 * excepciones del dominio: ninguna capa de arriba debería tener que saber qué
 * tecnología de persistencia hay debajo.
 */
@Repository
public class PreguntaRepositoryAdapter implements PreguntaRepository {

    private final PreguntaJpaRepository jpa;
    private final PreguntaMapper mapeador;

    public PreguntaRepositoryAdapter(PreguntaJpaRepository jpa, PreguntaMapper mapeador) {
        this.jpa = jpa;
        this.mapeador = mapeador;
    }

    /**
     * Guarda el agregado y le devuelve su número de versión actualizado.
     *
     * <p>Devuelve <em>la misma instancia</em> que recibió, no una copia: los
     * eventos de dominio pendientes viven dentro del agregado, y el caso de uso
     * los recoge justo después de esta llamada.
     */
    @Override
    public Pregunta guardar(Pregunta pregunta) {
        PreguntaEntity entidad = jpa.findById(pregunta.getId())
                .map(existente -> {
                    mapeador.actualizar(pregunta, existente);
                    return existente;
                })
                .orElseGet(() -> mapeador.aEntidadNueva(pregunta));

        // saveAndFlush y no save: hace falta que el INSERT/UPDATE salga ya, para
        // que un error de la base de datos aparezca aquí y no al cerrar la
        // transacción, cuando ya no se puede traducir a un error legible.
        jpa.saveAndFlush(entidad);
        return pregunta;
    }

    @Override
    public Optional<Pregunta> buscarPorId(UUID id) {
        return jpa.findById(id).map(mapeador::aDominio);
    }

    @Override
    public PaginaPreguntas buscarPorFiltros(FiltroPreguntas filtro, int pagina, int tamano) {
        // Orden estable: sin un ORDER BY explícito, PostgreSQL no garantiza que
        // dos páginas consecutivas no repitan o se salten filas.
        PageRequest peticion = PageRequest.of(pagina, tamano,
                Sort.by(Sort.Direction.DESC, "creadaEn").and(Sort.by("id")));

        Page<PreguntaEntity> resultado = jpa.findAll(comoEspecificacion(filtro), peticion);

        return new PaginaPreguntas(
                resultado.getContent().stream().map(mapeador::aDominio).toList(),
                pagina,
                tamano,
                resultado.getTotalElements());
    }

    /**
     * Convierte el filtro del dominio en una Specification de JPA.
     *
     * <p>Un criterio en null significa "no filtrar por esto", así que
     * simplemente no se agrega su predicado.
     */
    private Specification<PreguntaEntity> comoEspecificacion(FiltroPreguntas filtro) {
        return (raiz, consulta, constructor) -> {
            List<Predicate> predicados = new ArrayList<>();

            if (filtro.estado() != null) {
                predicados.add(constructor.equal(raiz.get("estado"), filtro.estado().name()));
            }
            if (tieneValor(filtro.competenciaCodigo())) {
                predicados.add(constructor.equal(raiz.get("competenciaCodigo"),
                        filtro.competenciaCodigo().trim().toUpperCase()));
            }
            if (tieneValor(filtro.tema())) {
                // Comparación sin distinguir mayúsculas: el tema lo escribe una
                // persona y "Arquitectura de Software" no debería fallar por una
                // mayúscula de diferencia.
                predicados.add(constructor.equal(
                        constructor.lower(raiz.get("tema")),
                        filtro.tema().trim().toLowerCase()));
            }
            if (filtro.nivelDificultad() != null) {
                predicados.add(constructor.equal(raiz.get("nivelDificultad"),
                        filtro.nivelDificultad().name()));
            }

            return predicados.isEmpty()
                    ? constructor.conjunction()
                    : constructor.and(predicados.toArray(Predicate[]::new));
        };
    }

    private static boolean tieneValor(String texto) {
        return texto != null && !texto.isBlank();
    }
}
