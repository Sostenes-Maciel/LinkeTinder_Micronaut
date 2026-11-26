// Testes de integracao do microsservico de vagas com PostgreSQL.
package linketinder.vagas

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
import linketinder.vagas.mensageria.NotificacaoVagaProducer
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.ResultSet

@MicronautTest(transactional = false)
class VagaIntegracaoSpec extends Specification {

    @Inject
    DataSource dataSource

    @Inject
    @Client("/")
    HttpClient cliente

    @Inject
    NotificacaoVagaProducer notificacaoVagaProducer

    @MockBean(NotificacaoVagaProducer)
    NotificacaoVagaProducer mockNotificacaoVagaProducer() {
        Mock(NotificacaoVagaProducer)
    }

    private Integer idVagaCriada
    private List<String> competenciasTeste = []

    private static Object lerJson(String corpo) {
        new JsonSlurper().parseText(corpo)
    }

    private static String citarIdentificador(String identificador) {
        return '"' + identificador.replace('"', '""') + '"'
    }

    private String buscarCnpjEmpresaDisponivel() {
        dataSource.connection.withCloseable { Connection conexao ->
            Map<String, String> referencia = null

            conexao.metaData
                    .getImportedKeys(conexao.catalog, null, 'vaga')
                    .withCloseable { ResultSet resultado ->
                        while (resultado.next()) {
                            if (resultado.getString('FKCOLUMN_NAME') == 'cnpj_empresa') {
                                referencia = [
                                        esquema: resultado.getString('PKTABLE_SCHEM'),
                                        tabela : resultado.getString('PKTABLE_NAME'),
                                        coluna : resultado.getString('PKCOLUMN_NAME')
                                ]
                                break
                            }
                        }
                    }

            assert referencia != null:
                    'Não foi encontrada a chave estrangeira de vaga.cnpj_empresa.'

            String tabela = referencia.esquema
                    ? citarIdentificador(referencia.esquema) + '.' +
                    citarIdentificador(referencia.tabela)
                    : citarIdentificador(referencia.tabela)

            String sql = 'SELECT ' +
                    citarIdentificador(referencia.coluna) +
                    ' FROM ' + tabela + ' LIMIT 1'

            return conexao.createStatement().withCloseable { instrucao ->
                instrucao.executeQuery(sql).withCloseable { consulta ->
                    assert consulta.next():
                            'É necessário ter pelo menos uma empresa cadastrada para executar os testes de escrita.'

                    return consulta.getString(1)
                }
            }
        }
    }

    private Map<String, Object> criarVaga() {
        String token = UUID.randomUUID().toString().replace('-', '')

        String competenciaJava = 'TesteJava-' + token
        String competenciaGroovy = 'TesteGroovy-' + token
        String titulo = 'Vaga de integracao ' + token

        List<String> competencias = [
                competenciaJava,
                competenciaGroovy
        ]

        competenciasTeste.addAll(competencias)

        Map<String, Object> dados = [
                cnpjEmpresa : buscarCnpjEmpresaDisponivel(),
                titulo      : titulo,
                pais        : 'Brasil',
                estado      : 'Pernambuco',
                descricao   : 'Vaga criada pelos testes de integracao.',
                areaAtuacao : 'TI',
                competencias: competencias
        ]

        def resposta = cliente.toBlocking().exchange(
                HttpRequest.POST('/api/vagas', dados)
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        assert resposta.status == HttpStatus.CREATED

        Map<String, Object> criada =
                lerJson(resposta.body()) as Map<String, Object>

        idVagaCriada = ((Number) criada.idVaga).intValue()

        return criada
    }

    private List<String> buscarCompetenciasPersistidas(int idVaga) {
        String sql = '''
            SELECT c.nome
            FROM competencia c
            INNER JOIN vaga_competencia vc
                ON vc.id_competencia = c.id_competencia
            WHERE vc.id_vaga = ?
            ORDER BY c.nome
        '''

        dataSource.connection.withCloseable { Connection conexao ->
            conexao.prepareStatement(sql).withCloseable { instrucao ->
                instrucao.setInt(1, idVaga)

                instrucao.executeQuery().withCloseable { resultado ->
                    List<String> competencias = []

                    while (resultado.next()) {
                        competencias.add(resultado.getString('nome'))
                    }

                    return competencias
                }
            }
        }
    }

    private int contarVagasNoBanco(int idVaga) {
        dataSource.connection.withCloseable { Connection conexao ->
            conexao.prepareStatement(
                    'SELECT COUNT(*) FROM vaga WHERE id_vaga = ?'
            ).withCloseable { instrucao ->
                instrucao.setInt(1, idVaga)

                instrucao.executeQuery().withCloseable { resultado ->
                    resultado.next()
                    return resultado.getInt(1)
                }
            }
        }
    }

    def "deve estabelecer conexao com PostgreSQL"() {
        when:
        int resultado = dataSource.connection.withCloseable {
            Connection conexao ->
                conexao.createStatement().withCloseable { instrucao ->
                    instrucao.executeQuery('SELECT 1').withCloseable { consulta ->
                        consulta.next()
                        consulta.getInt(1)
                    }
                }
        }

        then:
        resultado == 1
    }

    def "GET deve consultar todas as vagas no PostgreSQL"() {
        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET('/api/vagas'),
                String
        )

        then:
        resposta.status == HttpStatus.OK

        def vagas = lerJson(resposta.body())

        assert vagas instanceof List
        assert vagas.every { it instanceof Map }
    }

