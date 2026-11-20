package linketinder.vagas.model

import jakarta.inject.Singleton

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException

@Singleton
class RepositorioPostgresVaga implements RepositorioVaga {

    private final DataSource dataSource

    RepositorioPostgresVaga(DataSource dataSource) {
        this.dataSource = dataSource
    }

    @Override
    List<Map<String, Object>> listarVagas() {
        String sql = '''
            SELECT id_vaga, titulo, pais, estado, descricao, cnpj_empresa, area_atuacao
            FROM vaga
            ORDER BY id_vaga DESC
        '''
        dataSource.connection.withCloseable { Connection conexao ->
            List<Map<String, Object>> vagas = []
            conexao.prepareStatement(sql).withCloseable { instrucao ->
                instrucao.executeQuery().withCloseable { resultado ->
                    while (resultado.next()) {
                        int idVaga = resultado.getInt('id_vaga')
                        vagas << montarMapaVaga(conexao, resultado, idVaga)
                    }
                }
            }
            return vagas
        }
    }

    @Override
    Map<String, Object> buscarPorId(int idVaga) {
        String sql = '''
            SELECT id_vaga, titulo, pais, estado, descricao, cnpj_empresa, area_atuacao
            FROM vaga
            WHERE id_vaga = ?
        '''
        dataSource.connection.withCloseable { Connection conexao ->
            conexao.prepareStatement(sql).withCloseable { instrucao ->
                instrucao.setInt(1, idVaga)
                instrucao.executeQuery().withCloseable { resultado ->
                    if (!resultado.next()) {
                        return null
                    }
                    return montarMapaVaga(conexao, resultado, idVaga)
                }
            }
        }
    }

    @Override

    List<Map<String, Object>> listarPorEmpresa(String cnpjEmpresa) {
        String sql = '''
            SELECT id_vaga, titulo, pais, estado, descricao, cnpj_empresa, area_atuacao
            FROM vaga
            WHERE cnpj_empresa = ?
            ORDER BY id_vaga DESC
        '''
        dataSource.connection.withCloseable { Connection conexao ->
            List<Map<String, Object>> vagas = []
            conexao.prepareStatement(sql).withCloseable { instrucao ->
                instrucao.setString(1, cnpjEmpresa)
                instrucao.executeQuery().withCloseable { resultado ->
                    while (resultado.next()) {
                        int idVaga = resultado.getInt('id_vaga')
                        vagas << montarMapaVaga(conexao, resultado, idVaga)
                    }
                }
            }
            return vagas
        }
    }

    @Override
    Map<String, Object> inserirVaga(Map<String, Object> vaga) {
        String sql = '''
            INSERT INTO vaga (titulo, pais, estado, descricao, cnpj_empresa, area_atuacao)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id_vaga
        '''
        emTransacao { Connection conexao ->
            int idVaga = conexao.prepareStatement(sql).withCloseable { instrucao ->
                instrucao.setString(1, vaga.titulo as String)
                instrucao.setString(2, vaga.pais as String)
                instrucao.setString(3, vaga.estado as String)
                instrucao.setString(4, vaga.descricao as String)
                instrucao.setString(5, vaga.cnpjEmpresa as String)
                instrucao.setString(6, vaga.areaAtuacao as String)
                instrucao.executeQuery().withCloseable { resultado ->
                    if (!resultado.next()) {
                        throw new SQLException('Nao foi possivel obter o id da vaga criada.')
                    }
                    return resultado.getInt('id_vaga')
                }
            }

            List<String> competencias = (vaga.competencias ?: []) as List<String>
            inserirVinculosCompetencias(conexao, competencias, idVaga)

            return [
                    idVaga      : idVaga,
                    titulo      : vaga.titulo,
                    pais        : vaga.pais,
                    estado      : vaga.estado,
                    descricao   : vaga.descricao,
                    cnpjEmpresa : vaga.cnpjEmpresa,
                    areaAtuacao : vaga.areaAtuacao,
                    competencias: competencias
            ] as Map<String, Object>
        }
    }

