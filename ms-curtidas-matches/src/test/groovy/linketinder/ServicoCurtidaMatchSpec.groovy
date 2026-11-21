package linketinder.curtidasmatches.model

import spock.lang.Specification
import spock.lang.Unroll

class ServicoCurtidaMatchSpec extends Specification {

    RepositorioCurtidaMatch repositorio
    ServicoCurtidaMatch servico

    def setup() {
        repositorio = Mock(RepositorioCurtidaMatch)
        servico = new ServicoCurtidaMatch(repositorio)
    }

    def "listarCurtidas delega a consulta ao repositorio"() {
        given:
        List<Map<String, Object>> curtidasEsperadas = [[idCurtida: 10, cpfCandidato: '12345678901']]

        when:
        List<Map<String, Object>> resultado = servico.listarCurtidas()

        then:
        1 * repositorio.listarCurtidas() >> curtidasEsperadas
        resultado == curtidasEsperadas
    }

    def "registrarCurtida normaliza os dados de uma curtida de candidato"() {
        given:
        Map<String, Object> dados = [
                cpfCandidato: '123.456.789-01',
                idVaga: 25,
                cnpjEmpresa: null
        ]
        Map<String, Object> resposta = [idCurtida: 1, match: false]

        when:
        Map<String, Object> resultado = servico.registrarCurtida(dados)

        then:
        1 * repositorio.registrarCurtida({ Map<String, Object> payload ->
            payload == [cpfCandidato: '12345678901', idVaga: 25, cnpjEmpresa: null]
        }) >> resposta
        resultado == resposta
    }

    def "registrarCurtida normaliza CPF e CNPJ de uma curtida de empresa"() {
        given:
        Map<String, Object> dados = [
                cpfCandidato: '123.456.789-01',
                idVaga: '25',
                cnpjEmpresa: '12.345.678/0001-90'
        ]
        Map<String, Object> resposta = [idCurtida: 2, match: true]

        when:
        Map<String, Object> resultado = servico.registrarCurtida(dados)

        then:
        1 * repositorio.registrarCurtida({ Map<String, Object> payload ->
            payload == [cpfCandidato: '12345678901', idVaga: 25, cnpjEmpresa: '12345678000190']
        }) >> resposta
        resultado == resposta
    }

    def "registrarCurtida converte CNPJ vazio em null"() {
        when:
        servico.registrarCurtida([
                cpfCandidato: '12345678901',
                idVaga: 3,
                cnpjEmpresa: '   '
        ])

        then:
        1 * repositorio.registrarCurtida({ it.cnpjEmpresa == null }) >> [idCurtida: 3]
    }

    @Unroll
    def "registrarCurtida rejeita CPF inválido: #cpf"() {
        when:
        servico.registrarCurtida([cpfCandidato: cpf, idVaga: 1])

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        cpf << [null, '', '   ', '123456789012345']
    }

    @Unroll
    def "registrarCurtida rejeita CNPJ inválido: #cnpj"() {
        when:
        servico.registrarCurtida([cpfCandidato: '12345678901', idVaga: 1, cnpjEmpresa: cnpj])

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        cnpj << [12345678000190, '1234567800019012345']
    }

    @Unroll
    def "registrarCurtida rejeita ID de vaga inválido: #idVaga"() {
        when:
        servico.registrarCurtida([cpfCandidato: '12345678901', idVaga: idVaga])

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        idVaga << [null, 0, -1, 'abc', '', '0', '-2', true]
    }

    def "removerCurtidaCandidato normaliza CPF e retorna o resultado do repositorio"() {
        when:
        boolean resultado = servico.removerCurtidaCandidato('123.456.789-01', 8)

        then:
        1 * repositorio.removerCurtidaCandidato('12345678901', 8) >> true
        resultado
    }

    def "removerCurtidaEmpresa normaliza CNPJ e CPF e retorna o resultado do repositorio"() {
        when:
        boolean resultado = servico.removerCurtidaEmpresa(
                '12.345.678/0001-90',
                '123.456.789-01',
                8
        )

        then:
        1 * repositorio.removerCurtidaEmpresa('12345678000190', '12345678901', 8) >> true
        resultado
    }