    def "GET por ID deve retornar uma vaga persistida"() {
        given:
        def criada = criarVaga()
        int idVaga = criada.idVaga as int

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET('/api/vagas/' + idVaga),
                String
        )

        then:
        resposta.status == HttpStatus.OK

        def resultado = lerJson(resposta.body())

        resultado.idVaga == idVaga
        resultado.titulo == criada.titulo
        resultado.competencias.size() == 2
    }

    def "GET por empresa deve retornar suas vagas persistidas"() {
        given:
        def criada = criarVaga()
        String cnpj = criada.cnpjEmpresa as String

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET('/api/vagas/empresas/' + cnpj),
                String
        )

        then:
        resposta.status == HttpStatus.OK

        def vagas = lerJson(resposta.body())

        assert vagas instanceof List
        assert vagas.any { it.idVaga == criada.idVaga }
    }

    def "GET deve retornar HTTP 422 para vaga inexistente"() {
        when:
        cliente.toBlocking().exchange(
                HttpRequest.GET('/api/vagas/2147483647'),
                String
        )

        then:
        def excecao = thrown(HttpClientResponseException)

        excecao.status == HttpStatus.UNPROCESSABLE_ENTITY

        def corpo = lerJson(
                excecao.response.getBody(String).orElseThrow()
        )

        corpo.mensagem.contains('nao encontrada')
    }

    def "POST deve cadastrar vaga e persistir seus dados e competencias"() {
        when:
        def criada = criarVaga()

        then:
        criada.idVaga != null
        criada.titulo != null

        and:
        contarVagasNoBanco(criada.idVaga as int) == 1

        and:
        buscarCompetenciasPersistidas(criada.idVaga as int).sort() ==
                competenciasTeste.sort()
    }

    def "PUT deve atualizar dados e competencias da vaga no PostgreSQL"() {
        given:
        def criada = criarVaga()
        int idVaga = criada.idVaga as int

        String token = UUID.randomUUID().toString().replace('-', '')

        String competenciaKotlin = 'TesteKotlin-' + token
        String competenciaMicronaut = 'TesteMicronaut-' + token

        List<String> novasCompetencias = [
                competenciaKotlin,
                competenciaMicronaut
        ]

        competenciasTeste.addAll(novasCompetencias)

        Map<String, Object> dadosAtualizacao = [
                titulo      : 'Vaga atualizada pela integracao',
                pais        : 'Brasil',
                estado      : 'Pernambuco',
                descricao   : 'Descricao atualizada no PostgreSQL.',
                areaAtuacao : 'TI',
                competencias: novasCompetencias
        ]

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.PUT('/api/vagas/' + idVaga, dadosAtualizacao)
                        .contentType(MediaType.APPLICATION_JSON),
                String
        )

        then:
        resposta.status == HttpStatus.OK

        def resultado = lerJson(resposta.body())

        resultado.idVaga == idVaga
        resultado.titulo == 'Vaga atualizada pela integracao'
        resultado.competencias.sort() == novasCompetencias.sort()

        and:
        buscarCompetenciasPersistidas(idVaga).sort() ==
                novasCompetencias.sort()

        and:
        String tituloPersistido = dataSource.connection.withCloseable {
            Connection conexao ->
                conexao.prepareStatement(
                        'SELECT titulo FROM vaga WHERE id_vaga = ?'
                ).withCloseable { instrucao ->
                    instrucao.setInt(1, idVaga)

                    instrucao.executeQuery().withCloseable { consulta ->
                        assert consulta.next()
                        consulta.getString('titulo')
                    }
                }
        }

        tituloPersistido == 'Vaga atualizada pela integracao'
    }

    def "DELETE deve remover vaga e vinculos de competencias do PostgreSQL"() {
        given:
        def criada = criarVaga()
        int idVaga = criada.idVaga as int

        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.DELETE('/api/vagas/' + idVaga)
        )

        then:
        resposta.status == HttpStatus.NO_CONTENT

        and:
        contarVagasNoBanco(idVaga) == 0

        and:
        buscarCompetenciasPersistidas(idVaga).isEmpty()
    }

    def cleanup() {
        dataSource.connection.withCloseable { Connection conexao ->
            if (idVagaCriada != null) {
                conexao.prepareStatement(
                        'DELETE FROM vaga_competencia WHERE id_vaga = ?'
                ).withCloseable { instrucao ->
                    instrucao.setInt(1, idVagaCriada)
                    instrucao.executeUpdate()
                }

                conexao.prepareStatement(
                        'DELETE FROM vaga WHERE id_vaga = ?'
                ).withCloseable { instrucao ->
                    instrucao.setInt(1, idVagaCriada)
                    instrucao.executeUpdate()
                }
            }

            competenciasTeste.each { String nome ->
                conexao.prepareStatement(
                        'DELETE FROM competencia WHERE nome = ?'
                ).withCloseable { instrucao ->
                    instrucao.setString(1, nome)
                    instrucao.executeUpdate()
                }
            }
        }
    }
}