// Contrato de acesso aos dados de candidatos para notificacao por e-mail.
package linketinder.emails.model

interface RepositorioCandidato {

    List<Map> listarCandidatosComEmail()
}