package co.edu.unicauca.saberpro.banco.infraestructura.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Documentación OpenAPI de la API REST, en español.
 *
 * <p>El resultado se ve en Swagger UI ({@code /swagger-ui.html}) y el documento
 * OpenAPI está en {@code /v3/api-docs}, que es de donde el tercer microservicio
 * puede generarse un cliente sin que le pasemos ninguna librería.
 */
@Configuration
public class ConfiguracionOpenApi {

    @Bean
    public OpenAPI apiBancoPreguntas(@Value("${server.port:8081}") int puertoHttp) {
        return new OpenAPI()
                .info(new Info()
                        .title("Banco de Preguntas — API REST")
                        .version("1.0.0")
                        .description("""
                                Microservicio del Bounded Context **Banco de Preguntas** del \
                                sistema de gestión de preguntas de selección múltiple para las \
                                Pruebas Saber Pro.

                                Gestiona el ciclo de vida completo de una pregunta: creación, \
                                validación estructural, envío a revisión por pares, publicación \
                                y archivado.

                                ### Autenticación
                                No hay autenticación real (queda como trabajo futuro). El usuario \
                                se simula con dos cabeceras **obligatorias** en las operaciones \
                                que lo requieren:

                                - `X-Usuario-Id`: UUID del usuario.
                                - `X-Usuario-Rol`: `AUTOR`, `REVISOR`, `ADMINISTRADOR`, \
                                `DOCENTE`, `ESTUDIANTE` o `COORDINADOR`.

                                ### Errores
                                Todos los errores siguen el formato **Problem Details \
                                (RFC 7807)**, con un campo adicional `errores[]` que lista cada \
                                regla incumplida en español.

                                | Código | Cuándo |
                                |---|---|
                                | 400 | El contenido incumple las reglas del banco (invariantes 1 a 5) |
                                | 403 | El rol o el usuario no pueden hacer esa operación (invariante 7) |
                                | 404 | La pregunta no existe |
                                | 409 | Transición de estado no permitida (invariante 6) |

                                ### Otros protocolos
                                Este servicio expone además un **servidor gRPC** en el puerto \
                                9091 (`ObtenerPregunta`, `ListarPreguntasPublicadas`) y publica \
                                eventos de dominio en **RabbitMQ**. Los contratos están en \
                                `contracts/` del repositorio.""")
                        .contact(new Contact()
                                .name("Henersson — Arquitectura de Microservicios, Universidad del Cauca"))
                        .license(new License().name("Uso académico")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + puertoHttp)
                                .description("Entorno local de desarrollo")))
                .tags(List.of(
                        new Tag()
                                .name("Preguntas")
                                .description("Ciclo de vida de las preguntas del banco: crear, "
                                        + "editar, consultar, enviar a revisión, publicar y "
                                        + "archivar.")))
                .components(new Components());
    }
}
