package linketinder.competencias.model

import spock.lang.Specification
import spock.lang.Unroll

class ServicoCompetenciaSpec extends Specification {

    RepositorioCompetencia repositorio
    ServicoCompetencia servico

    def setup() {
        repositorio = Mock(RepositorioCompetencia)
        servico = new ServicoCompetencia(repositorio)
    }

    // ==========================================
    // LISTAGEM DE COMPETÊNCIAS
    // ==========================================

    def "deve listar todas as competencias"() {
        given:
        List<Map<String, Object>> competencias = [
                [idCompetencia: 1, nome: 'Java'],
                [idCompetencia: 2, nome: 'Python']
        ]

        when:
        def resultado = servico.listarCompetencias()

        then:
        1 * repositorio.listarCompetencias() >> competencias

        resultado == competencias
    }

    // ==========================================
    // CADASTRO DE COMPETÊNCIAS
    // ==========================================

    def "deve cadastrar competencia com nome valido"() {
        given:
        Map<String, Object> dados = [nome: ' Java ']

        Map<String, Object> criada = [
                idCompetencia: 1,
                nome: 'Java'
        ]

        when:
        def resultado = servico.cadastrarCompetencia(dados)

        then:
        1 * repositorio.inserirCompetencia('Java') >> criada

        resultado == criada
    }

    def "deve aceitar competencia com exatamente 255 caracteres"() {
        given:
        String nome = 'J' * 255

        when:
        servico.cadastrarCompetencia([nome: nome])

        then:
        1 * repositorio.inserirCompetencia(nome)
    }

    @Unroll
    def "deve rejeitar cadastro com dados invalidos: #dados"() {
        when:
        servico.cadastrarCompetencia(dados)

        then:
        thrown(CompetenciaInvalidoException)

        0 * repositorio._

        where:
        dados << [
                null,
                [:],
                [nome: null],
                [nome: ''],
                [nome: '   '],
                [nome: 123],
                [nome: 'J' * 256]
        ]
    }

    // ==========================================
    // COMPETÊNCIAS DO CANDIDATO
    // ==========================================

    def "deve listar competencias de um candidato"() {
        given:
        String cpf = '12345678901'

        List<Map<String, Object>> competencias = [
                [idCompetencia: 1, nome: 'Java']
        ]

        when:
        def resultado = servico.listarPorCandidato(" ${cpf} ")

        then:
        1 * repositorio.listarPorCandidato(cpf) >> competencias

        resultado == competencias
    }

    @Unroll
    def "deve rejeitar consulta de candidato com CPF invalido: #cpf"() {
        when:
        servico.listarPorCandidato(cpf)

        then:
        thrown(CompetenciaInvalidoException)

        0 * repositorio._

        where:
        cpf << [
                null,
                '',
                '   ',
                '123456789012345'
        ]
    }

    def "deve vincular competencia a um candidato"() {
        given:
        Map<String, Object> dados = [
                cpfCandidato: ' 12345678901 ',
                nome: ' Java '
        ]

        when:
        def resultado = servico.vincularCandidato(dados)

        then:
        1 * repositorio.vincularCandidato(
                '12345678901',
                'Java'
        )

        resultado == [
                cpfCandidato: '12345678901',
                competencia: 'Java'
        ]
    }

    @Unroll
    def "deve rejeitar vinculo de candidato com dados invalidos: #dados"() {
        when:
        servico.vincularCandidato(dados)

        then:
        thrown(CompetenciaInvalidoException)

        0 * repositorio._

        where:
        dados << [
                null,
                [:],
                [cpfCandidato: '', nome: 'Java'],
                [cpfCandidato: '12345678901', nome: ''],
                [cpfCandidato: '12345678901', nome: 123],
                [cpfCandidato: '123456789012345', nome: 'Java']
        ]
    }

    // ==========================================
    // CONSULTA EM LOTE DE CANDIDATOS
    // ==========================================

    def "deve consultar competencias de varios candidatos removendo CPFs duplicados"() {
        given:
        Map<String, Object> dados = [
                cpfs: [
                        '123.456.789-01',
                        '12345678901',
                        '987.654.321-00'
                ]
        ]

        Map<String, List<Map<String, Object>>> competencias = [
                '12345678901': [
                        [idCompetencia: 1, nome: 'Java']
                ],
                '98765432100': [
                        [idCompetencia: 2, nome: 'Python']
                ]
        ]

        when:
        def resultado = servico.listarPorCandidatos(dados)

        then:
        1 * repositorio.listarPorCandidatos([
                '12345678901',
                '98765432100'
        ]) >> competencias

        resultado == competencias
    }

    def "deve retornar mapa vazio quando a lista de CPFs estiver vazia"() {
        when:
        def resultado = servico.listarPorCandidatos([cpfs: []])

        then:
        resultado == [:]

        0 * repositorio._
    }

