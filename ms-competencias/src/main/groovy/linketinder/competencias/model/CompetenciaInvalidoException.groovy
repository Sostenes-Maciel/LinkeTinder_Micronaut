package linketinder.competencias.model

class CompetenciaInvalidoException extends RuntimeException {
    CompetenciaInvalidoException(String mensagem) {
        super(mensagem)
    }
}