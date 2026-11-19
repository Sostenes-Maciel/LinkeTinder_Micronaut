package br.com.zg.acelera.cadastro.model

class CadastroInvalidoException extends RuntimeException {
    CadastroInvalidoException(String mensagem) {
        super(mensagem)
    }
}