    @Unroll
    def "deve rejeitar consulta em lote com estrutura invalida: #dados"() {
        when:
        servico.listarPorCandidatos(dados)

        then:
        thrown(CompetenciaInvalidoException)

        0 * repositorio._

        where:
        dados << [
                null,
                [:],
                [cpfs: null],
                [cpfs: '12345678901']
        ]
    }

    @Unroll
    def "deve rejeitar CPF invalido na consulta em lote: #cpf"() {
        when:
        servico.listarPorCandidatos([cpfs: [cpf]])

        then:
        thrown(CompetenciaInvalidoException)

        0 * repositorio._

        where:
        cpf << [
                null,
                '',
                '   ',
                12345678901,
                '1234567890',
                '123456789012',
                '123456789012345'
        ]
    }

    def "deve rejeitar consulta com mais de 500 CPFs"() {
        given:
        List<String> cpfs = ['12345678901'] * 501

        when:
        servico.listarPorCandidatos([cpfs: cpfs])

        then:
        thrown(CompetenciaInvalidoException)

        0 * repositorio._
    }

    // ==========================================
    // COMPETÊNCIAS DA EMPRESA
    // ==========================================

    def "deve listar competencias de uma empresa"() {
        given:
        String cnpj = '12345678000199'

        List<Map<String, Object>> competencias = [
                [idCompetencia: 1, nome: 'Java'],
                [idCompetencia: 2, nome: 'PostgreSQL']
        ]

        when:
        def resultado = servico.listarPorEmpresa(" ${cnpj} ")

        then:
        1 * repositorio.listarPorEmpresa(cnpj) >> competencias

        resultado == competencias
    }

    @Unroll
    def "deve rejeitar consulta de empresa com CNPJ invalido: #cnpj"() {
        when:
        servico.listarPorEmpresa(cnpj)

        then:
        thrown(CompetenciaInvalidoException)

        0 * repositorio._

        where:
        cnpj << [
                null,
                '',
                '   ',
                '1234567890123456789'
        ]
    }

    def "deve vincular competencia a uma empresa"() {
        given:
        Map<String, Object> dados = [
                cnpjEmpresa: ' 12345678000199 ',
                nome: ' PostgreSQL '
        ]

        when:
        def resultado = servico.vincularEmpresa(dados)

        then:
        1 * repositorio.vincularEmpresa(
                '12345678000199',
                'PostgreSQL'
        )

        resultado == [
                cnpjEmpresa: '12345678000199',
                competencia: 'PostgreSQL'
        ]
    }

    @Unroll
    def "deve rejeitar vinculo de empresa com dados invalidos: #dados"() {
        when:
        servico.vincularEmpresa(dados)

        then:
        thrown(CompetenciaInvalidoException)

        0 * repositorio._

        where:
        dados << [
                null,
                [:],
                [cnpjEmpresa: '', nome: 'Java'],
                [cnpjEmpresa: '12345678000199', nome: ''],
                [cnpjEmpresa: '12345678000199', nome: 123]
        ]
    }

    // ==========================================
    // COMPETÊNCIAS DA VAGA
    // ==========================================

    def "deve listar competencias de uma vaga"() {
        given:
        int idVaga = 10

        List<Map<String, Object>> competencias = [
                [idCompetencia: 1, nome: 'Java'],
                [idCompetencia: 2, nome: 'Spring']
        ]

        when:
        def resultado = servico.listarPorVaga(idVaga)

        then:
        1 * repositorio.listarPorVaga(idVaga) >> competencias

        resultado == competencias
    }

    @Unroll
    def "deve rejeitar consulta de vaga com ID invalido: #idVaga"() {
        when:
        servico.listarPorVaga(idVaga)

        then:
        thrown(CompetenciaInvalidoException)

        0 * repositorio._

        where:
        idVaga << [
                0,
                -1,
                -10
        ]
    }

    def "deve vincular competencia a uma vaga"() {
        given:
        Map<String, Object> dados = [
                idVaga: 10,
                nome: ' Spring '
        ] as Map<String, Object>

        when:
        def resultado = servico.vincularVaga(dados)

        then:
        1 * repositorio.vincularVaga(10, 'Spring')

        resultado == [
                idVaga: 10,
                competencia: 'Spring'
        ]
    }

    @Unroll
    def "deve rejeitar vinculo de vaga com ID invalido: #idVaga"() {
        when:
        servico.vincularVaga([
                idVaga: idVaga,
                nome: 'Java'
        ])

        then:
        thrown(CompetenciaInvalidoException)

        0 * repositorio._

        where:
        idVaga << [
                null,
                '10',
                0,
                -1,
                1.5
        ]
    }

    def "deve rejeitar vinculo de vaga sem nome de competencia"() {
        when:
        servico.vincularVaga([
                idVaga: 10,
                nome: ' '
        ])

        then:
        thrown(CompetenciaInvalidoException)

        0 * repositorio._
    }
}