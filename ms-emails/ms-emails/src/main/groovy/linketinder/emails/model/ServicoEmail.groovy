// Servico responsavel por montar e enviar notificacoes por e-mail sobre novas vagas.
package linketinder.emails.model

import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.email.Email
import io.micronaut.email.EmailSender
import jakarta.inject.Singleton

@Slf4j
@Singleton
class ServicoEmail {

    private final RepositorioCandidato repositorioCandidato
    private final EmailSender<?, ?> emailSender
    private final String remetente
    private final boolean modoSimulacao

    ServicoEmail(
            RepositorioCandidato repositorioCandidato,
            EmailSender<?, ?> emailSender,
            @Value('${micronaut.email.from.email:no-reply@linketinder.com}') String remetente,
            @Value('${email.modo-simulacao:false}') boolean modoSimulacao
    ) {
        this.repositorioCandidato = repositorioCandidato
        this.emailSender = emailSender
        this.remetente = remetente
        this.modoSimulacao = modoSimulacao
    }

    int notificarCandidatosSobreNovaVaga(Map<String, Object> eventoVaga) {
        if (!eventoVaga) {
            log.warn('Evento de vaga vazio recebido; nenhum e-mail sera enviado.')
            return 0
        }

        List<String> candidatos = repositorioCandidato.listarCandidatosComEmail()

        if (candidatos.isEmpty()) {
            log.info('Nenhum candidato com e-mail cadastrado para notificar.')
            return 0
        }

        String nomeVaga = (eventoVaga.nome ?: eventoVaga.titulo ?: 'Nova oportunidade').toString()
        String assunto = "Linketinder: Nova vaga disponivel - ${nomeVaga}"
        int enviados = 0

        candidatos.each { Map<String, String> candidato ->
            String destinatario = candidato.email
            if (!destinatario) {
                return
            }

            String corpo = montarMensagemPadrao(candidato.nome, eventoVaga)

            try {
                if (modoSimulacao) {
                    log.info('[SIMULACAO E-MAIL] Para: {} | Assunto: {}\n{}', destinatario, assunto, corpo)
                } else {
                    emailSender.send(
                            Email.builder()
                                    .from(remetente)
                                    .to(destinatario)
                                    .subject(assunto)
                                    .body(corpo)
                    )
                    log.info('E-mail de nova vaga enviado para {} ({})', candidato.nome, destinatario)
                }
                enviados++
            } catch (Exception e) {
                log.error('Falha ao enviar e-mail para {} ({}): {}', candidato.nome, destinatario, e.message)
            }
        }

        return enviados
    }

    String montarMensagemPadrao(String nomeCandidato, Map<String, Object> eventoVaga) {
        String saudacao = nomeCandidato?.trim() ? "Ola, ${nomeCandidato.trim()}!" : 'Ola!'
        String nomeVaga = (eventoVaga.nome ?: eventoVaga.titulo ?: 'Nao informado').toString()
        String descricao = (eventoVaga.descricao ?: 'Nao informada').toString()
        String local = (eventoVaga.local ?: 'Nao informado').toString()
        String dataHora = (eventoVaga.dataHoraCriacao ?: 'Agora').toString()

        List<String> listaCompetencias = eventoVaga.competencias instanceof Collection
                ? ((Collection) eventoVaga.competencias).collect { it.toString() }
                : []
        String competenciasFormatadas = listaCompetencias.isEmpty()
                ? 'Nao especificadas'
                : listaCompetencias.join(', ')

        return """${saudacao}

Uma nova vaga acabou de ser cadastrada no Linketinder e pode combinar com o seu perfil!

Detalhes da vaga:
- Vaga: ${nomeVaga}
- Descricao: ${descricao}
- Competencias: ${competenciasFormatadas}
- Local: ${local}
- Data e hora de criacao: ${dataHora}

Acesse o Linketinder para conferir mais detalhes e demonstrar interesse.

Atenciosamente,
Equipe Linketinder"""
    }
}