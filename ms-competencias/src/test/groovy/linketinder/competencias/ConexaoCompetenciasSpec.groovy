package linketinder.competencias

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest

import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource

@MicronautTest
class ConexaoCompetenciasSpec extends Specification {

    @Inject
    DataSource dataSource

    @Inject
    @Client("/")
    HttpClient cliente

    def "deve estabelecer conexao com PostgreSQL"() {
        when:
        boolean conexaoValida = dataSource.connection.withCloseable { conexao ->

            conexao.prepareStatement('SELECT 1').withCloseable { instrucao ->

                instrucao.executeQuery().withCloseable { resultado ->

                    resultado.next() && resultado.getInt(1) == 1
                }
            }
        }

        then:
        conexaoValida
    }

    def "deve responder ao endpoint de listagem de competencias"() {
        when:
        def resposta = cliente.toBlocking().exchange(
                HttpRequest.GET('/api/competencias'),
                String.class
        )

        then:
        resposta.status == HttpStatus.OK

        and:
        String corpo = resposta.getBody(String).orElse('')

        corpo.trim().startsWith('[')
    }
}