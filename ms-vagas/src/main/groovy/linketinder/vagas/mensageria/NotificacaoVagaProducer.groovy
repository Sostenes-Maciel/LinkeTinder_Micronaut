package linketinder.vagas.mensageria

import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires

@KafkaClient
@Requires(property = 'kafka.enabled', notEquals = 'false')
interface NotificacaoVagaProducer {

    @Topic('${kafka.topics.novas-vagas:linketinder.vagas.criadas}')
    void enviarNovaVaga(Map<String, Object> eventoVaga)
}