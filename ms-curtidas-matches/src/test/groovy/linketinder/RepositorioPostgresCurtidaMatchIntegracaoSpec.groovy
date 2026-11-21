package linketinder.curtidasmatches.model

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

@MicronautTest(transactional = false)
class RepositorioPostgresCurtidaMatchIntegracaoSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient cliente

    @Inject
    DataSource dataSource

    private String cpfTeste
    private String cnpjTeste
    private Integer idVagaTeste


    void setup() {
        selecionarParDisponivel()
    }


    void cleanup() {
        if (cpfTeste == null || cnpjTeste == null || idVagaTeste == null) {
            return
        }

        dataSource.connection.withCloseable { Connection conexao ->

            conexao.prepareStatement('''
                DELETE FROM match_vaga
                WHERE BTRIM(cpf_candidato) = BTRIM(?)
                  AND BTRIM(cnpj_empresa) = BTRIM(?)
                  AND id_vaga = ?
            ''').withCloseable { PreparedStatement instrucao ->

                instrucao.setString(1, cpfTeste)
                instrucao.setString(2, cnpjTeste)
                instrucao.setInt(3, idVagaTeste)

                instrucao.executeUpdate()
            }

            conexao.prepareStatement('''
                DELETE FROM curtida
                WHERE BTRIM(cpf_candidato) = BTRIM(?)
                  AND id_vaga = ?
            ''').withCloseable { PreparedStatement instrucao ->

                instrucao.setString(1, cpfTeste)
                instrucao.setInt(2, idVagaTeste)

                instrucao.executeUpdate()
            }
        }
    }


    def "deve conectar ao PostgreSQL e consultar curtidas e matches pela API"() {

        when:
        int resultadoBanco = dataSource.connection.withCloseable { Connection conexao ->

            conexao.prepareStatement('SELECT 1').withCloseable {
                PreparedStatement instrucao ->

                    instrucao.executeQuery().withCloseable { ResultSet resultado ->

                        resultado.next()
                        return resultado.getInt(1)
                    }
            }
        }

        HttpResponse<?> respostaCurtidas =
                executar(HttpRequest.GET('/api/curtidas'))

        HttpResponse<?> respostaMatches =
                executar(HttpRequest.GET('/api/matches'))

        then:
        resultadoBanco == 1

        respostaCurtidas.status.code == 200
        respostaMatches.status.code == 200

        respostaCurtidas.getBody().isPresent()
        respostaMatches.getBody().isPresent()
    }


    def "deve registrar curtida de candidato, impedir duplicidade e permitir remocao pendente"() {

        when:
        Map payload = [
                autor       : 'CANDIDATO',
                cpfCandidato: cpfTeste,
                idVaga      : idVagaTeste
        ]

        HttpResponse<?> primeiraResposta =
                executar(postJson('/api/curtidas', payload))

        HttpResponse<?> segundaResposta =
                executar(postJson('/api/curtidas', payload))

        Map primeiraCurtida = (mapa(primeiraResposta).dados ?: [:]) as Map
        Map segundaCurtida = (mapa(segundaResposta).dados ?: [:]) as Map

        HttpResponse<?> respostaStatus =
                executar(HttpRequest.GET('/api/matches?cpf=' + cpfTeste))

        HttpResponse<?> respostaCompleta = executar(
                HttpRequest.GET('/api/matches?cpf=' + cpfTeste + '&completo=true')
        )

        Map dadosCompletos = mapa(respostaCompleta)

        List matchesCandidato =
                (dadosCompletos.get('matches') ?: []) as List

        List curtidasPendentesCandidato =
                (dadosCompletos.get('curtidasPendentes') ?: []) as List

        // Captura a quantidade antes da remoção.
        int curtidasAntesRemocao = contarCurtidasDoPar()

        HttpResponse<?> respostaRemocao = executar(deleteJson([
                autor       : 'CANDIDATO',
                cpfCandidato: cpfTeste,
                idVaga      : idVagaTeste
        ]))

        then:
        primeiraResposta.status.code == 201
        segundaResposta.status.code == 201

        primeiraCurtida.idCurtida != null
        primeiraCurtida.idCurtida == segundaCurtida.idCurtida
        primeiraCurtida.cnpjEmpresa == null

        respostaStatus.status.code == 200

        lista(respostaStatus).any {
            it.idVaga == idVagaTeste &&
                    it.status == 'curtido'
        }

        respostaCompleta.status.code == 200

        dadosCompletos.containsKey('matches')
        dadosCompletos.containsKey('curtidasPendentes')

        curtidasPendentesCandidato.any {
            ((Map) it).vaga?.id == idVagaTeste
        }

        curtidasAntesRemocao == 1

        respostaRemocao.status.code == 200

        // Após a remoção, não deve existir mais nenhuma curtida.
        contarCurtidasDoPar() == 0
    }


    def "deve registrar curtida de empresa e permitir remocao enquanto estiver pendente"() {

        when:
        Map payload = [
                autor       : 'EMPRESA',
                cpfCandidato: cpfTeste,
                cnpjEmpresa : cnpjTeste,
                idVaga      : idVagaTeste
        ]

        HttpResponse<?> respostaRegistro =
                executar(postJson('/api/matches', payload))

        HttpResponse<?> respostaStatus =
                executar(HttpRequest.GET('/api/matches?cnpj=' + cnpjTeste))

        HttpResponse<?> respostaCompleta = executar(
                HttpRequest.GET('/api/matches?cnpj=' + cnpjTeste + '&completo=true')
        )

        Map dadosCompletos = mapa(respostaCompleta)

        List matchesEmpresa =
                (dadosCompletos.get('matches') ?: []) as List

        List curtidasPendentesEmpresa =
                (dadosCompletos.get('curtidasPendentes') ?: []) as List

        // Captura a quantidade antes da remoção.
        int curtidasAntesRemocao = contarCurtidasDoPar()

        HttpResponse<?> respostaRemocao = executar(deleteJson([
                autor       : 'EMPRESA',
                cpfCandidato: cpfTeste,
                cnpjEmpresa : cnpjTeste,
                idVaga      : idVagaTeste
        ]))

        then:
        respostaRegistro.status.code == 201
        mapa(respostaRegistro).match == false

        respostaStatus.status.code == 200

        lista(respostaStatus).any {
            it.cpfCandidato?.toString()?.trim() == cpfTeste &&
                    it.idVaga == idVagaTeste &&
                    it.status == 'curtido'
        }

        respostaCompleta.status.code == 200

        dadosCompletos.containsKey('matches')
        dadosCompletos.containsKey('curtidasPendentes')

        curtidasPendentesEmpresa.any {
            ((Map) it).vaga?.id == idVagaTeste
        }

        curtidasAntesRemocao == 1

        respostaRemocao.status.code == 200

        // Após a remoção, não deve existir mais nenhuma curtida.
        contarCurtidasDoPar() == 0
    }


    def "deve gerar match quando candidato e empresa curtirem reciprocamente a mesma vaga"() {

        when:
        Map payloadCandidato = [
                autor       : 'CANDIDATO',
                cpfCandidato: cpfTeste,
                idVaga      : idVagaTeste
        ]

        Map payloadEmpresa = [
                autor       : 'EMPRESA',
                cpfCandidato: cpfTeste,
                cnpjEmpresa : cnpjTeste,
                idVaga      : idVagaTeste
        ]

        HttpResponse<?> respostaCandidato =
                executar(postJson('/api/curtidas', payloadCandidato))

        HttpResponse<?> respostaEmpresa =
                executar(postJson('/api/matches', payloadEmpresa))

        HttpResponse<?> respostaStatusCandidato =
                executar(HttpRequest.GET('/api/matches?cpf=' + cpfTeste))

        HttpResponse<?> respostaStatusEmpresa =
                executar(HttpRequest.GET('/api/matches?cnpj=' + cnpjTeste))

        HttpResponse<?> respostaCompletaCandidato = executar(
                HttpRequest.GET('/api/matches?cpf=' + cpfTeste + '&completo=true')
        )

        HttpResponse<?> respostaCompletaEmpresa = executar(
                HttpRequest.GET('/api/matches?cnpj=' + cnpjTeste + '&completo=true')
        )

        Map completoCandidato = mapa(respostaCompletaCandidato)
        Map completoEmpresa = mapa(respostaCompletaEmpresa)

        List matchesCompletosCandidato =
                (completoCandidato.get('matches') ?: []) as List

        List matchesCompletosEmpresa =
                (completoEmpresa.get('matches') ?: []) as List

        List curtidasPendentesCandidato =
                (completoCandidato.get('curtidasPendentes') ?: []) as List

        List curtidasPendentesEmpresa =
                (completoEmpresa.get('curtidasPendentes') ?: []) as List

        // Captura o estado do banco antes das tentativas de remoção.
        int curtidasAntesTentativaRemocao = contarCurtidasDoPar()
        int matchesAntesTentativaRemocao = contarMatchesDoPar()

        HttpResponse<?> tentativaRemocaoCandidato =
                executar(deleteJson([
                        autor       : 'CANDIDATO',
                        cpfCandidato: cpfTeste,
                        idVaga      : idVagaTeste
                ]))

        HttpResponse<?> tentativaRemocaoEmpresa =
                executar(deleteJson([
                        autor       : 'EMPRESA',
                        cpfCandidato: cpfTeste,
                        cnpjEmpresa : cnpjTeste,
                        idVaga      : idVagaTeste
                ]))

        then:
        respostaCandidato.status.code == 201
        mapa(respostaCandidato).match == false

        respostaEmpresa.status.code == 201
        mapa(respostaEmpresa).match == true
        mapa(respostaEmpresa).mensagem == 'MATCH ENCONTRADO!'

        respostaStatusCandidato.status.code == 200

        lista(respostaStatusCandidato).any {
            it.idVaga == idVagaTeste &&
                    it.status == 'match'
        }

        respostaStatusEmpresa.status.code == 200

        lista(respostaStatusEmpresa).any {
            it.cpfCandidato?.toString()?.trim() == cpfTeste &&
                    it.idVaga == idVagaTeste &&
                    it.status == 'match'
        }

        respostaCompletaCandidato.status.code == 200
        respostaCompletaEmpresa.status.code == 200

        completoCandidato.containsKey('matches')
        completoCandidato.containsKey('curtidasPendentes')

        completoEmpresa.containsKey('matches')
        completoEmpresa.containsKey('curtidasPendentes')

        matchesCompletosCandidato.any {
            ((Map) it).candidato?.cpf?.toString()?.trim() == cpfTeste &&
                    ((Map) it).empresa?.cnpj?.toString()?.trim() == cnpjTeste &&
                    ((Map) it).vaga?.id == idVagaTeste
        }

        matchesCompletosEmpresa.any {
            ((Map) it).candidato?.cpf?.toString()?.trim() == cpfTeste &&
                    ((Map) it).empresa?.cnpj?.toString()?.trim() == cnpjTeste &&
                    ((Map) it).vaga?.id == idVagaTeste
        }

        curtidasPendentesCandidato.isEmpty()

        curtidasPendentesEmpresa.every { Map item ->
            item.candidato instanceof Map &&
                    !item.candidato.containsKey('cpf') &&
                    item.vaga?.id != null
        }

        // Antes de tentar remover, devem existir duas curtidas e um match.
        curtidasAntesTentativaRemocao == 2
        matchesAntesTentativaRemocao == 1

        // Curtidas que fazem parte de um match não podem ser removidas.
        tentativaRemocaoCandidato.status.code == 400
        tentativaRemocaoEmpresa.status.code == 400

        // O banco deve continuar com as duas curtidas e o match.
        contarCurtidasDoPar() == 2
        contarMatchesDoPar() == 1
    }


    private void selecionarParDisponivel() {

        String sql = '''
            SELECT
                BTRIM(c.cpf) AS cpf,
                BTRIM(v.cnpj_empresa) AS cnpj,
                v.id_vaga
            FROM candidato c
            JOIN vaga v
                ON v.cnpj_empresa IS NOT NULL
            JOIN empresa e
                ON BTRIM(e.cnpj) = BTRIM(v.cnpj_empresa)
            WHERE BTRIM(c.cpf) ~ '^[0-9]{11}$'
              AND BTRIM(v.cnpj_empresa) ~ '^[0-9]{14}$'
              AND NOT EXISTS (
                  SELECT 1
                  FROM curtida cu
                  WHERE BTRIM(cu.cpf_candidato) = BTRIM(c.cpf)
                    AND cu.id_vaga = v.id_vaga
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM match_vaga m
                  WHERE BTRIM(m.cpf_candidato) = BTRIM(c.cpf)
                    AND m.id_vaga = v.id_vaga
              )
            ORDER BY v.id_vaga, c.cpf
            LIMIT 1
        '''

        dataSource.connection.withCloseable { Connection conexao ->

            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->

                instrucao.executeQuery().withCloseable { ResultSet resultado ->

                    if (!resultado.next()) {
                        throw new AssertionError(
                                'Não foi encontrado um candidato e uma vaga disponíveis ' +
                                        'sem curtidas anteriores para executar os testes.'
                        )
                    }

                    cpfTeste = resultado.getString('cpf')
                    cnpjTeste = resultado.getString('cnpj')
                    idVagaTeste = resultado.getInt('id_vaga')
                }
            }
        }
    }


    private HttpRequest<?> postJson(String caminho, Map dados) {
        return HttpRequest.POST(caminho, dados)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
    }


    private HttpRequest<?> deleteJson(Map dados) {
        return HttpRequest.create(HttpMethod.DELETE, '/api/matches')
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .body(dados)
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

        return (corpo instanceof Map)
                ? (corpo as Map)
                : [:]
    }


    private static List lista(HttpResponse<?> resposta) {
        Object corpo = resposta.getBody(Object).orElse([])

        return (corpo instanceof List)
                ? (corpo as List)
                : []
    }


    private int contarCurtidasDoPar() {
        return dataSource.connection.withCloseable { Connection conexao ->

            conexao.prepareStatement('''
                SELECT COUNT(*)
                FROM curtida
                WHERE BTRIM(cpf_candidato) = BTRIM(?)
                  AND id_vaga = ?
            ''').withCloseable { PreparedStatement instrucao ->

                instrucao.setString(1, cpfTeste)
                instrucao.setInt(2, idVagaTeste)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    resultado.next()
                    return resultado.getInt(1)
                }
            }
        }
    }


    private int contarMatchesDoPar() {
        return dataSource.connection.withCloseable { Connection conexao ->

            conexao.prepareStatement('''
                SELECT COUNT(*)
                FROM match_vaga
                WHERE BTRIM(cpf_candidato) = BTRIM(?)
                  AND BTRIM(cnpj_empresa) = BTRIM(?)
                  AND id_vaga = ?
            ''').withCloseable { PreparedStatement instrucao ->

                instrucao.setString(1, cpfTeste)
                instrucao.setString(2, cnpjTeste)
                instrucao.setInt(3, idVagaTeste)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    resultado.next()
                    return resultado.getInt(1)
                }
            }
        }
    }
}