package linketinder.curtidasmatches.controller

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import linketinder.curtidasmatches.model.CurtidaMatchInvalidoException
import linketinder.curtidasmatches.model.ServicoCurtidaMatch
import spock.lang.Specification

import java.sql.SQLException

@MicronautTest
class CurtidaMatchApiSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient cliente

    @Inject
    ServicoCurtidaMatch servico

    @MockBean(ServicoCurtidaMatch)
    ServicoCurtidaMatch mockServicoCurtidaMatch() {
        Mock(ServicoCurtidaMatch)
    }

    private static HttpRequest<?> requisicaoDelete(Map dados) {
        return HttpRequest.create(
                HttpMethod.DELETE,
                '/api/matches'
        )
                .contentType(MediaType.APPLICATION_JSON)
                .body(dados)
    }

    private static Map corpoErro(HttpClientResponseException excecao) {
        return excecao.response.getBody(Map).orElseThrow() as Map
    }

    private Map<String, Object> dadosCurtidaCandidato() {
        return [
                autor       : 'CANDIDATO',
                cpfCandidato: '12345678901',
                idVaga      : 10
        ]
    }

    private Map<String, Object> dadosCurtidaEmpresa() {
        return [
                autor       : 'EMPRESA',
                cpfCandidato: '12345678901',
                cnpjEmpresa : '12345678000199',
                idVaga      : 10
        ]
    }

    // ---------------------------------------------------------
    // GET /api/curtidas
    // ---------------------------------------------------------

    def "GET deve listar todas as curtidas"() {
        given:
        def curtidas = [
                [idCurtida: 1, cpfCandidato: '12345678901', idVaga: 10],
                [idCurtida: 2, cpfCandidato: '98765432100', idVaga: 20]
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET('/api/curtidas'),
                Object
        )

        then:
        1 * servico.listarCurtidas() >> curtidas

        resposta.status == HttpStatus.OK
        resposta.body() == curtidas
    }

    // ---------------------------------------------------------
    // GET /api/matches
    // ---------------------------------------------------------

    def "GET sem filtros deve listar todos os matches"() {
        given:
        def matches = [
                [idMatch: 1, cpfCandidato: '12345678901', idVaga: 10]
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET('/api/matches'),
                Object
        )

        then:
        1 * servico.listarMatches(null, null) >> matches

        resposta.status == HttpStatus.OK
        resposta.body() == matches
    }

    def "GET com CPF deve consultar status de curtidas do candidato"() {
        given:
        String cpf = '12345678901'

        def status = [
                [idVaga: 10, status: 'curtido'],
                [idVaga: 20, status: 'match']
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET('/api/matches?cpf=' + cpf),
                Object
        )

        then:
        1 * servico.listarStatusCurtidasCandidato(cpf) >> status

        resposta.status == HttpStatus.OK
        resposta.body() == status
    }

    def "GET com CNPJ deve consultar status de curtidas da empresa"() {
        given:
        String cnpj = '12345678000199'

        def status = [
                [cpfCandidato: '12345678901', idVaga: 10, status: 'curtido']
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET('/api/matches?cnpj=' + cnpj),
                Object
        )

        then:
        1 * servico.listarStatusCurtidasEmpresa(cnpj) >> status

        resposta.status == HttpStatus.OK
        resposta.body() == status
    }

    def "GET completo com CPF deve retornar matches e curtidas pendentes do candidato"() {
        given:
        String cpf = '12345678901'

        def resultado = [
                matches: [
                        [idMatch: 1, empresa: [nome: 'Empresa A']]
                ],
                curtidasPendentes: [
                        [vaga: [id: 10, titulo: 'Desenvolvedor Java']]
                ]
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET(
                        '/api/matches?cpf=' + cpf + '&completo=true'
                ),
                Object
        )

        then:
        1 * servico.listarMatchesCompletosCandidato(cpf) >> resultado

        resposta.status == HttpStatus.OK
        resposta.body() == resultado
    }

    def "GET completo com CNPJ deve retornar matches e curtidas pendentes da empresa"() {
        given:
        String cnpj = '12345678000199'

        def resultado = [
                matches: [
                        [idMatch: 1, candidato: [nome: 'Candidato A']]
                ],
                curtidasPendentes: [
                        [vaga: [id: 10, titulo: 'Desenvolvedor Java']]
                ]
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET(
                        '/api/matches?cnpj=' + cnpj + '&completo=true'
                ),
                Object
        )

        then:
        1 * servico.listarMatchesCompletosEmpresa(cnpj) >> resultado

        resposta.status == HttpStatus.OK
        resposta.body() == resultado
    }

    // ---------------------------------------------------------
    // POST /api/matches
    // ---------------------------------------------------------

    def "POST de candidato deve registrar curtida sem CNPJ e sem recrutador"() {
        given:
        def dados = dadosCurtidaCandidato()
        dados.cnpjEmpresa = '12345678000199'
        dados.recrutadorId = 5

        def registrada = [
                idCurtida   : 1,
                cpfCandidato: '12345678901',
                idVaga      : 10,
                cnpjEmpresa : null,
                recrutadorId: null,
                match       : false
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.POST('/api/matches', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                Object
        )

        then:
        1 * servico.registrarCurtida({
            it.autor == 'CANDIDATO' &&
                    it.cpfCandidato == '12345678901' &&
                    it.idVaga == 10 &&
                    it.cnpjEmpresa == null &&
                    !it.containsKey('recrutadorId')
        }) >> registrada

        resposta.status == HttpStatus.CREATED

        resposta.body() == [
                mensagem: 'Curtida registrada com sucesso!',
                match   : false,
                dados   : registrada
        ]
    }

    def "POST de empresa deve registrar curtida e remover recrutador do payload"() {
        given:
        def dados = dadosCurtidaEmpresa()
        dados.recrutadorId = 9

        def registrada = [
                idCurtida   : 2,
                cpfCandidato: '12345678901',
                cnpjEmpresa : '12345678000199',
                idVaga      : 10,
                recrutadorId: null,
                match       : false
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.POST('/api/matches', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                Object
        )

        then:
        1 * servico.registrarCurtida({
            it.autor == 'EMPRESA' &&
                    it.cpfCandidato == '12345678901' &&
                    it.cnpjEmpresa == '12345678000199' &&
                    it.idVaga == 10 &&
                    !it.containsKey('recrutadorId')
        }) >> registrada

        resposta.status == HttpStatus.CREATED
        resposta.body().match == false
    }

    def "POST deve informar quando uma curtida gerar match"() {
        given:
        def dados = dadosCurtidaEmpresa()

        def registrada = [
                idCurtida   : 2,
                cpfCandidato: '12345678901',
                cnpjEmpresa : '12345678000199',
                idVaga      : 10,
                recrutadorId: null,
                match       : true
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.POST('/api/matches', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                Object
        )

        then:
        1 * servico.registrarCurtida(_ as Map) >> registrada

        resposta.status == HttpStatus.CREATED
        resposta.body().mensagem == 'MATCH ENCONTRADO!'
        resposta.body().match == true
    }

    // ---------------------------------------------------------
    // POST /api/curtidas
    // ---------------------------------------------------------

    def "POST de curtidas deve utilizar o mesmo fluxo de registro"() {
        given:
        def dados = dadosCurtidaCandidato()

        def registrada = [
                idCurtida   : 3,
                cpfCandidato: '12345678901',
                idVaga      : 10,
                cnpjEmpresa : null,
                recrutadorId: null,
                match       : false
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.POST('/api/curtidas', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                Object
        )

        then:
        1 * servico.registrarCurtida(_ as Map) >> registrada

        resposta.status == HttpStatus.CREATED
        resposta.body().dados == registrada
    }

    def "POST deve rejeitar autor diferente de candidato ou empresa"() {
        given:
        def dados = [
                autor       : 'RECRUTADOR',
                cpfCandidato: '12345678901',
                idVaga      : 10
        ]

        when:
        cliente.toBlocking().exchange(
                HttpRequest.POST('/api/matches', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                Object
        )

        then:
        0 * servico.registrarCurtida(_)

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.UNPROCESSABLE_ENTITY
        corpoErro(excecao).mensagem.contains('autor válido')
    }

    // ---------------------------------------------------------
    // DELETE /api/matches
    // ---------------------------------------------------------

    def "DELETE de candidato deve remover curtida utilizando CPF normalizado"() {
        given:
        def dados = [
                autor       : 'CANDIDATO',
                cpfCandidato: '123.456.789-01',
                idVaga      : 10
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                requisicaoDelete(dados),
                Object
        )

        then:
        1 * servico.removerCurtidaCandidato('12345678901', 10) >> true

        resposta.status == HttpStatus.OK
        resposta.body().mensagem == 'Curtida removida com sucesso.'
    }

    def "DELETE de empresa deve remover curtida utilizando CPF e CNPJ normalizados"() {
        given:
        def dados = [
                autor       : 'EMPRESA',
                cpfCandidato: '123.456.789-01',
                cnpjEmpresa : '12.345.678/0001-99',
                idVaga      : 10
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                requisicaoDelete(dados),
                Object
        )

        then:
        1 * servico.removerCurtidaEmpresa(
                '12345678000199',
                '12345678901',
                10
        ) >> true

        resposta.status == HttpStatus.OK
        resposta.body().mensagem == 'Curtida removida com sucesso.'
    }

    def "DELETE deve retornar HTTP 400 quando nao for possivel remover a curtida"() {
        given:
        def dados = [
                autor       : 'CANDIDATO',
                cpfCandidato: '12345678901',
                idVaga      : 10
        ]

        when:
        cliente.toBlocking().exchange(
                requisicaoDelete(dados),
                Object
        )

        then:
        1 * servico.removerCurtidaCandidato('12345678901', 10) >> false

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.BAD_REQUEST
        corpoErro(excecao).mensagem.contains('Não foi possível remover')
    }

    def "DELETE deve rejeitar solicitacao sem CPF ou ID da vaga"() {
        when:
        cliente.toBlocking().exchange(
                requisicaoDelete([
                        autor: 'CANDIDATO'
                ]),
                Object
        )

        then:
        0 * servico.removerCurtidaCandidato(_, _)

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.UNPROCESSABLE_ENTITY
        corpoErro(excecao).mensagem.contains('obrigatórios')
    }

    def "DELETE de empresa deve exigir CNPJ"() {
        when:
        cliente.toBlocking().exchange(
                requisicaoDelete([
                        autor       : 'EMPRESA',
                        cpfCandidato: '12345678901',
                        idVaga      : 10
                ]),
                Object
        )

        then:
        0 * servico.removerCurtidaEmpresa(_, _, _)

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.UNPROCESSABLE_ENTITY
        corpoErro(excecao).mensagem.contains('CNPJ')
    }

    // ---------------------------------------------------------
    // TRATAMENTO DE ERROS
    // ---------------------------------------------------------

    def "deve retornar HTTP 422 para CurtidaMatchInvalidoException"() {
        when:
        cliente.toBlocking().exchange(
                HttpRequest.POST(
                        '/api/matches',
                        dadosCurtidaCandidato()
                ).contentType(MediaType.APPLICATION_JSON),
                Object
        )

        then:
        1 * servico.registrarCurtida(_ as Map) >> {
            throw new CurtidaMatchInvalidoException('Dados inválidos.')
        }

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.UNPROCESSABLE_ENTITY
        corpoErro(excecao).mensagem == 'Dados inválidos.'
    }

    def "deve retornar HTTP 409 para duplicidade no banco"() {
        when:
        cliente.toBlocking().exchange(
                HttpRequest.POST(
                        '/api/matches',
                        dadosCurtidaCandidato()
                ).contentType(MediaType.APPLICATION_JSON),
                Object
        )

        then:
        1 * servico.registrarCurtida(_ as Map) >> {
            throw new SQLException('Registro duplicado.', '23505')
        }

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.CONFLICT
        corpoErro(excecao).mensagem ==
                'Registro duplicado em curtida ou match_vaga.'
    }

    def "deve retornar HTTP 422 para chave estrangeira invalida"() {
        when:
        cliente.toBlocking().exchange(
                HttpRequest.POST(
                        '/api/matches',
                        dadosCurtidaCandidato()
                ).contentType(MediaType.APPLICATION_JSON),
                Object
        )

        then:
        1 * servico.registrarCurtida(_ as Map) >> {
            throw new SQLException('Chave estrangeira inválida.', '23503')
        }

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.UNPROCESSABLE_ENTITY
        corpoErro(excecao).mensagem.contains('Chave estrangeira')
    }

    def "deve retornar HTTP 500 para falha SQL inesperada"() {
        when:
        cliente.toBlocking().exchange(
                HttpRequest.POST(
                        '/api/matches',
                        dadosCurtidaCandidato()
                ).contentType(MediaType.APPLICATION_JSON),
                Object
        )

        then:
        1 * servico.registrarCurtida(_ as Map) >> {
            throw new SQLException('Falha inesperada.', '08006')
        }

        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.INTERNAL_SERVER_ERROR
        corpoErro(excecao).mensagem ==
                'Falha ao persistir curtida ou match no banco.'
    }
}