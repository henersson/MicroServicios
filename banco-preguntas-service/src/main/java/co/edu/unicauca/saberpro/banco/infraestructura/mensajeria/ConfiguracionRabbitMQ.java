package co.edu.unicauca.saberpro.banco.infraestructura.mensajeria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Topología de RabbitMQ vista desde este microservicio.
 *
 * <p>La topología "oficial" se carga desde
 * {@code infra/rabbitmq/definitions.json} al arrancar el broker. Aquí se vuelve
 * a declarar de forma <strong>idempotente</strong> a propósito: declarar un
 * exchange o una cola que ya existe con los mismos parámetros no hace nada, pero
 * si el broker se levantó sin las definiciones (o alguien lo reinició con el
 * volumen vacío), el servicio arranca igual y no se queda sin cola.
 *
 * <p>Ojo: si se declara aquí con parámetros <em>distintos</em> a los del
 * {@code definitions.json}, RabbitMQ responde con un error de canal
 * {@code PRECONDITION_FAILED}. Por eso los argumentos de dead-lettering de abajo
 * tienen que coincidir exactamente con los del archivo de definiciones.
 */
@Configuration
public class ConfiguracionRabbitMQ {

    /** Exchange único por el que viajan todos los eventos del sistema. */
    public static final String EXCHANGE_EVENTOS = "saberpro.eventos";

    /** Exchange de mensajes muertos. */
    public static final String EXCHANGE_DLX = "saberpro.eventos.dlx";

    /** Cola de la que lee este servicio: los resultados de la revisión por pares. */
    public static final String COLA_RESULTADOS_REVISION = "banco.resultados-revision";

    public static final String COLA_RESULTADOS_REVISION_DLQ = COLA_RESULTADOS_REVISION + ".dlq";

    // Routing keys que consume este servicio.
    public static final String RK_REVISOR_ASIGNADO = "revision.revisor.asignado";
    public static final String RK_PREGUNTA_APROBADA = "revision.pregunta.aprobada";
    public static final String RK_PREGUNTA_RECHAZADA = "revision.pregunta.rechazada";

    @Bean
    public TopicExchange exchangeEventos() {
        return new TopicExchange(EXCHANGE_EVENTOS, true, false);
    }

    @Bean
    public TopicExchange exchangeDlx() {
        return new TopicExchange(EXCHANGE_DLX, true, false);
    }

    /**
     * Cola de entrada de este servicio. Los mensajes que fallan de forma
     * permanente salen por el dead-letter exchange hacia su DLQ.
     */
    @Bean
    public Queue colaResultadosRevision() {
        return QueueBuilder.durable(COLA_RESULTADOS_REVISION)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", EXCHANGE_DLX,
                        "x-dead-letter-routing-key", COLA_RESULTADOS_REVISION_DLQ))
                .build();
    }

    @Bean
    public Queue colaResultadosRevisionDlq() {
        return QueueBuilder.durable(COLA_RESULTADOS_REVISION_DLQ).build();
    }

    // Los tres eventos de resultado comparten cola: los tres los procesa el
    // mismo consumidor y los tres afectan al mismo agregado, así que separarlos
    // en tres colas solo complicaría el orden de aplicación.

    @Bean
    public Binding bindingRevisorAsignado() {
        return BindingBuilder.bind(colaResultadosRevision())
                .to(exchangeEventos())
                .with(RK_REVISOR_ASIGNADO);
    }

    @Bean
    public Binding bindingPreguntaAprobada() {
        return BindingBuilder.bind(colaResultadosRevision())
                .to(exchangeEventos())
                .with(RK_PREGUNTA_APROBADA);
    }

    @Bean
    public Binding bindingPreguntaRechazada() {
        return BindingBuilder.bind(colaResultadosRevision())
                .to(exchangeEventos())
                .with(RK_PREGUNTA_RECHAZADA);
    }

    @Bean
    public Binding bindingDlq() {
        return BindingBuilder.bind(colaResultadosRevisionDlq())
                .to(exchangeDlx())
                .with(COLA_RESULTADOS_REVISION_DLQ);
    }

    /**
     * Los mensajes viajan como JSON, no como serialización binaria de Java: es
     * lo que permite que el revision-service (Python) y el futuro
     * simulacros-service (otra tecnología) los entiendan.
     */
    @Bean
    public MessageConverter conversorJson() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Fábrica de contenedores para el consumidor, que le entrega el mensaje
     * <strong>crudo</strong>.
     *
     * <p>Hace falta porque el {@code JacksonJsonMessageConverter} de arriba, al
     * ser un bean, lo aplica Spring Boot también a los listeners. Con él puesto,
     * un mensaje con JSON mal formado revienta <em>antes</em> de llegar al
     * método del consumidor: el error sale como un stack trace de Jackson en vez
     * del log en español del consumidor, y el mensaje no llega a la DLQ por el
     * camino previsto.
     *
     * <p>Con {@code SimpleMessageConverter} el cuerpo llega tal cual y es el
     * consumidor quien decide qué hacer: interpretarlo, o registrarlo y mandarlo
     * a la DLQ.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory fabricaEventosCrudos(
            SimpleRabbitListenerContainerFactoryConfigurer configurador,
            ConnectionFactory fabricaConexiones) {
        SimpleRabbitListenerContainerFactory fabrica = new SimpleRabbitListenerContainerFactory();
        // El configurador aplica lo que venga de application.yml (ACK manual y
        // prefetch) para no tener que repetirlo aquí.
        configurador.configure(fabrica, fabricaConexiones);
        fabrica.setMessageConverter(new SimpleMessageConverter());
        return fabrica;
    }

    /**
     * Plantilla de publicación con confirmaciones del broker activadas.
     *
     * <p>Sin {@code mandatory}, una routing key mal escrita haría que el mensaje
     * se descartara en silencio y nadie se enteraría hasta que alguien notara que
     * faltan revisiones.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory fabricaConexiones,
                                         MessageConverter conversorJson,
                                         @Value("${spring.rabbitmq.template.mandatory:true}")
                                         boolean mandatory) {
        RabbitTemplate plantilla = new RabbitTemplate(fabricaConexiones);
        plantilla.setMessageConverter(conversorJson);
        plantilla.setExchange(EXCHANGE_EVENTOS);
        plantilla.setMandatory(mandatory);
        return plantilla;
    }
}