    @Override
    Map<String, Object> atualizarVaga(int idVaga, Map<String, Object> vaga) {
        String sql = '''
            UPDATE vaga
            SET titulo = ?, pais = ?, estado = ?, descricao = ?, area_atuacao = ?
            WHERE id_vaga = ?
        '''
        emTransacao { Connection conexao ->
            int afetadas = conexao.prepareStatement(sql).withCloseable { instrucao ->
                instrucao.setString(1, vaga.titulo as String)
                instrucao.setString(2, vaga.pais as String)
                instrucao.setString(3, vaga.estado as String)
                instrucao.setString(4, vaga.descricao as String)
                instrucao.setString(5, vaga.areaAtuacao as String)
                instrucao.setInt(6, idVaga)
                return instrucao.executeUpdate()
            }
            if (afetadas == 0) {
                return null
            }

            if (vaga.containsKey('competencias')) {
                conexao.prepareStatement('DELETE FROM vaga_competencia WHERE id_vaga = ?').withCloseable { del ->
                    del.setInt(1, idVaga)
                    del.executeUpdate()
                }
                List<String> competencias = (vaga.competencias ?: []) as List<String>
                inserirVinculosCompetencias(conexao, competencias, idVaga)
            }

            return [
                    idVaga      : idVaga,
                    titulo      : vaga.titulo,
                    pais        : vaga.pais,
                    estado      : vaga.estado,
                    descricao   : vaga.descricao,
                    areaAtuacao : vaga.areaAtuacao,
                    competencias: buscarCompetenciasVaga(conexao, idVaga)
            ] as Map<String, Object>
        }
    }

    @Override
    boolean excluirVaga(int idVaga) {
        emTransacao { Connection conexao ->
            conexao.prepareStatement('DELETE FROM vaga_competencia WHERE id_vaga = ?').withCloseable { del ->
                del.setInt(1, idVaga)
                del.executeUpdate()
            }
            int afetadas = conexao.prepareStatement('DELETE FROM vaga WHERE id_vaga = ?').withCloseable { del ->
                del.setInt(1, idVaga)
                return del.executeUpdate()
            }
            return afetadas > 0
        }
    }

    private static Map<String, Object> montarMapaVaga(Connection conexao, def resultado, int idVaga) {
        return [
                idVaga      : idVaga,
                titulo      : resultado.getString('titulo'),
                pais        : resultado.getString('pais'),
                estado      : resultado.getString('estado'),
                descricao   : resultado.getString('descricao'),
                cnpjEmpresa : resultado.getString('cnpj_empresa'),
                areaAtuacao : resultado.getString('area_atuacao'),
                competencias: buscarCompetenciasVaga(conexao, idVaga)
        ] as Map<String, Object>
    }

    private static List<String> buscarCompetenciasVaga(Connection conexao, int idVaga) {
        String sql = '''
            SELECT c.nome
            FROM competencia c
            INNER JOIN vaga_competencia vc ON vc.id_competencia = c.id_competencia
            WHERE vc.id_vaga = ?
            ORDER BY c.nome
        '''
        conexao.prepareStatement(sql).withCloseable { instrucao ->
            instrucao.setInt(1, idVaga)
            instrucao.executeQuery().withCloseable { resultado ->
                List<String> lista = []
                while (resultado.next()) {
                    lista << resultado.getString('nome')
                }
                return lista
            }
        }
    }

    private static void inserirVinculosCompetencias(Connection conexao, Collection<String> competencias, int idVaga) {
        String sqlVinculo = 'INSERT INTO vaga_competencia (id_vaga, id_competencia) VALUES (?, ?) ON CONFLICT DO NOTHING'
        competencias.each { String competencia ->
            String nomePadronizado = obterNomePadronizado(conexao, competencia)
            long idCompetencia = garantirEObterIdCompetencia(conexao, nomePadronizado)
            conexao.prepareStatement(sqlVinculo).withCloseable { instrucao ->
                instrucao.setInt(1, idVaga)
                instrucao.setLong(2, idCompetencia)
                instrucao.executeUpdate()
            }
        }
    }

    private static String obterNomePadronizado(Connection conexao, String competencia) {
        String nomeLimpo = competencia.trim()
        String sql = 'SELECT nome FROM competencia WHERE lower(nome) = lower(?) LIMIT 1'
        conexao.prepareStatement(sql).withCloseable { instrucao ->
            instrucao.setString(1, nomeLimpo)
            instrucao.executeQuery().withCloseable { resultado ->
                if (resultado.next()) {
                    return resultado.getString('nome')
                }
            }
        }
        return nomeLimpo
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