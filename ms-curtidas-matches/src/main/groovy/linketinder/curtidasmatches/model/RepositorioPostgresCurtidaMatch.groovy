package linketinder.curtidasmatches.model

import jakarta.inject.Singleton

import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

@Singleton
class RepositorioPostgresCurtidaMatch implements RepositorioCurtidaMatch {

    private final DataSource dataSource

    RepositorioPostgresCurtidaMatch(DataSource dataSource) {
        this.dataSource = dataSource
    }


    @Override
    List<Map<String, Object>> listarCurtidas() {
        String sql = '''
            SELECT id_curtida, cpf_candidato, id_vaga,
                   cnpj_empresa, recrutador_id
            FROM curtida
            ORDER BY id_curtida DESC
        '''

        dataSource.connection.withCloseable { Connection conexao ->
            List<Map<String, Object>> curtidas = []

            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    while (resultado.next()) {
                        curtidas.add(montarMapaCurtida(resultado))
                    }
                }
            }

            return curtidas
        }
    }


    @Override
    Map<String, Object> registrarCurtida(Map<String, Object> dados) {
        String sqlInserir = '''
            INSERT INTO curtida (
                cpf_candidato,
                id_vaga,
                cnpj_empresa,
                recrutador_id
            )
            VALUES (?, ?, ?, ?)
            RETURNING id_curtida
        '''

        emTransacao { Connection conexao ->

            String cpfCandidato = dados.cpfCandidato as String
            Integer idVaga = dados.idVaga as Integer
            String cnpjInformado = dados.cnpjEmpresa as String

            String cnpjResolvido = resolverCnpjEmpresa(
                    conexao,
                    cnpjInformado,
                    idVaga
            )

            if (cnpjResolvido == null) {
                throw new SQLException(
                        'Nao foi possivel identificar a empresa proprietaria da vaga.'
                )
            }

            String cnpjPersistido = cnpjInformado == null
                    ? null
                    : cnpjResolvido

            String sqlVerificar = cnpjPersistido == null
                    ? '''
                    SELECT id_curtida
                    FROM curtida
                    WHERE cpf_candidato = ?
                      AND id_vaga = ?
                      AND cnpj_empresa IS NULL
                      AND recrutador_id IS NULL
                    LIMIT 1
                '''
                    : '''
                    SELECT id_curtida
                    FROM curtida
                    WHERE cpf_candidato = ?
                      AND id_vaga = ?
                      AND cnpj_empresa = ?
                      AND recrutador_id IS NULL
                    LIMIT 1
                '''

            Integer idCurtidaExistente =
                    conexao.prepareStatement(sqlVerificar).withCloseable { PreparedStatement instrucao ->

                        instrucao.setString(1, cpfCandidato)
                        instrucao.setInt(2, idVaga)

                        if (cnpjPersistido != null) {
                            instrucao.setString(3, cnpjPersistido)
                        }

                        instrucao.executeQuery().withCloseable { ResultSet resultado ->
                            if (resultado.next()) {
                                return resultado.getInt('id_curtida')
                            }

                            return null
                        }
                    }

            int idCurtida

            if (idCurtidaExistente != null) {
                idCurtida = idCurtidaExistente
            } else {

                idCurtida = conexao.prepareStatement(sqlInserir).withCloseable { PreparedStatement instrucao ->

                    instrucao.setString(1, cpfCandidato)
                    instrucao.setInt(2, idVaga)

                    if (cnpjPersistido != null) {
                        instrucao.setString(3, cnpjPersistido)
                    } else {
                        instrucao.setNull(3, Types.VARCHAR)
                    }

                    instrucao.setNull(4, Types.INTEGER)

                    instrucao.executeQuery().withCloseable { ResultSet resultado ->
                        if (!resultado.next()) {
                            throw new SQLException(
                                    'Nao foi possivel obter o id da curtida registrada.'
                            )
                        }

                        return resultado.getInt('id_curtida')
                    }
                }
            }

            Map<String, Object> matchGerado =
                    avaliarEInserirMatchSeReciproco(
                            conexao,
                            cpfCandidato,
                            cnpjResolvido,
                            idVaga
                    )

            Map<String, Object> resposta = [
                    idCurtida   : idCurtida,
                    cpfCandidato: cpfCandidato,
                    idVaga      : idVaga,
                    cnpjEmpresa : cnpjPersistido,
                    recrutadorId: null,
                    match       : matchGerado != null
            ] as Map<String, Object>

            if (matchGerado != null) {
                resposta.dadosMatch = matchGerado
            }

            return resposta
        }
    }


    @Override
    boolean removerCurtidaCandidato(String cpfCandidato, int idVaga) {
        String sql = '''
            DELETE FROM curtida c
            WHERE c.cpf_candidato = ?
              AND c.id_vaga = ?
              AND c.cnpj_empresa IS NULL
              AND c.recrutador_id IS NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM match_vaga m
                  WHERE m.cpf_candidato = c.cpf_candidato
                    AND m.id_vaga = c.id_vaga
              )
        '''

        dataSource.connection.withCloseable { Connection conexao ->
            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.setString(1, cpfCandidato)
                instrucao.setInt(2, idVaga)

                return instrucao.executeUpdate() > 0
            }
        }
    }

    @Override
    boolean removerCurtidaEmpresa(
            String cnpjEmpresa,
            String cpfCandidato,
            int idVaga
    ) {
        String sql = '''
            DELETE FROM curtida c
            WHERE c.cnpj_empresa = ?
              AND c.cpf_candidato = ?
              AND c.id_vaga = ?
              AND c.recrutador_id IS NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM match_vaga m
                  WHERE m.cnpj_empresa = c.cnpj_empresa
                    AND m.cpf_candidato = c.cpf_candidato
                    AND m.id_vaga = c.id_vaga
              )
        '''

        dataSource.connection.withCloseable { Connection conexao ->
            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.setString(1, cnpjEmpresa)
                instrucao.setString(2, cpfCandidato)
                instrucao.setInt(3, idVaga)

                return instrucao.executeUpdate() > 0
            }
        }
    }


    @Override
    List<Map<String, Object>> listarMatches() {
        String sql = '''
            SELECT id_match, cpf_candidato, cnpj_empresa, id_vaga
            FROM match_vaga
            ORDER BY id_match DESC
        '''

        dataSource.connection.withCloseable { Connection conexao ->
            List<Map<String, Object>> matches = []

            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    while (resultado.next()) {
                        matches.add(montarMapaMatch(resultado))
                    }
                }
            }

            return matches
        }
    }

    @Override
    List<Map<String, Object>> listarMatchesPorCandidato(String cpfCandidato) {
        String sql = '''
            SELECT id_match, cpf_candidato, cnpj_empresa, id_vaga
            FROM match_vaga
            WHERE cpf_candidato = ?
            ORDER BY id_match DESC
        '''

        dataSource.connection.withCloseable { Connection conexao ->
            List<Map<String, Object>> matches = []

            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.setString(1, cpfCandidato)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    while (resultado.next()) {
                        matches.add(montarMapaMatch(resultado))
                    }
                }
            }

            return matches
        }
    }

    @Override
    List<Map<String, Object>> listarMatchesPorEmpresa(String cnpjEmpresa) {
        String sql = '''
            SELECT id_match, cpf_candidato, cnpj_empresa, id_vaga
            FROM match_vaga
            WHERE cnpj_empresa = ?
            ORDER BY id_match DESC
        '''

        dataSource.connection.withCloseable { Connection conexao ->
            List<Map<String, Object>> matches = []

            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.setString(1, cnpjEmpresa)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    while (resultado.next()) {
                        matches.add(montarMapaMatch(resultado))
                    }
                }
            }

            return matches
        }
    }


    @Override
    List<Map<String, Object>> listarStatusCurtidasCandidato(String cpfCandidato) {
        String sql = '''
            SELECT
                c.id_vaga,
                CASE
                    WHEN m.id_match IS NULL THEN 'curtido'
                    ELSE 'match'
                END AS status
            FROM curtida c
            LEFT JOIN match_vaga m
                ON m.cpf_candidato = c.cpf_candidato
               AND m.id_vaga = c.id_vaga
            WHERE c.cpf_candidato = ?
              AND c.id_vaga IS NOT NULL
              AND c.cnpj_empresa IS NULL
              AND c.recrutador_id IS NULL
            ORDER BY c.id_vaga
        '''

        dataSource.connection.withCloseable { Connection conexao ->
            List<Map<String, Object>> resultados = []

            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.setString(1, cpfCandidato)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    while (resultado.next()) {
                        resultados.add([
                                idVaga: resultado.getInt('id_vaga'),
                                status: resultado.getString('status')
                        ] as Map<String, Object>)
                    }
                }
            }

            return resultados
        }
    }

    @Override
    List<Map<String, Object>> listarStatusCurtidasEmpresa(String cnpjEmpresa) {
        String sql = '''
            SELECT
                c.cpf_candidato,
                c.id_vaga,
                CASE
                    WHEN m.id_match IS NULL THEN 'curtido'
                    ELSE 'match'
                END AS status
            FROM curtida c
            LEFT JOIN match_vaga m
                ON m.cpf_candidato = c.cpf_candidato
               AND m.cnpj_empresa = c.cnpj_empresa
               AND m.id_vaga = c.id_vaga
            WHERE c.cnpj_empresa = ?
              AND c.id_vaga IS NOT NULL
              AND c.recrutador_id IS NULL
            ORDER BY c.cpf_candidato, c.id_vaga
        '''

        dataSource.connection.withCloseable { Connection conexao ->
            List<Map<String, Object>> resultados = []

            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.setString(1, cnpjEmpresa)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    while (resultado.next()) {
                        resultados.add([
                                cpfCandidato: resultado.getString('cpf_candidato'),
                                idVaga      : resultado.getInt('id_vaga'),
                                status      : resultado.getString('status')
                        ] as Map<String, Object>)
                    }
                }
            }

            return resultados
        }
    }

    @Override
    Map<String, Object> registrarMatch(Map<String, Object> dados) {
        String sqlBusca = '''
            SELECT id_match, cpf_candidato, cnpj_empresa, id_vaga
            FROM match_vaga
            WHERE cpf_candidato = ?
              AND cnpj_empresa = ?
              AND id_vaga = ?
            LIMIT 1
        '''

        String sqlInsert = '''
            INSERT INTO match_vaga (cpf_candidato, cnpj_empresa, id_vaga)
            VALUES (?, ?, ?)
            ON CONFLICT (cpf_candidato, cnpj_empresa, id_vaga) DO NOTHING
            RETURNING id_match
        '''

        emTransacao { Connection conexao ->
            String cpf = dados.cpfCandidato as String
            String cnpj = dados.cnpjEmpresa as String
            int idVaga = dados.idVaga as int

            Integer idMatchInserido =
                    conexao.prepareStatement(sqlInsert).withCloseable { PreparedStatement instrucao ->

                        instrucao.setString(1, cpf)
                        instrucao.setString(2, cnpj)
                        instrucao.setInt(3, idVaga)

                        instrucao.executeQuery().withCloseable { ResultSet resultado ->
                            if (resultado.next()) {
                                return resultado.getInt('id_match')
                            }

                            return null
                        }
                    }

            Map<String, Object> match = conexao.prepareStatement(sqlBusca).withCloseable { PreparedStatement instrucao ->
                instrucao.setString(1, cpf)
                instrucao.setString(2, cnpj)
                instrucao.setInt(3, idVaga)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    if (resultado.next()) {
                        return montarMapaMatch(resultado)
                    }

                    return null
                }
            }

            if (match == null && idMatchInserido == null) {
                throw new SQLException('Nao foi possivel recuperar o match registrado.')
            }

            return match
        }
    }


    private static String resolverCnpjEmpresa(
            Connection conexao,
            String cnpjInformado,
            Integer idVaga
    ) {
        String sql = '''
            SELECT cnpj_empresa
            FROM vaga
            WHERE id_vaga = ?
            LIMIT 1
        '''

        String cnpjDaVaga = conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
            instrucao.setInt(1, idVaga)

            instrucao.executeQuery().withCloseable { ResultSet resultado ->
                if (resultado.next()) {
                    return resultado.getString('cnpj_empresa')
                }

                return null
            }
        }

        if (cnpjDaVaga == null) {
            return null
        }

        if (cnpjInformado != null &&
                cnpjInformado.replaceAll(/\D/, '') != cnpjDaVaga.replaceAll(/\D/, '')) {

            throw new SQLException(
                    'A vaga selecionada nao pertence a esta empresa.'
            )
        }

        return cnpjDaVaga
    }


    private static Map<String, Object> avaliarEInserirMatchSeReciproco(
            Connection conexao,
            String cpfCandidato,
            String cnpjEmpresa,
            Integer idVaga
    ) {
        if (cpfCandidato == null || cnpjEmpresa == null || idVaga == null) {
            return null
        }


        String sqlCurtidaCandidato = '''
            SELECT 1
            FROM curtida
            WHERE cpf_candidato = ?
              AND id_vaga = ?
              AND cnpj_empresa IS NULL
              AND recrutador_id IS NULL
            LIMIT 1
        '''


        String sqlCurtidaEmpresa = '''
            SELECT 1
            FROM curtida
            WHERE cpf_candidato = ?
              AND id_vaga = ?
              AND cnpj_empresa = ?
              AND recrutador_id IS NULL
            LIMIT 1
        '''

        boolean temCurtidaCandidato = existeRegistro(
                conexao,
                sqlCurtidaCandidato,
                cpfCandidato,
                idVaga
        )

        boolean temCurtidaEmpresa = existeRegistro(
                conexao,
                sqlCurtidaEmpresa,
                cpfCandidato,
                idVaga,
                cnpjEmpresa
        )

        // Sem reciprocidade, não existe match.
        if (!temCurtidaCandidato || !temCurtidaEmpresa) {
            return null
        }

        String sqlBuscaMatch = '''
            SELECT id_match, cpf_candidato, cnpj_empresa, id_vaga
            FROM match_vaga
            WHERE cpf_candidato = ?
              AND cnpj_empresa = ?
              AND id_vaga = ?
            LIMIT 1
        '''

        Map<String, Object> matchExistente =
                conexao.prepareStatement(sqlBuscaMatch).withCloseable { PreparedStatement instrucao ->

                    instrucao.setString(1, cpfCandidato)
                    instrucao.setString(2, cnpjEmpresa)
                    instrucao.setInt(3, idVaga)

                    instrucao.executeQuery().withCloseable { ResultSet resultado ->
                        if (resultado.next()) {
                            return montarMapaMatch(resultado)
                        }

                        return null
                    }
                }

        if (matchExistente != null) {
            return matchExistente
        }

        // Cria o match somente após confirmar as duas curtidas.
        String sqlInsertMatch = '''
            INSERT INTO match_vaga (
                cpf_candidato,
                cnpj_empresa,
                id_vaga
            )
            VALUES (?, ?, ?)
            ON CONFLICT (cpf_candidato, cnpj_empresa, id_vaga) DO NOTHING
            RETURNING id_match
        '''

        Integer idMatch = conexao.prepareStatement(sqlInsertMatch).withCloseable { PreparedStatement instrucao ->

            instrucao.setString(1, cpfCandidato)
            instrucao.setString(2, cnpjEmpresa)
            instrucao.setInt(3, idVaga)

            instrucao.executeQuery().withCloseable { ResultSet resultado ->
                if (resultado.next()) {
                    return resultado.getInt('id_match')
                }

                return null
            }
        }

        return conexao.prepareStatement(sqlBuscaMatch).withCloseable { PreparedStatement instrucao ->

            instrucao.setString(1, cpfCandidato)
            instrucao.setString(2, cnpjEmpresa)
            instrucao.setInt(3, idVaga)

            instrucao.executeQuery().withCloseable { ResultSet resultado ->
                if (resultado.next()) {
                    return montarMapaMatch(resultado)
                }

                return null
            }
        }
    }


    private static boolean existeRegistro(
            Connection conexao,
            String sql,
            Object... parametros
    ) {
        conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->

            parametros.eachWithIndex { Object valor, int indice ->
                instrucao.setObject(indice + 1, valor)
            }

            instrucao.executeQuery().withCloseable { ResultSet resultado ->
                return resultado.next()
            }
        }
    }


    private static Map<String, Object> montarMapaCurtida(ResultSet resultado) {
        int idVagaVal = resultado.getInt('id_vaga')
        Integer idVaga = resultado.wasNull() ? null : idVagaVal

        int recrutadorVal = resultado.getInt('recrutador_id')
        Integer recrutadorId = resultado.wasNull() ? null : recrutadorVal

        return [
                idCurtida   : resultado.getInt('id_curtida'),
                cpfCandidato: resultado.getString('cpf_candidato'),
                idVaga      : idVaga,
                cnpjEmpresa : resultado.getString('cnpj_empresa'),
                recrutadorId: recrutadorId
        ] as Map<String, Object>
    }

    private static Map<String, Object> montarMapaMatch(ResultSet resultado) {
        return [
                idMatch     : resultado.getInt('id_match'),
                cpfCandidato: resultado.getString('cpf_candidato'),
                cnpjEmpresa : resultado.getString('cnpj_empresa'),
                idVaga      : resultado.getInt('id_vaga')
        ] as Map<String, Object>
    }


    private <T> T emTransacao(Closure<T> operacao) {
        dataSource.connection.withCloseable { Connection conexao ->

            conexao.autoCommit = false

            try {
                T resultado = operacao.call(conexao)
                conexao.commit()

                return resultado

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

    @Override
    List<Map<String, Object>> listarMatchesCompletosPorCandidato(String cpfCandidato) {
        String sql = '''
        SELECT
            m.id_match,
            m.cpf_candidato,
            c.nome AS nome_candidato,
            e.cnpj AS cnpj_empresa,
            e.nome AS nome_empresa,
            v.id_vaga,
            v.titulo AS titulo_vaga,
            v.pais AS pais_vaga,
            v.estado AS estado_vaga
        FROM match_vaga m
        JOIN candidato c ON c.cpf = m.cpf_candidato
        JOIN empresa e ON e.cnpj = m.cnpj_empresa
        JOIN vaga v ON v.id_vaga = m.id_vaga
        WHERE m.cpf_candidato = ?
        ORDER BY m.id_match DESC
    '''

        dataSource.connection.withCloseable { Connection conexao ->
            List<Map<String, Object>> matches = []

            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.setString(1, cpfCandidato)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    while (resultado.next()) {
                        matches.add(montarMapaMatchCompleto(resultado))
                    }
                }
            }

            return matches
        }
    }

    @Override
    List<Map<String, Object>> listarCurtidasPendentesPorCandidato(String cpfCandidato) {
        String sql = '''
        SELECT
            v.id_vaga,
            v.titulo AS titulo_vaga,
            v.pais AS pais_vaga,
            v.estado AS estado_vaga
        FROM curtida cu
        JOIN vaga v ON v.id_vaga = cu.id_vaga
        WHERE cu.cpf_candidato = ?
          AND cu.cnpj_empresa IS NULL
          AND cu.recrutador_id IS NULL
          AND cu.id_vaga IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
              FROM match_vaga m
              WHERE m.cpf_candidato = cu.cpf_candidato
                AND m.id_vaga = cu.id_vaga
          )
        ORDER BY cu.id_curtida DESC
    '''

        dataSource.connection.withCloseable { Connection conexao ->
            List<Map<String, Object>> curtidas = []

            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.setString(1, cpfCandidato)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    while (resultado.next()) {
                        curtidas.add([
                                vaga: montarMapaVaga(resultado)
                        ] as Map<String, Object>)
                    }
                }
            }

            return curtidas
        }
    }

    @Override
    List<Map<String, Object>> listarMatchesCompletosPorEmpresa(String cnpjEmpresa) {
        String sql = '''
        SELECT
            m.id_match,
            m.cpf_candidato,
            c.nome AS nome_candidato,
            e.cnpj AS cnpj_empresa,
            e.nome AS nome_empresa,
            v.id_vaga,
            v.titulo AS titulo_vaga,
            v.pais AS pais_vaga,
            v.estado AS estado_vaga
        FROM match_vaga m
        JOIN candidato c ON c.cpf = m.cpf_candidato
        JOIN empresa e ON e.cnpj = m.cnpj_empresa
        JOIN vaga v ON v.id_vaga = m.id_vaga
        WHERE m.cnpj_empresa = ?
        ORDER BY m.id_match DESC
    '''

        dataSource.connection.withCloseable { Connection conexao ->
            List<Map<String, Object>> matches = []

            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.setString(1, cnpjEmpresa)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    while (resultado.next()) {
                        matches.add(montarMapaMatchCompleto(resultado))
                    }
                }
            }

            return matches
        }
    }

    @Override
    List<Map<String, Object>> listarCurtidasPendentesPorEmpresa(String cnpjEmpresa) {
        String sql = '''
        SELECT
            cu.cpf_candidato,
            v.id_vaga,
            v.titulo AS titulo_vaga,
            v.pais AS pais_vaga,
            v.estado AS estado_vaga
        FROM curtida cu
        JOIN vaga v ON v.id_vaga = cu.id_vaga
        WHERE cu.cnpj_empresa = ?
          AND cu.recrutador_id IS NULL
          AND cu.id_vaga IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
              FROM match_vaga m
              WHERE m.cnpj_empresa = cu.cnpj_empresa
                AND m.cpf_candidato = cu.cpf_candidato
                AND m.id_vaga = cu.id_vaga
          )
        ORDER BY cu.id_curtida DESC
    '''

        dataSource.connection.withCloseable { Connection conexao ->
            List<Map<String, Object>> curtidas = []

            conexao.prepareStatement(sql).withCloseable { PreparedStatement instrucao ->
                instrucao.setString(1, cnpjEmpresa)

                instrucao.executeQuery().withCloseable { ResultSet resultado ->
                    while (resultado.next()) {
                        curtidas.add([
                                candidato: [
                                        competencias: []
                                ],
                                vaga: montarMapaVaga(resultado)
                        ] as Map<String, Object>)
                    }
                }
            }

            return curtidas
        }
    }
    private static Map<String, Object> montarMapaMatchCompleto(ResultSet resultado) {
        return [
                idMatch: resultado.getInt('id_match'),

                candidato: [
                        cpf: resultado.getString('cpf_candidato'),
                        nome: resultado.getString('nome_candidato'),
                        competencias: []
                ],

                empresa: [
                        cnpj: resultado.getString('cnpj_empresa'),
                        nome: resultado.getString('nome_empresa')
                ],

                vaga: [
                        id: resultado.getInt('id_vaga'),
                        titulo: resultado.getString('titulo_vaga'),
                        pais: resultado.getString('pais_vaga'),
                        estado: resultado.getString('estado_vaga')
                ]
        ] as Map<String, Object>
    }

    private static Map<String, Object> montarMapaVaga(ResultSet resultado) {
        return [
                id: resultado.getInt('id_vaga'),
                titulo: resultado.getString('titulo_vaga'),
                pais: resultado.getString('pais_vaga'),
                estado: resultado.getString('estado_vaga')
        ] as Map<String, Object>
    }
}