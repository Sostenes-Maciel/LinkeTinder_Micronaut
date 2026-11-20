package linketinder.competencias.model

import jakarta.inject.Singleton

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException

@Singleton
class RepositorioPostgresCompetencia implements RepositorioCompetencia {

    private final DataSource dataSource

    RepositorioPostgresCompetencia(DataSource dataSource) {
        this.dataSource = dataSource
    }

    @Override
    List<Map<String, Object>> listarCompetencias() {
        String sql = 'SELECT id_competencia, nome FROM competencia ORDER BY nome '
        dataSource.connection.withCloseable { Connection conexao ->
            conexao.prepareStatement(sql).withCloseable { instrucao ->
                instrucao.executeQuery().withCloseable { resultado ->
                    List<Map<String, Object>> lista = []
                    while (resultado.next()) {
                        lista << ([
                                idCompetencia: resultado.getInt('id_competencia'),
                                nome         : resultado.getString('nome')
                        ] as Map<String, Object>)
                    }
                    return lista
                }
            }
        }
    }

    @Override
    Map<String, Object> inserirCompetencia(String nome) {
        emTransacao { Connection conexao ->
            String nomePadronizado = obterNomePadronizado(conexao, nome)
            long idCompetencia = garantirEObterIdCompetencia(conexao, nomePadronizado)
            return [
                    idCompetencia: idCompetencia,
                    nome         : nomePadronizado
            ]
        } as Map<String, Object>
    }

    @Override
    List<Map<String, Object>> listarPorCandidato(String cpfCandidato) {
        String sql = '''
            SELECT c.id_competencia, c.nome
            FROM competencia c
            INNER JOIN candidato_competencia cc ON cc.id_competencia = c.id_competencia
            WHERE cc.cpf_candidato = ?
            ORDER BY c.nome ASC
        '''
        buscarVinculosPorTexto(sql, cpfCandidato)
    }

    @Override
    void vincularCandidato(String cpfCandidato, String nomeCompetencia) {
        emTransacao { Connection conexao ->
            String nomePadronizado = obterNomePadronizado(conexao, nomeCompetencia)
            long idCompetencia = garantirEObterIdCompetencia(conexao, nomePadronizado)
            String sql = '''
                INSERT INTO candidato_competencia (cpf_candidato, id_competencia)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
            '''
            conexao.prepareStatement(sql).withCloseable { instrucao ->
                instrucao.setString(1, cpfCandidato)
                instrucao.setLong(2, idCompetencia)
                instrucao.executeUpdate()
            }
        }
    }

    @Override
    List<Map<String, Object>> listarPorEmpresa(String cnpjEmpresa) {
        String sql = '''
            SELECT c.id_competencia, c.nome
            FROM competencia c
            INNER JOIN empresa_competencia ec ON ec.id_competencia = c.id_competencia
            WHERE ec.cnpj_empresa = ?
            ORDER BY c.nome ASC
        '''
        buscarVinculosPorTexto(sql, cnpjEmpresa)
    }

    @Override
    void vincularEmpresa(String cnpjEmpresa, String nomeCompetencia) {
        emTransacao { Connection conexao ->
            String nomePadronizado = obterNomePadronizado(conexao, nomeCompetencia)
            long idCompetencia = garantirEObterIdCompetencia(conexao, nomePadronizado)
            String sql = '''
                INSERT INTO empresa_competencia (cnpj_empresa, id_competencia)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
            '''
            conexao.prepareStatement(sql).withCloseable { instrucao ->
                instrucao.setString(1, cnpjEmpresa)
                instrucao.setLong(2, idCompetencia)
                instrucao.executeUpdate()
            }
        }
    }

