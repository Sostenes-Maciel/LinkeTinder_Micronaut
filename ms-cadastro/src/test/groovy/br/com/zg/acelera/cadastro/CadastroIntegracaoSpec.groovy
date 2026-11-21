package br.com.zg.acelera.cadastro.model

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
import java.security.SecureRandom

@MicronautTest(transactional = false)
class CadastroIntegracaoSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient cliente

    @Inject
    DataSource dataSource

    private String cpfTeste
    private String cnpjTeste
    private String emailCandidatoTeste
    private String emailEmpresaTeste
    private String competenciaCandidatoTeste
    private String competenciaEmpresaTeste


    void setup() {
        String identificador = UUID.randomUUID().toString()

        cpfTeste = gerarNumero(11)
        cnpjTeste = gerarNumero(14)

        emailCandidatoTeste =
                'candidato.' + identificador + '@teste.com'

        emailEmpresaTeste =
                'empresa.' + identificador + '@teste.com'

        competenciaCandidatoTeste =
                'Integracao Candidato ' + identificador

        competenciaEmpresaTeste =
                'Integracao Empresa ' + identificador
    }


    void cleanup() {
        if (cpfTeste == null || cnpjTeste == null) {
            return
        }

        dataSource.connection.withCloseable { Connection conexao ->

            executarUpdate(conexao, '''
                DELETE FROM candidato_competencia
                WHERE cpf_candidato = ?
            ''', [cpfTeste])

            executarUpdate(conexao, '''
                DELETE FROM empresa_competencia
                WHERE cnpj_empresa = ?
            ''', [cnpjTeste])

            executarUpdate(conexao, '''
                DELETE FROM candidato
                WHERE cpf = ?
            ''', [cpfTeste])

            executarUpdate(conexao, '''
                DELETE FROM empresa
                WHERE cnpj = ?
            ''', [cnpjTeste])

            executarUpdate(conexao, '''
                DELETE FROM competencia
                WHERE nome IN (?, ?)
            ''', [
                    competenciaCandidatoTeste,
                    competenciaEmpresaTeste
            ])
        }
    }


    def "deve conectar ao PostgreSQL"() {

        when:
        int resultado = dataSource.connection.withCloseable { Connection conexao ->

            conexao.prepareStatement('SELECT 1').withCloseable {
                PreparedStatement instrucao ->

                    instrucao.executeQuery().withCloseable { ResultSet consulta ->

                        consulta.next()
                        return consulta.getInt(1)
                    }
            }
        }

        then:
        resultado == 1
    }


    def "deve cadastrar candidato e persistir suas competencias no PostgreSQL"() {

        given:
        Map candidato = [
                cpf        : cpfTeste,
                nome       : 'Candidato Integracao',
                email      : emailCandidatoTeste,
                pais       : 'Brasil',
                estado     : 'Pernambuco',
                cep        : '50000-000',
                descricao  : 'Candidato criado pelo teste de integracao',
                idade      : 29,
                competencias: [
                        competenciaCandidatoTeste
                ]
        ]

        when:
        HttpResponse<?> resposta = executar(
                HttpRequest.POST('/api/candidatos', candidato)
                        .contentType(MediaType.APPLICATION_JSON_TYPE)
        )

        Map corpo = mapa(resposta)

        Map candidatoBanco = buscarCandidato()

        int quantidadeVinculos = contarVinculosCandidato()

        then:
        resposta.status.code == 201

        corpo.tipo == 'candidato'
        corpo.identificador == cpfTeste

        candidatoBanco != null
        candidatoBanco.cpf == cpfTeste
        candidatoBanco.nome == 'Candidato Integracao'
        candidatoBanco.email == emailCandidatoTeste
        candidatoBanco.pais == 'Brasil'
        candidatoBanco.estado == 'Pernambuco'
        candidatoBanco.cep == '50000-000'
        candidatoBanco.descricao == 'Candidato criado pelo teste de integracao'
        candidatoBanco.idade == 29

        quantidadeVinculos == 1

        competenciaCandidatoExiste()
    }


    def "deve cadastrar empresa e persistir suas competencias no PostgreSQL"() {

        given:
        Map empresa = [
                cnpj        : cnpjTeste,
                nome        : 'Empresa Integracao',
                email       : emailEmpresaTeste,
                pais        : 'Brasil',
                estado      : 'Pernambuco',
                cep         : '50000-000',
                descricao   : 'Empresa criada pelo teste de integracao',
                competencias: [
                        competenciaEmpresaTeste
                ]
        ]

        when:
        HttpResponse<?> resposta = executar(
                HttpRequest.POST('/api/empresas', empresa)
                        .contentType(MediaType.APPLICATION_JSON_TYPE)
        )

        Map corpo = mapa(resposta)

        Map empresaBanco = buscarEmpresa()

        int quantidadeVinculos = contarVinculosEmpresa()

        then:
        resposta.status.code == 201

        corpo.tipo == 'empresa'
        corpo.identificador == cnpjTeste

        empresaBanco != null
        empresaBanco.cnpj == cnpjTeste
        empresaBanco.nome == 'Empresa Integracao'
        empresaBanco.email == emailEmpresaTeste
        empresaBanco.pais == 'Brasil'
        empresaBanco.estado == 'Pernambuco'
        empresaBanco.cep == '50000-000'
        empresaBanco.descricao == 'Empresa criada pelo teste de integracao'

        quantidadeVinculos == 1

        competenciaEmpresaExiste()
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


    private Map buscarCandidato() {
        return dataSource.connection.withCloseable { Connection conexao ->

            conexao.prepareStatement('''
                SELECT cpf, nome, email, pais, estado, cep, descricao, idade
                FROM candidato
                WHERE cpf = ?
            ''').withCloseable { PreparedStatement instrucao ->

                instrucao.setString(1, cpfTeste)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->

                    if (!resultado.next()) {
                        return null
                    }

                    return [
                            cpf      : resultado.getString('cpf'),
                            nome     : resultado.getString('nome'),
                            email    : resultado.getString('email'),
                            pais     : resultado.getString('pais'),
                            estado   : resultado.getString('estado'),
                            cep      : resultado.getString('cep'),
                            descricao: resultado.getString('descricao'),
                            idade    : resultado.getInt('idade')
                    ]
                }
            }
        }
    }


    private Map buscarEmpresa() {
        return dataSource.connection.withCloseable { Connection conexao ->

            conexao.prepareStatement('''
                SELECT cnpj, nome, email, pais, estado, cep, descricao
                FROM empresa
                WHERE cnpj = ?
            ''').withCloseable { PreparedStatement instrucao ->

                instrucao.setString(1, cnpjTeste)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->

                    if (!resultado.next()) {
                        return null
                    }

                    return [
                            cnpj     : resultado.getString('cnpj'),
                            nome     : resultado.getString('nome'),
                            email    : resultado.getString('email'),
                            pais     : resultado.getString('pais'),
                            estado   : resultado.getString('estado'),
                            cep      : resultado.getString('cep'),
                            descricao: resultado.getString('descricao')
                    ]
                }
            }
        }
    }


    private int contarVinculosCandidato() {
        return dataSource.connection.withCloseable { Connection conexao ->

            conexao.prepareStatement('''
                SELECT COUNT(*)
                FROM candidato_competencia
                WHERE cpf_candidato = ?
            ''').withCloseable { PreparedStatement instrucao ->

                instrucao.setString(1, cpfTeste)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->

                    resultado.next()
                    return resultado.getInt(1)
                }
            }
        }
    }


    private int contarVinculosEmpresa() {
        return dataSource.connection.withCloseable { Connection conexao ->

            conexao.prepareStatement('''
                SELECT COUNT(*)
                FROM empresa_competencia
                WHERE cnpj_empresa = ?
            ''').withCloseable { PreparedStatement instrucao ->

                instrucao.setString(1, cnpjTeste)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->

                    resultado.next()
                    return resultado.getInt(1)
                }
            }
        }
    }


    private boolean competenciaCandidatoExiste() {
        return dataSource.connection.withCloseable { Connection conexao ->

            conexao.prepareStatement('''
                SELECT COUNT(*)
                FROM candidato_competencia cc
                JOIN competencia c
                    ON c.id_competencia = cc.id_competencia
                WHERE cc.cpf_candidato = ?
                  AND c.nome = ?
            ''').withCloseable { PreparedStatement instrucao ->

                instrucao.setString(1, cpfTeste)
                instrucao.setString(2, competenciaCandidatoTeste)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->

                    resultado.next()
                    return resultado.getInt(1) == 1
                }
            }
        }
    }


    private boolean competenciaEmpresaExiste() {
        return dataSource.connection.withCloseable { Connection conexao ->

            conexao.prepareStatement('''
                SELECT COUNT(*)
                FROM empresa_competencia ec
                JOIN competencia c
                    ON c.id_competencia = ec.id_competencia
                WHERE ec.cnpj_empresa = ?
                  AND c.nome = ?
            ''').withCloseable { PreparedStatement instrucao ->

                instrucao.setString(1, cnpjTeste)
                instrucao.setString(2, competenciaEmpresaTeste)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->

                    resultado.next()
                    return resultado.getInt(1) == 1
                }
            }
        }
    }


    private static void executarUpdate(
            Connection conexao,
            String sql,
            List parametros
    ) {
        conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->

            parametros.eachWithIndex { Object valor, int indice ->
                instrucao.setObject(indice + 1, valor)
            }

            instrucao.executeUpdate()
        }
    }


    private static String gerarNumero(int tamanho) {
        SecureRandom aleatorio = new SecureRandom()

        return new BigInteger(100, aleatorio)
                .toString()
                .padLeft(tamanho, '0')
                .takeRight(tamanho)
    }
}