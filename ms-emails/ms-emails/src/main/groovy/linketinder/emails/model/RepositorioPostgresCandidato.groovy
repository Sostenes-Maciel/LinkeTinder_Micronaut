package linketinder.emails.model

import jakarta.inject.Singleton

import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

@Singleton
class RepositorioPostgresCandidato implements RepositorioCandidato {

    private final DataSource dataSource

    RepositorioPostgresCandidato(DataSource dataSource) {
        this.dataSource = dataSource
    }

    @Override
    List<Map> listarCandidatosComEmail() {
        String sql = '''
        SELECT cpf, nome, email
        FROM candidato
        WHERE email IS NOT NULL AND TRIM(email) <> ''
        ORDER BY nome
    '''

        List<Map> candidatos = []

        dataSource.connection.withCloseable { Connection conexao ->
            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    while (resultado.next()) {
                        candidatos.add([
                                cpf  : resultado.getString('cpf'),
                                nome : resultado.getString('nome'),
                                email: resultado.getString('email').trim()
                        ])
                    }
                }
            }
        }

        return candidatos
    }
}