    @Override
    List<Map<String, Object>> listarPorVaga(int idVaga){
        String sql = '''
            SELECT c.id_competencia, c.nome
            FROM competencia c
            INNER JOIN vaga_competencia vc ON vc.id_competencia = c.id_competencia
            WHERE vc.id_vaga = ?
            ORDER BY c.nome
        '''
        dataSource.connection.withCloseable { Connection conexao ->
            conexao.prepareStatement(sql).withCloseable { instrucao ->
                instrucao.setInt(1, idVaga)
                instrucao.executeQuery().withCloseable { resultado ->
                    List<Map<String, Object>> lista = []
                    while (resultado.next()) {
                        lista << ([
                                idCompetencia: resultado.getInt('id_competencia'),
                                nome         : resultado.getString('nome')
                        ] as Map<String, Object>)
                    }
                    return lista
                }
            }
        }
    }

    @Override
    void vincularVaga(int idVaga, String nomeCompetencia) {
        emTransacao { Connection conexao ->
            String nomePadronizado = obterNomePadronizado(conexao, nomeCompetencia)
            long idCompetencia = garantirEObterIdCompetencia(conexao, nomePadronizado)
            String sql = '''
                INSERT INTO vaga_competencia (id_vaga, id_competencia)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
            '''
            conexao.prepareStatement(sql).withCloseable { instrucao ->
                instrucao.setInt(1, idVaga)
                instrucao.setLong(2, idCompetencia)
                instrucao.executeUpdate()
            }
        }
    }

    private List<Map<String, Object>> buscarVinculosPorTexto(
            String sql,
            String identificador
    ) {
        dataSource.connection.withCloseable { Connection conexao ->
            conexao.prepareStatement(sql).withCloseable { instrucao ->
                instrucao.setString(1, identificador)
                instrucao.executeQuery().withCloseable { resultado ->
                    List<Map<String, Object>> lista = []
                    while (resultado.next()) {
                        lista << ([
                                idCompetencia: resultado.getInt('id_competencia'),
                                nome         : resultado.getString('nome')
                        ] as Map<String, Object>)
                    }
                    return lista
                }
            }
        }
    }

    private static String obterNomePadronizado(
            Connection conexao,
            String competencia
    ) {
        String nomeLimpo = competencia.trim()

        String sql = '''
        SELECT nome
        FROM competencia
        WHERE lower(nome) = lower(?)
        LIMIT 1
    '''

        String nomeEncontrado = conexao.prepareStatement(sql).withCloseable { instrucao ->
            instrucao.setString(1, nomeLimpo)

            instrucao.executeQuery().withCloseable { resultado ->
                resultado.next() ? resultado.getString('nome') : null
            }
        }

        return nomeEncontrado ?: nomeLimpo
    }

    private static long garantirEObterIdCompetencia(Connection conexao, String nome) {
        String sql = '''
            INSERT INTO competencia (nome) VALUES (?)
            ON CONFLICT (nome) DO NOTHING
            RETURNING id_competencia
        '''
        long id = conexao.prepareStatement(sql).withCloseable { instrucao ->
            instrucao.setString(1, nome)
            instrucao.executeQuery().withCloseable { resultado ->
                if (resultado.next()) {
                    return resultado.getLong('id_competencia')
                }
                return -1L
            }
        }
        if (id == -1L) {
            String sqlBusca = 'SELECT id_competencia FROM competencia WHERE nome = ? LIMIT 1'
            id = conexao.prepareStatement(sqlBusca).withCloseable { instrucao ->
                instrucao.setString(1, nome)
                instrucao.executeQuery().withCloseable { resultado ->
                    if (resultado.next()) {
                        return resultado.getLong('id_competencia')
                    }
                    return -1L
                }
            }
        }
        if (id == -1L) {
            throw new SQLException('Nao foi possivel obter o id da competencia.')
        }
        return id
    }

    private <T> T emTransacao(Closure<T> operacao) {
        dataSource.connection.withCloseable { Connection conexao ->
            conexao.autoCommit = false
            try {
                T resultado = operacao.call(conexao)
                conexao.commit()
                resultado
            } catch (Exception exception) {
                try {
                    conexao.rollback()
                } catch (SQLException falhaRollback) {
                    exception.addSuppressed(falhaRollback)
                }
                throw exception
            }
        }
    }
}