    @Unroll
    def "removerCurtidaCandidato rejeita CPF inválido: #cpf"() {
        when:
        servico.removerCurtidaCandidato(cpf, 1)

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        cpf << [null, '', '   ', '123456789012345']
    }

    @Unroll
    def "removerCurtidaCandidato rejeita ID de vaga não positivo: #idVaga"() {
        when:
        servico.removerCurtidaCandidato('12345678901', idVaga)

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        idVaga << [0, -1]
    }

    @Unroll
    def "removerCurtidaEmpresa rejeita CNPJ inválido: #cnpj"() {
        when:
        servico.removerCurtidaEmpresa(cnpj, '12345678901', 1)

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        cnpj << [null, '', '   ', '1234567800019012345']
    }

    @Unroll
    def "removerCurtidaEmpresa rejeita CPF inválido: #cpf"() {
        when:
        servico.removerCurtidaEmpresa('12345678000190', cpf, 1)

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        cpf << [null, '', '   ', '123456789012345']
    }

    @Unroll
    def "removerCurtidaEmpresa rejeita ID de vaga não positivo: #idVaga"() {
        when:
        servico.removerCurtidaEmpresa('12345678000190', '12345678901', idVaga)

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        idVaga << [0, -1]
    }

    def "listarMatches consulta por CPF quando o CPF é informado"() {
        given:
        List<Map<String, Object>> matchesEsperados = [[idMatch: 1]]

        when:
        def resultado = servico.listarMatches('123.456.789-01', null)

        then:
        1 * repositorio.listarMatchesPorCandidato('12345678901') >> matchesEsperados
        0 * repositorio._
        resultado == matchesEsperados
    }

    def "listarMatches consulta por CNPJ quando não há CPF informado"() {
        given:
        List<Map<String, Object>> matchesEsperados = [[idMatch: 2]]

        when:
        def resultado = servico.listarMatches(null, '12.345.678/0001-90')

        then:
        1 * repositorio.listarMatchesPorEmpresa('12345678000190') >> matchesEsperados
        0 * repositorio._
        resultado == matchesEsperados
    }

    def "listarMatches consulta todos quando CPF e CNPJ não são informados"() {
        given:
        List<Map<String, Object>> matchesEsperados = [[idMatch: 3]]

        when:
        def resultado = servico.listarMatches()

        then:
        1 * repositorio.listarMatches() >> matchesEsperados
        resultado == matchesEsperados
    }

    def "listarMatches ignora CNPJ quando CPF e CNPJ são informados juntos"() {
        when:
        def resultado = servico.listarMatches('12345678901', '12345678000190')

        then:
        1 * repositorio.listarMatchesPorCandidato('12345678901') >> []
        0 * repositorio.listarMatchesPorEmpresa(_)
        resultado == []
    }

    def "listarMatches trata CPF em branco como não informado e consulta todos sem CNPJ"() {
        when:
        def resultado = servico.listarMatches('   ', null)

        then:
        1 * repositorio.listarMatches() >> []
        resultado == []
    }

    def "listarStatusCurtidasCandidato normaliza CPF"() {
        given:
        List<Map<String, Object>> statusEsperado = [[idVaga: 4, status: 'PENDENTE']]

        when:
        def resultado = servico.listarStatusCurtidasCandidato('123.456.789-01')

        then:
        1 * repositorio.listarStatusCurtidasCandidato('12345678901') >> statusEsperado
        resultado == statusEsperado
    }

    def "listarStatusCurtidasEmpresa normaliza CNPJ"() {
        given:
        List<Map<String, Object>> statusEsperado = [[idVaga: 4, status: 'PENDENTE']]

        when:
        def resultado = servico.listarStatusCurtidasEmpresa('12.345.678/0001-90')

        then:
        1 * repositorio.listarStatusCurtidasEmpresa('12345678000190') >> statusEsperado
        resultado == statusEsperado
    }

