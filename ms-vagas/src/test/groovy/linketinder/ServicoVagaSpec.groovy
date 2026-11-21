package linketinder.vagas.model

import spock.lang.Specification

class ServicoVagaSpec extends Specification {

    RepositorioVaga repositorio
    ServicoVaga servico

    def setup() {
        repositorio = Mock(RepositorioVaga)
        servico = new ServicoVaga(repositorio)
    }

    private Map<String, Object> dadosCadastro(Map adicionais = [:]) {
        Map<String, Object> dados = [
                cnpjEmpresa : '12345678000199',
                titulo      : 'Desenvolvedor Java',
                pais        : 'Brasil',
                estado      : 'Pernambuco',
                descricao   : 'Desenvolvimento de aplicações.',
                areaAtuacao : 'TI',
                competencias: ['Java', 'Groovy']
        ]

        dados.putAll(adicionais)
        return dados
    }

    private Map<String, Object> dadosAtualizacao(Map adicionais = [:]) {
        Map<String, Object> dados = [
                titulo      : 'Desenvolvedor Backend',
                pais        : 'Brasil',
                estado      : 'Pernambuco',
                descricao   : 'Desenvolvimento de sistemas.',
                areaAtuacao : 'TI'
        ]

        dados.putAll(adicionais)
        return dados
    }

    // ---------------------------------------------------------
    // LISTAGEM
    // ---------------------------------------------------------

    def "deve listar todas as vagas"() {
        given:
        def vagas = [
                [idVaga: 1, titulo: 'Desenvolvedor Java'],
                [idVaga: 2, titulo: 'Analista de Sistemas']
        ]

        when:
        def resultado = servico.listarVagas()

        then:
        1 * repositorio.listarVagas() >> vagas

        resultado == vagas
    }

    // ---------------------------------------------------------
    // BUSCA POR ID
    // ---------------------------------------------------------

    def "deve buscar uma vaga pelo ID"() {
        given:
        int idVaga = 10
        def vaga = [idVaga: 10, titulo: 'Desenvolvedor Java']

        when:
        def resultado = servico.buscarPorId(idVaga)

        then:
        1 * repositorio.buscarPorId(idVaga) >> vaga

        resultado == vaga
    }

    def "nao deve buscar vaga com ID invalido"() {
        when:
        servico.buscarPorId(idVaga)

        then:
        thrown(VagaInvalidoException)

        0 * repositorio.buscarPorId(_)

        where:
        idVaga << [0, -1, -10]
    }

    def "deve rejeitar busca por vaga inexistente"() {
        when:
        servico.buscarPorId(999)

        then:
        1 * repositorio.buscarPorId(999) >> null

        thrown(VagaInvalidoException)
    }

    // ---------------------------------------------------------
    // LISTAGEM POR EMPRESA
    // ---------------------------------------------------------

    def "deve listar vagas de uma empresa"() {
        given:
        String cnpj = '12345678000199'

        def vagas = [
                [idVaga: 1, titulo: 'Desenvolvedor Java']
        ]

        when:
        def resultado = servico.listarPorEmpresa(cnpj)

        then:
        1 * repositorio.listarPorEmpresa(cnpj) >> vagas

        resultado == vagas
    }

    def "deve rejeitar CNPJ vazio ou com mais de 18 caracteres"() {
        when:
        servico.listarPorEmpresa(cnpj)

        then:
        thrown(VagaInvalidoException)

        where:
        cnpj << [null, '', '   ', '1234567890123456789']
    }

    // ---------------------------------------------------------
    // CADASTRO
    // ---------------------------------------------------------

    def "deve cadastrar uma vaga com dados validos"() {
        given:
        def dados = dadosCadastro()

        def vagaCriada = [
                idVaga      : 10,
                titulo      : 'Desenvolvedor Java',
                pais        : 'Brasil',
                estado      : 'Pernambuco',
                competencias: ['Java', 'Groovy']
        ]

        when:
        def resultado = servico.cadastrarVaga(dados)

        then:
        1 * repositorio.inserirVaga({ Map recebidos ->
            recebidos.titulo == 'Desenvolvedor Java' &&
                    recebidos.cnpjEmpresa == '12345678000199' &&
                    recebidos.competencias == ['Java', 'Groovy']
        }) >> vagaCriada

        resultado == vagaCriada
    }

    def "deve remover competencias duplicadas ignorando maiusculas e minusculas"() {
        given:
        def dados = dadosCadastro([
                competencias: [
                        'Java',
                        ' java ',
                        'Groovy',
                        'GROOVY',
                        'Python'
                ]
        ])

        when:
        servico.cadastrarVaga(dados)

        then:
        1 * repositorio.inserirVaga({ Map recebidos ->
            recebidos.competencias == ['Java', 'Groovy', 'Python']
        }) >> [:]
    }

