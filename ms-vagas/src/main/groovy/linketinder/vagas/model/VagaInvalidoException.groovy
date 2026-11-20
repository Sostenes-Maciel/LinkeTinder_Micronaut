package linketinder.vagas.model

class VagaInvalidoException extends RuntimeException {
    VagaInvalidoException(String mensagem) {
        super(mensagem)
    }
}