    def "registrarMatch normaliza os dados antes de delegar ao repositorio"() {
        given:
        Map<String, Object> resposta = [idMatch: 7]

        when:
        def resultado = servico.registrarMatch([
                cpfCandidato: '123.456.789-01',
                cnpjEmpresa: '12.345.678/0001-90',
                idVaga: '12'
        ])

        then:
        1 * repositorio.registrarMatch({ Map<String, Object> payload ->
            payload == [
                    cpfCandidato: '12345678901',
                    cnpjEmpresa: '12345678000190',
                    idVaga: 12
            ]
        }) >> resposta
        resultado == resposta
    }

    @Unroll
    def "registrarMatch rejeita campo obrigatório inválido: #campo"() {
        given:
        Map<String, Object> dados = [
                cpfCandidato: '12345678901',
                cnpjEmpresa: '12345678000190',
                idVaga: 1
        ]
        dados[campo] = valor

        when:
        servico.registrarMatch(dados)

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        campo          | valor
        'cpfCandidato' | null
        'cpfCandidato' | ''
        'cpfCandidato' | '123456789012345'
        'cpfCandidato' | 12345678901
        'cnpjEmpresa'  | null
        'cnpjEmpresa'  | ''
        'cnpjEmpresa'  | '1234567800019012345'
        'cnpjEmpresa'  | 12345678000190
        'idVaga'       | null
        'idVaga'       | 0
        'idVaga'       | 'invalido'
    }

    def "listarMatchesCompletosCandidato retorna matches e curtidas pendentes"() {
        given:
        List<Map<String, Object>> matchesEsperados = [[idMatch: 1]]
        List<Map<String, Object>> pendentesEsperadas = [[idVaga: 2]]

        when:
        Map<String, Object> resultado = servico.listarMatchesCompletosCandidato('123.456.789-01')

        then:
        1 * repositorio.listarMatchesCompletosPorCandidato('12345678901') >> matchesEsperados
        1 * repositorio.listarCurtidasPendentesPorCandidato('12345678901') >> pendentesEsperadas
        resultado == [matches: matchesEsperados, curtidasPendentes: pendentesEsperadas]
    }

    @Unroll
    def "listarMatchesCompletosCandidato rejeita CPF inválido: #cpf"() {
        when:
        servico.listarMatchesCompletosCandidato(cpf)

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        cpf << [null, '', '1234567890', '123456789012']
    }

    def "listarMatchesCompletosEmpresa retorna matches e curtidas pendentes"() {
        given:
        List<Map<String, Object>> matchesEsperados = [[idMatch: 2]]
        List<Map<String, Object>> pendentesEsperadas = [[idVaga: 3]]

        when:
        Map<String, Object> resultado = servico.listarMatchesCompletosEmpresa('12.345.678/0001-90')

        then:
        1 * repositorio.listarMatchesCompletosPorEmpresa('12345678000190') >> matchesEsperados
        1 * repositorio.listarCurtidasPendentesPorEmpresa('12345678000190') >> pendentesEsperadas
        resultado == [matches: matchesEsperados, curtidasPendentes: pendentesEsperadas]
    }

    @Unroll
    def "listarMatchesCompletosEmpresa rejeita CNPJ inválido: #cnpj"() {
        when:
        servico.listarMatchesCompletosEmpresa(cnpj)

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        cnpj << [null, '', '1234567890123', '123456789012345']
    }

    @Unroll
    def "listarMatches rejeita CPF acima do limite: #cpf"() {
        when:
        servico.listarMatches(cpf, null)

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        cpf << ['123456789012345', '1234567890123456']
    }

    @Unroll
    def "listarMatches rejeita CNPJ acima do limite: #cnpj"() {
        when:
        servico.listarMatches(null, cnpj)

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        cnpj << ['1234567890123456789', '12345678901234567890']
    }

    @Unroll
    def "listarStatusCurtidasCandidato rejeita CPF inválido: #cpf"() {
        when:
        servico.listarStatusCurtidasCandidato(cpf)

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        cpf << [null, '', '   ', '123456789012345']
    }

    @Unroll
    def "listarStatusCurtidasEmpresa rejeita CNPJ inválido: #cnpj"() {
        when:
        servico.listarStatusCurtidasEmpresa(cnpj)

        then:
        thrown(CurtidaMatchInvalidoException)
        0 * repositorio._

        where:
        cnpj << [null, '', '   ', '1234567890123456789']
    }

}
