package linketinder.competencias

import groovy.json.JsonSlurper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import linketinder.competencias.model.CompetenciaInvalidoException
import linketinder.competencias.model.ServicoCompetencia
import spock.lang.Specification
import io.micronaut.http.client.exceptions.HttpClientResponseException

import java.sql.SQLException

@MicronautTest
class CompetenciaApiSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient cliente

    @Inject
    ServicoCompetencia servico

    @MockBean(ServicoCompetencia)
    ServicoCompetencia mockServicoCompetencia() {
        Mock(ServicoCompetencia)
    }

    private static Object lerJson(HttpResponse<String> resposta) {
        new JsonSlurper().parseText(resposta.body())
    }

    def "GET /api/competencias deve retornar a lista de competencias"() {
        given:
        def competencias = [
                [idCompetencia: 1, nome: 'Java'],
                [idCompetencia: 2, nome: 'Groovy']
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET('/api/competencias'),
                String
        )

        then:
        1 * servico.listarCompetencias() >> competencias

        resposta.status == HttpStatus.OK
        lerJson(resposta) == competencias
    }

    def "POST /api/competencias deve cadastrar uma competencia"() {
        given:
        def dados = [nome: 'Java']
        def criada = [idCompetencia: 1, nome: 'Java']

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.POST('/api/competencias', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        1 * servico.cadastrarCompetencia({
            it.nome == 'Java'
        }) >> criada

        resposta.status == HttpStatus.CREATED
        lerJson(resposta) == criada
    }

    def "GET /api/competencias/candidatos/{cpf} deve retornar competencias do candidato"() {
        given:
        String cpf = '12345678901'
        def competencias = [
                [idCompetencia: 1, nome: 'Java'],
                [idCompetencia: 2, nome: 'SQL']
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET("/api/competencias/candidatos/${cpf}"),
                String
        )

        then:
        1 * servico.listarPorCandidato(cpf) >> competencias

        resposta.status == HttpStatus.OK
        lerJson(resposta) == competencias
    }

    def "POST /api/competencias/candidatos/consulta-lote deve retornar competencias de varios CPFs"() {
        given:
        def dados = [
                cpfs: ['12345678901', '98765432100']
        ]

        def resultado = [
                '12345678901': [
                        [idCompetencia: 1, nome: 'Java']
                ],
                '98765432100': [
                        [idCompetencia: 2, nome: 'Python']
                ]
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.POST(
                        '/api/competencias/candidatos/consulta-lote',
                        dados
                ).contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        1 * servico.listarPorCandidatos({
            it.cpfs == dados.cpfs
        }) >> resultado

        resposta.status == HttpStatus.OK
        lerJson(resposta) == resultado
    }

    def "POST /api/competencias/candidatos deve vincular competencia ao candidato"() {
        given:
        def dados = [
                cpfCandidato: '12345678901',
                nome: 'Java'
        ]

        def resultado = [
                cpfCandidato: '12345678901',
                competencia: 'Java'
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.POST('/api/competencias/candidatos', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        1 * servico.vincularCandidato({
            it.cpfCandidato == '12345678901' &&
                    it.nome == 'Java'
        }) >> resultado

        resposta.status == HttpStatus.CREATED
        lerJson(resposta) == resultado
    }

    def "GET /api/competencias/empresas/{cnpj} deve retornar competencias da empresa"() {
        given:
        String cnpj = '12345678000199'
        def competencias = [
                [idCompetencia: 1, nome: 'Java'],
                [idCompetencia: 2, nome: 'Micronaut']
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET("/api/competencias/empresas/${cnpj}"),
                String
        )

        then:
        1 * servico.listarPorEmpresa(cnpj) >> competencias

        resposta.status == HttpStatus.OK
        lerJson(resposta) == competencias
    }

    def "POST /api/competencias/empresas deve vincular competencia a empresa"() {
        given:
        def dados = [
                cnpjEmpresa: '12345678000199',
                nome: 'Java'
        ]

        def resultado = [
                cnpjEmpresa: '12345678000199',
                competencia: 'Java'
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.POST('/api/competencias/empresas', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        1 * servico.vincularEmpresa({
            it.cnpjEmpresa == '12345678000199' &&
                    it.nome == 'Java'
        }) >> resultado

        resposta.status == HttpStatus.CREATED
        lerJson(resposta) == resultado
    }

    def "GET /api/competencias/vagas/{idVaga} deve retornar competencias da vaga"() {
        given:
        int idVaga = 10
        def competencias = [
                [idCompetencia: 1, nome: 'Java'],
                [idCompetencia: 2, nome: 'PostgreSQL']
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET("/api/competencias/vagas/${idVaga}"),
                String
        )

        then:
        1 * servico.listarPorVaga(idVaga) >> competencias

        resposta.status == HttpStatus.OK
        lerJson(resposta) == competencias
    }

    def "POST /api/competencias/vagas deve vincular competencia a vaga"() {
        given:
        def dados = [
                idVaga: 10,
                nome: 'Java'
        ]

        def resultado = [
                idVaga: 10,
                competencia: 'Java'
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.POST('/api/competencias/vagas', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        1 * servico.vincularVaga({
            it.idVaga == 10 &&
                    it.nome == 'Java'
        }) >> resultado

        resposta.status == HttpStatus.CREATED
        lerJson(resposta) == resultado
    }

    def "deve retornar HTTP 422 quando o servico rejeitar os dados"() {
        when:
        cliente.toBlocking().exchange(
                HttpRequest.GET('/api/competencias/vagas/0'),
                String
        )

        then:
        1 * servico.listarPorVaga(0) >> {
            throw new CompetenciaInvalidoException(
                    'idVaga deve ser maior que zero.'
            )
        }

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.UNPROCESSABLE_ENTITY

        def corpo = new JsonSlurper().parseText(
                excecao.response.getBody(String).orElseThrow()
        )

        corpo.mensagem == 'idVaga deve ser maior que zero.'
    }

    def "deve retornar HTTP 409 quando ocorrer duplicidade no banco"() {
        when:
        cliente.toBlocking().exchange(
                HttpRequest.POST(
                        '/api/competencias',
                        [nome: 'Java']
                ).contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        1 * servico.cadastrarCompetencia(_ as Map) >> {
            throw new SQLException('Registro duplicado.', '23505')
        }

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.CONFLICT

        def corpo = new JsonSlurper().parseText(
                excecao.response.getBody(String).orElseThrow()
        )

        corpo.mensagem == 'Registro duplicado de competencia ou vinculo.'
    }

    def "deve retornar HTTP 500 para uma falha SQL inesperada"() {
        when:
        cliente.toBlocking().exchange(
                HttpRequest.POST(
                        '/api/competencias',
                        [nome: 'Java']
                ).contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        1 * servico.cadastrarCompetencia(_ as Map) >> {
            throw new SQLException('Falha inesperada.', '08006')
        }

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.INTERNAL_SERVER_ERROR

        def corpo = new JsonSlurper().parseText(
                excecao.response.getBody(String).orElseThrow()
        )

        corpo.mensagem == 'Falha ao persistir competencia no banco.'
    }
}