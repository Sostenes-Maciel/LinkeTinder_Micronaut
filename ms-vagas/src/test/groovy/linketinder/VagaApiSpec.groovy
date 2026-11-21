package linketinder.vagas.controller

import groovy.json.JsonSlurper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import linketinder.vagas.model.RepositorioVaga
import spock.lang.Specification

import java.sql.SQLException

@MicronautTest
class VagaApiSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient cliente

    @Inject
    RepositorioVaga repositorio

    @MockBean(RepositorioVaga)
    RepositorioVaga mockRepositorio() {
        Mock(RepositorioVaga)
    }

    private static Object lerJson(String corpo) {
        new JsonSlurper().parseText(corpo)
    }

    private Map<String, Object> dadosCadastro(Map adicionais = [:]) {
        Map<String, Object> dados = [
                cnpjEmpresa : '12345678000199',
                titulo      : 'Desenvolvedor Java',
                pais        : 'Brasil',
                estado      : 'Pernambuco',
                descricao   : 'Desenvolvimento de aplicações.',
                areaAtuacao : 'TI',
                competencias: ['Java', 'Groovy']
        ]

        dados.putAll(adicionais)
        return dados
    }

    private Map<String, Object> dadosAtualizacao(Map adicionais = [:]) {
        Map<String, Object> dados = [
                titulo     : 'Desenvolvedor Backend',
                pais       : 'Brasil',
                estado     : 'Pernambuco',
                descricao  : 'Desenvolvimento de sistemas.',
                areaAtuacao: 'TI'
        ]

        dados.putAll(adicionais)
        return dados
    }

    // ---------------------------------------------------------
    // GET /api/vagas
    // ---------------------------------------------------------

    def "GET deve retornar todas as vagas"() {
        given:
        def vagas = [
                [idVaga: 1, titulo: 'Desenvolvedor Java'],
                [idVaga: 2, titulo: 'Analista de Sistemas']
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET('/api/vagas'),
                String
        )

        then:
        1 * repositorio.listarVagas() >> vagas

        resposta.status == HttpStatus.OK
        lerJson(resposta.body()) == vagas
    }

    // ---------------------------------------------------------
    // GET /api/vagas/{idVaga}
    // ---------------------------------------------------------

    def "GET por ID deve retornar uma vaga"() {
        given:
        int idVaga = 10

        def vaga = [
                idVaga: 10,
                titulo: 'Desenvolvedor Java'
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET("/api/vagas/${idVaga}"),
                String
        )

        then:
        1 * repositorio.buscarPorId(idVaga) >> vaga

        resposta.status == HttpStatus.OK
        lerJson(resposta.body()) == vaga
    }

    def "GET por ID deve retornar HTTP 422 quando a vaga nao existir"() {
        when:
        cliente.toBlocking().exchange(
                HttpRequest.GET('/api/vagas/999'),
                String
        )

        then:
        1 * repositorio.buscarPorId(999) >> null

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.UNPROCESSABLE_ENTITY

        def corpo = lerJson(
                excecao.response.getBody(String).orElseThrow()
        )

        corpo.mensagem.contains('nao encontrada')
    }

    // ---------------------------------------------------------
    // GET /api/vagas/empresas/{cnpj}
    // ---------------------------------------------------------

    def "GET por empresa deve retornar as vagas do CNPJ informado"() {
        given:
        String cnpj = '12345678000199'

        def vagas = [
                [idVaga: 1, titulo: 'Desenvolvedor Java']
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET("/api/vagas/empresas/${cnpj}"),
                String
        )

        then:
        1 * repositorio.listarPorEmpresa(cnpj) >> vagas

        resposta.status == HttpStatus.OK
        lerJson(resposta.body()) == vagas
    }

    // ---------------------------------------------------------
    // POST /api/vagas
    // ---------------------------------------------------------

    def "POST deve cadastrar uma vaga"() {
        given:
        def dados = dadosCadastro()

        def vagaCriada = [
                idVaga      : 10,
                titulo      : 'Desenvolvedor Java',
                pais        : 'Brasil',
                estado      : 'Pernambuco',
                competencias: ['Java', 'Groovy']
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.POST('/api/vagas', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        1 * repositorio.inserirVaga({ Map recebidos ->
            recebidos.cnpjEmpresa == '12345678000199' &&
                    recebidos.titulo == 'Desenvolvedor Java' &&
                    recebidos.competencias == ['Java', 'Groovy']
        }) >> vagaCriada

        resposta.status == HttpStatus.CREATED
        lerJson(resposta.body()) == vagaCriada
    }

    def "POST deve retornar HTTP 422 quando a vaga nao possuir competencias"() {
        given:
        def dados = dadosCadastro([
                competencias: []
        ])

        when:
        cliente.toBlocking().exchange(
                HttpRequest.POST('/api/vagas', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        0 * repositorio.inserirVaga(_)

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.UNPROCESSABLE_ENTITY

        def corpo = lerJson(
                excecao.response.getBody(String).orElseThrow()
        )

        corpo.mensagem.contains('competencia')
    }

    // ---------------------------------------------------------
    // PUT /api/vagas/{idVaga}
    // ---------------------------------------------------------

    def "PUT deve atualizar uma vaga"() {
        given:
        int idVaga = 10
        def dados = dadosAtualizacao([
                competencias: ['Java', 'Python']
        ])

        def vagaAtualizada = [
                idVaga: 10,
                titulo: 'Desenvolvedor Backend',
                competencias: ['Java', 'Python']
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.PUT("/api/vagas/${idVaga}", dados)
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        1 * repositorio.atualizarVaga(idVaga, { Map recebidos ->
            recebidos.titulo == 'Desenvolvedor Backend' &&
                    recebidos.competencias == ['Java', 'Python']
        }) >> vagaAtualizada

        resposta.status == HttpStatus.OK
        lerJson(resposta.body()) == vagaAtualizada
    }

    def "PUT deve retornar HTTP 422 quando a atualizacao remover todas as competencias"() {
        given:
        def dados = dadosAtualizacao([
                competencias: []
        ])

        when:
        cliente.toBlocking().exchange(
                HttpRequest.PUT('/api/vagas/10', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        0 * repositorio.atualizarVaga(_, _)

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.UNPROCESSABLE_ENTITY
    }

    // ---------------------------------------------------------
    // DELETE /api/vagas/{idVaga}
    // ---------------------------------------------------------

    def "DELETE deve excluir uma vaga e retornar HTTP 204"() {
        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.DELETE('/api/vagas/10')
        )

        then:
        1 * repositorio.excluirVaga(10) >> true

        resposta.status == HttpStatus.NO_CONTENT
    }

    // ---------------------------------------------------------
    // TRATAMENTO DE ERROS SQL
    // ---------------------------------------------------------

    def "deve retornar HTTP 409 quando ocorrer duplicidade no banco"() {
        when:
        cliente.toBlocking().exchange(
                HttpRequest.POST('/api/vagas', dadosCadastro())
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        1 * repositorio.inserirVaga(_ as Map) >> {
            throw new SQLException('Registro duplicado.', '23505')
        }

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.CONFLICT

        def corpo = lerJson(
                excecao.response.getBody(String).orElseThrow()
        )

        corpo.mensagem == 'Ja existe uma vaga com esses dados.'
    }

    def "deve retornar HTTP 422 quando a empresa vinculada nao existir"() {
        when:
        cliente.toBlocking().exchange(
                HttpRequest.POST('/api/vagas', dadosCadastro())
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        1 * repositorio.inserirVaga(_ as Map) >> {
            throw new SQLException('Chave estrangeira invalida.', '23503')
        }

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.UNPROCESSABLE_ENTITY

        def corpo = lerJson(
                excecao.response.getBody(String).orElseThrow()
        )

        corpo.mensagem.contains('Empresa informada')
    }

    def "deve retornar HTTP 500 para uma falha SQL inesperada"() {
        when:
        cliente.toBlocking().exchange(
                HttpRequest.POST('/api/vagas', dadosCadastro())
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        1 * repositorio.inserirVaga(_ as Map) >> {
            throw new SQLException('Falha inesperada.', '08006')
        }

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.INTERNAL_SERVER_ERROR

        def corpo = lerJson(
                excecao.response.getBody(String).orElseThrow()
        )

        corpo.mensagem == 'Falha ao persistir vaga no banco.'
    }
}