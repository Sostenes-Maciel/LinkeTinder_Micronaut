package linketinder.emails.mensageria

import groovy.util.logging.Slf4j
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import linketinder.emails.model.ServicoEmail

@Slf4j
@KafkaListener(groupId = 'ms-emails-group', offsetReset = OffsetReset.EARLIEST)
@Requires(property = 'kafka.enabled', notEquals = 'false')
class NotificacaoVagaConsumer {

    private final ServicoEmail servicoEmail

    NotificacaoVagaConsumer(ServicoEmail servicoEmail) {
        this.servicoEmail = servicoEmail
    }

    @Topic('${kafka.topics.novas-vagas:linketinder.vagas.criadas}')
    void receberNovaVaga(Map<String, Object> eventoVaga) {
        log.info('Evento de nova vaga recebido do Kafka: {}', eventoVaga?.nome ?: eventoVaga?.titulo)
        int totalEnviados = servicoEmail.notificarCandidatosSobreNovaVaga(eventoVaga)
        log.info('Notificacoes finalizadas. Total de e-mails processados: {}', totalEnviados)
    }
}