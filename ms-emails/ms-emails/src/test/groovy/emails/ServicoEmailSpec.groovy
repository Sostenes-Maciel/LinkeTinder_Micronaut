// Testes unitarios do servico de montagem e disparo de e-mails de novas vagas.
package linketinder.emails

import io.micronaut.email.EmailSender
import linketinder.emails.model.RepositorioCandidato
import linketinder.emails.model.ServicoEmail
import spock.lang.Specification

class ServicoEmailSpec extends Specification {

    RepositorioCandidato repositorioCandidato = Mock()
    EmailSender emailSender = Mock()

    def "deve formatar a mensagem padrao com todos os dados da vaga"() {
        given:
        def servico = new ServicoEmail(repositorioCandidato, emailSender, 'no-reply@linketinder.com', false)
        def evento = [
                nome           : 'Desenvolvedor Groovy',
                descricao      : 'Atuacao em microsservicos Micronaut.',
                competencias   : ['Groovy', 'Kafka', 'PostgreSQL'],
                local          : 'Pernambuco - Brasil',
                dataHoraCriacao: '26/11/2026 13:00:00'
        ] as Map<String, Object>

        when:
        String mensagem = servico.montarMensagemPadrao('Sostenes Maciel', evento)

        then:
        mensagem.contains('Ola, Sostenes Maciel!')
        mensagem.contains('Desenvolvedor Groovy')
        mensagem.contains('Atuacao em microsservicos Micronaut.')
        mensagem.contains('Groovy, Kafka, PostgreSQL')
        mensagem.contains('Pernambuco - Brasil')
        mensagem.contains('26/11/2026 13:00:00')
    }

    def "deve enviar um e-mail para cada candidato cadastrado com e-mail"() {
        given:
        def servico = new ServicoEmail(repositorioCandidato, emailSender, 'no-reply@linketinder.com', false)
        def evento = [
                nome           : 'Engenheiro de Software',
                descricao      : 'Desenvolvimento backend.',
                competencias   : ['Java', 'Groovy'],
                local          : 'PE - Brasil',
                dataHoraCriacao: '26/11/2026 13:10:00'
        ] as Map<String, Object>

        def candidatos = [
                [cpf: '111.111.111-11', nome: 'Ana Julia', email: 'ana@teste.com'],
                [cpf: '222.222.222-22', nome: 'Jose Silva', email: 'jose@teste.com']
        ]

        when:
        int totalEnviados = servico.notificarCandidatosSobreNovaVaga(evento)

        then:
        1 * repositorioCandidato.listarCandidatosComEmail() >> candidatos
        2 * emailSender.send(_)
        totalEnviados == 2
    }

    def "nao deve enviar e-mails quando nao houver candidatos cadastrados"() {
        given:
        def servico = new ServicoEmail(repositorioCandidato, emailSender, 'no-reply@linketinder.com', false)
        def evento = [nome: 'Desenvolvedor Python'] as Map<String, Object>

        when:
        int totalEnviados = servico.notificarCandidatosSobreNovaVaga(evento)

        then:
        1 * repositorioCandidato.listarCandidatosComEmail() >> []
        0 * emailSender.send(_)
        totalEnviados == 0
    }
}