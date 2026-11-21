package br.com.zg.acelera.cadastro.controller

import br.com.zg.acelera.cadastro.model.CadastroInvalidoException
import br.com.zg.acelera.cadastro.model.ServicoCadastro
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.sql.SQLException

@MicronautTest
class CadastroApiSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient cliente

    @Inject
    ServicoCadastro servico


    @MockBean(ServicoCadastro)
    ServicoCadastro mockServicoCadastro() {
        Mock(ServicoCadastro)
    }


    def "POST candidatos deve cadastrar candidato e retornar 201"() {

        given:
        Map dados = dadosCandidato()

        when:
        HttpResponse<?> resposta = executar(
                postJson('/api/candidatos', dados)
        )

        then:
        1 * servico.cadastrarCandidato({
            it.cpf == '12345678900' &&
                    it.nome == 'Ana Silva' &&
                    it.email == 'ana@email.com' &&
                    it.idade == 25
        }) >> [
                tipo        : 'candidato',
                identificador: '12345678900'
        ]

        resposta.status.code == 201

        mapa(resposta) == [
                tipo        : 'candidato',
                identificador: '12345678900'
        ]
    }


    def "POST empresas deve cadastrar empresa e retornar 201"() {

        given:
        Map dados = dadosEmpresa()

        when:
        HttpResponse<?> resposta = executar(
                postJson('/api/empresas', dados)
        )

        then:
        1 * servico.cadastrarEmpresa({
            it.cnpj == '12345678000199' &&
                    it.nome == 'Empresa de Tecnologia' &&
                    it.email == 'contato@empresa.com'
        }) >> [
                tipo        : 'empresa',
                identificador: '12345678000199'
        ]

        resposta.status.code == 201

        mapa(resposta) == [
                tipo        : 'empresa',
                identificador: '12345678000199'
        ]
    }


    def "POST candidatos deve encaminhar mapa vazio quando o corpo estiver vazio"() {

        when:
        HttpResponse<?> resposta = executar(
                postJson('/api/candidatos', [:])
        )

        then:
        1 * servico.cadastrarCandidato({ it.isEmpty() }) >>
                { throw new CadastroInvalidoException('cpf e obrigatorio e deve ser texto.') }

        resposta.status.code == 422

        mapa(resposta).mensagem ==
                'cpf e obrigatorio e deve ser texto.'
    }


    def "POST empresas deve encaminhar mapa vazio quando o corpo estiver vazio"() {

        when:
        HttpResponse<?> resposta = executar(
                postJson('/api/empresas', [:])
        )

        then:
        1 * servico.cadastrarEmpresa({ it.isEmpty() }) >>
                { throw new CadastroInvalidoException('cnpj e obrigatorio e deve ser texto.') }

        resposta.status.code == 422

        mapa(resposta).mensagem ==
                'cnpj e obrigatorio e deve ser texto.'
    }


    def "POST candidatos deve retornar 422 quando o cadastro for invalido"() {

        given:
        Map dados = dadosCandidato()

        when:
        HttpResponse<?> resposta = executar(
                postJson('/api/candidatos', dados)
        )

        then:
        1 * servico.cadastrarCandidato(_ as Map) >>
                { throw new CadastroInvalidoException('email invalido.') }

        resposta.status.code == 422

        mapa(resposta).mensagem == 'email invalido.'
    }


    def "POST empresas deve retornar 422 quando o cadastro for invalido"() {

        given:
        Map dados = dadosEmpresa()

        when:
        HttpResponse<?> resposta = executar(
                postJson('/api/empresas', dados)
        )

        then:
        1 * servico.cadastrarEmpresa(_ as Map) >>
                { throw new CadastroInvalidoException('email invalido.') }

        resposta.status.code == 422

        mapa(resposta).mensagem == 'email invalido.'
    }


    def "POST candidatos deve retornar 409 quando houver cadastro duplicado"() {

        given:
        Map dados = dadosCandidato()

        when:
        HttpResponse<?> resposta = executar(
                postJson('/api/candidatos', dados)
        )

        then:
        1 * servico.cadastrarCandidato(_ as Map) >>
                { throw new SQLException('Cadastro duplicado.', '23505') }

        resposta.status.code == 409

        mapa(resposta).mensagem ==
                'Ja existe um cadastro com esse identificador ou email.'
    }


    def "POST empresas deve retornar 409 quando houver cadastro duplicado"() {

        given:
        Map dados = dadosEmpresa()

        when:
        HttpResponse<?> resposta = executar(
                postJson('/api/empresas', dados)
        )

        then:
        1 * servico.cadastrarEmpresa(_ as Map) >>
                { throw new SQLException('Cadastro duplicado.', '23505') }

        resposta.status.code == 409

        mapa(resposta).mensagem ==
                'Ja existe um cadastro com esse identificador ou email.'
    }


    def "POST candidatos deve retornar 500 quando ocorrer erro inesperado no banco"() {

        given:
        Map dados = dadosCandidato()

        when:
        HttpResponse<?> resposta = executar(
                postJson('/api/candidatos', dados)
        )

        then:
        1 * servico.cadastrarCandidato(_ as Map) >>
                { throw new SQLException('Falha inesperada.', 'XX000') }

        resposta.status.code == 500

        mapa(resposta).mensagem ==
                'Falha ao persistir o cadastro.'
    }


    def "POST empresas deve retornar 500 quando ocorrer erro inesperado no banco"() {

        given:
        Map dados = dadosEmpresa()

        when:
        HttpResponse<?> resposta = executar(
                postJson('/api/empresas', dados)
        )

        then:
        1 * servico.cadastrarEmpresa(_ as Map) >>
                { throw new SQLException('Falha inesperada.', 'XX000') }

        resposta.status.code == 500

        mapa(resposta).mensagem ==
                'Falha ao persistir o cadastro.'
    }


    private HttpRequest<?> postJson(String caminho, Map dados) {
        return HttpRequest.POST(caminho, dados)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
    }


    private HttpResponse<?> executar(HttpRequest<?> requisicao) {
        try {
            return cliente.toBlocking().exchange(requisicao, Object)

        } catch (HttpClientResponseException excecao) {
            return excecao.response
        }
    }


    private static Map mapa(HttpResponse<?> resposta) {
        Object corpo = resposta.getBody(Object).orElse([:])

        return corpo instanceof Map
                ? (corpo as Map)
                : [:]
    }


    private static Map dadosCandidato() {
        return [
                cpf        : '12345678900',
                nome       : 'Ana Silva',
                email      : 'ana@email.com',
                pais       : 'Brasil',
                estado     : 'Pernambuco',
                cep        : '50000-000',
                descricao  : 'Desenvolvedora Java',
                idade      : 25,
                competencias: ['Java', 'Groovy']
        ]
    }


    private static Map dadosEmpresa() {
        return [
                cnpj        : '12345678000199',
                nome        : 'Empresa de Tecnologia',
                email       : 'contato@empresa.com',
                pais        : 'Brasil',
                estado      : 'Pernambuco',
                cep         : '50000-000',
                descricao   : 'Empresa de desenvolvimento',
                competencias: ['Java', 'PostgreSQL']
        ]
    }
}