    def "deve remover espacos desnecessarios dos dados da vaga"() {
        given:
        def dados = dadosCadastro([
                titulo: '  Desenvolvedor Java  ',
                pais: ' Brasil ',
                estado: ' Pernambuco ',
                descricao: ' Descricao da vaga ',
                areaAtuacao: ' TI ',
                competencias: [' Java ', ' Groovy ']
        ])

        when:
        servico.cadastrarVaga(dados)

        then:
        1 * repositorio.inserirVaga({ Map recebidos ->
            recebidos.titulo == 'Desenvolvedor Java' &&
                    recebidos.pais == 'Brasil' &&
                    recebidos.estado == 'Pernambuco' &&
                    recebidos.descricao == 'Descricao da vaga' &&
                    recebidos.areaAtuacao == 'TI' &&
                    recebidos.competencias == ['Java', 'Groovy']
        }) >> [:]
    }

    def "deve rejeitar cadastro quando faltar um campo obrigatorio"() {
        given:
        def dados = dadosCadastro()
        dados.remove(campo)

        when:
        servico.cadastrarVaga(dados)

        then:
        thrown(VagaInvalidoException)

        0 * repositorio.inserirVaga(_)

        where:
        campo << [
                'cnpjEmpresa',
                'titulo',
                'pais',
                'estado',
                'descricao',
                'areaAtuacao',
                'competencias'
        ]
    }

    def "deve rejeitar campos que ultrapassem seus limites"() {
        given:
        def dados = dadosCadastro([(campo): 'A' * tamanho])

        when:
        servico.cadastrarVaga(dados)

        then:
        thrown(VagaInvalidoException)

        0 * repositorio.inserirVaga(_)

        where:
        campo         | tamanho
        'titulo'      | 256
        'pais'        | 101
        'estado'      | 101
        'cnpjEmpresa' | 19
        'areaAtuacao' | 151
    }

    // ---------------------------------------------------------
    // VALIDAÇÃO DE COMPETÊNCIAS
    // ---------------------------------------------------------

    def "deve rejeitar competencias ausentes, vazias ou invalidas"() {
        given:
        def dados = dadosCadastro([
                competencias: valor
        ])

        when:
        servico.cadastrarVaga(dados)

        then:
        thrown(VagaInvalidoException)

        0 * repositorio.inserirVaga(_)

        where:
        valor << [
                null,
                [],
                'Java',
                ['Java', 123],
                ['Java', null],
                ['Java', '   ']
        ]
    }

    // ---------------------------------------------------------
    // ATUALIZAÇÃO
    // ---------------------------------------------------------

    def "deve atualizar uma vaga sem alterar suas competencias"() {
        given:
        int idVaga = 10
        def dados = dadosAtualizacao()

        def vagaAtualizada = [
                idVaga: 10,
                titulo: 'Desenvolvedor Backend'
        ]

        when:
        def resultado = servico.atualizarVaga(idVaga, dados)

        then:
        1 * repositorio.atualizarVaga(idVaga, { Map recebidos ->
            recebidos.titulo == 'Desenvolvedor Backend' &&
                    !recebidos.containsKey('competencias')
        }) >> vagaAtualizada

        resultado == vagaAtualizada
    }

    def "deve atualizar uma vaga com suas competencias"() {
        given:
        int idVaga = 10

        def dados = dadosAtualizacao([
                competencias: ['Java', ' java ', 'Python']
        ])

        when:
        servico.atualizarVaga(idVaga, dados)

        then:
        1 * repositorio.atualizarVaga(idVaga, { Map recebidos ->
            recebidos.competencias == ['Java', 'Python']
        }) >> [idVaga: 10]
    }

    def "deve rejeitar atualizacao com competencias vazias"() {
        given:
        def dados = dadosAtualizacao([
                competencias: []
        ])

        when:
        servico.atualizarVaga(10, dados)

        then:
        thrown(VagaInvalidoException)

        0 * repositorio.atualizarVaga(_, _)
    }

    def "deve rejeitar atualizacao com ID invalido"() {
        when:
        servico.atualizarVaga(0, dadosAtualizacao())

        then:
        thrown(VagaInvalidoException)

        0 * repositorio.atualizarVaga(_, _)
    }

    def "deve rejeitar atualizacao de vaga inexistente"() {
        when:
        servico.atualizarVaga(999, dadosAtualizacao())

        then:
        1 * repositorio.atualizarVaga(999, _) >> null

        thrown(VagaInvalidoException)
    }

    // ---------------------------------------------------------
    // EXCLUSÃO
    // ---------------------------------------------------------

    def "deve excluir uma vaga existente"() {
        when:
        servico.excluirVaga(10)

        then:
        1 * repositorio.excluirVaga(10) >> true

        noExceptionThrown()
    }

    def "deve rejeitar exclusao com ID invalido"() {
        when:
        servico.excluirVaga(0)

        then:
        thrown(VagaInvalidoException)

        0 * repositorio.excluirVaga(_)
    }

    def "deve rejeitar exclusao de vaga inexistente"() {
        when:
        servico.excluirVaga(999)

        then:
        1 * repositorio.excluirVaga(999) >> false

        thrown(VagaInvalidoException)
    }
}