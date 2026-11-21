package br.com.zg.acelera.cadastro.model

import spock.lang.Specification
import spock.lang.Unroll

class ServicoCadastroSpec extends Specification {

    RepositorioCadastro repositorio = Mock()
    ServicoCadastro servico = new ServicoCadastro(repositorio)


    def "deve cadastrar candidato com dados validos e competencias normalizadas"() {

        given:
        Map dados = dadosCandidato([
                cpf        : ' 12345678900 ',
                nome       : ' Ana Silva ',
                email      : ' ana@email.com ',
                pais       : ' Brasil ',
                estado     : ' Pernambuco ',
                cep        : ' 50000-000 ',
                descricao  : ' Desenvolvedora Java ',
                idade      : 25,
                competencias: [
                        ' Java ',
                        'JAVA',
                        'Groovy',
                        ' groovy ',
                        'SQL'
                ]
        ])

        when:
        Map resultado = servico.cadastrarCandidato(dados)

        then:
        resultado == [
                tipo        : 'candidato',
                identificador: '12345678900'
        ]

        1 * repositorio.inserirCandidato({
            it == [
                    cpf        : '12345678900',
                    nome       : 'Ana Silva',
                    email      : 'ana@email.com',
                    pais       : 'Brasil',
                    estado     : 'Pernambuco',
                    cep        : '50000-000',
                    descricao  : 'Desenvolvedora Java',
                    idade      : 25,
                    competencias: ['Java', 'Groovy', 'SQL']
            ]
        })
    }


    def "deve cadastrar empresa com dados validos e competencias normalizadas"() {

        given:
        Map dados = dadosEmpresa([
                cnpj        : ' 12345678000199 ',
                nome        : ' Empresa de Tecnologia ',
                email       : ' contato@empresa.com ',
                pais        : ' Brasil ',
                estado      : ' Pernambuco ',
                cep         : ' 50000-000 ',
                descricao   : ' Empresa de desenvolvimento ',
                competencias: [
                        ' Java ',
                        'JAVA',
                        'PostgreSQL',
                        ' postgresql '
                ]
        ])

        when:
        Map resultado = servico.cadastrarEmpresa(dados)

        then:
        resultado == [
                tipo        : 'empresa',
                identificador: '12345678000199'
        ]

        1 * repositorio.inserirEmpresa({
            it == [
                    cnpj        : '12345678000199',
                    nome        : 'Empresa de Tecnologia',
                    email       : 'contato@empresa.com',
                    pais        : 'Brasil',
                    estado      : 'Pernambuco',
                    cep         : '50000-000',
                    descricao   : 'Empresa de desenvolvimento',
                    competencias: ['Java', 'PostgreSQL']
            ]
        })
    }


    def "deve cadastrar candidato sem competencias quando o campo for nulo"() {

        when:
        Map resultado = servico.cadastrarCandidato(
                dadosCandidato([competencias: null])
        )

        then:
        resultado.tipo == 'candidato'

        1 * repositorio.inserirCandidato({
            it.competencias == []
        })
    }


    def "deve cadastrar empresa sem competencias quando o campo for omitido"() {

        given:
        Map dados = dadosEmpresa()
        dados.remove('competencias')

        when:
        Map resultado = servico.cadastrarEmpresa(dados)

        then:
        resultado.tipo == 'empresa'

        1 * repositorio.inserirEmpresa({
            it.competencias == []
        })
    }


    def "deve aceitar idade minima de 18 anos"() {

        when:
        servico.cadastrarCandidato(dadosCandidato([idade: 18]))

        then:
        1 * repositorio.inserirCandidato({
            it.idade == 18
        })
    }


    def "deve aceitar idade maxima de 120 anos"() {

        when:
        servico.cadastrarCandidato(dadosCandidato([idade: 120]))

        then:
        1 * repositorio.inserirCandidato({
            it.idade == 120
        })
    }


    @Unroll
    def "deve rejeitar idade invalida: #idade"() {

        when:
        servico.cadastrarCandidato(
                dadosCandidato([idade: idade])
        )

        then:
        thrown(CadastroInvalidoException)

        0 * repositorio._

        where:
        idade << [
                null,
                '25',
                17,
                121,
                18.5G
        ]
    }


    @Unroll
    def "deve rejeitar campo obrigatorio invalido no cadastro de candidato: #campo"() {

        when:
        servico.cadastrarCandidato(
                dadosCandidato([(campo): valor])
        )

        then:
        thrown(CadastroInvalidoException)

        0 * repositorio._

        where:
        campo       | valor
        'cpf'       | null
        'nome'      | ' '
        'pais'      | 123
        'estado'    | null
        'cep'       | ''
        'descricao' | 100
    }


    @Unroll
    def "deve rejeitar campo obrigatorio invalido no cadastro de empresa: #campo"() {

        when:
        servico.cadastrarEmpresa(
                dadosEmpresa([(campo): valor])
        )

        then:
        thrown(CadastroInvalidoException)

        0 * repositorio._

        where:
        campo       | valor
        'cnpj'      | null
        'nome'      | ' '
        'pais'      | 123
        'estado'    | null
        'cep'       | ''
        'descricao' | 100
    }


    @Unroll
    def "deve rejeitar email invalido no cadastro de candidato: #email"() {

        when:
        servico.cadastrarCandidato(
                dadosCandidato([email: email])
        )

        then:
        thrown(CadastroInvalidoException)

        0 * repositorio._

        where:
        email << [
                'email',
                'ana@',
                '@email.com',
                'ana email@email.com',
                'ana@email',
                ''
        ]
    }


    def "deve rejeitar email invalido no cadastro de empresa"() {

        when:
        servico.cadastrarEmpresa(
                dadosEmpresa([email: 'email-invalido'])
        )

        then:
        CadastroInvalidoException excecao =
                thrown(CadastroInvalidoException)

        excecao.message == 'email invalido.'

        0 * repositorio._
    }


    @Unroll
    def "deve rejeitar campo acima do limite no cadastro de candidato: #campo"() {

        given:
        String valor = 'X' * (limite + 1)

        when:
        servico.cadastrarCandidato(
                dadosCandidato([(campo): valor])
        )

        then:
        CadastroInvalidoException excecao =
                thrown(CadastroInvalidoException)

        excecao.message == "${campo} excede o limite de ${limite} caracteres."

        0 * repositorio._

        where:
        campo   | limite
        'cpf'   | 14
        'nome'  | 255
        'email' | 255
        'pais'  | 100
        'estado'| 100
        'cep'   | 20
    }


    @Unroll
    def "deve rejeitar campo acima do limite no cadastro de empresa: #campo"() {

        given:
        String valor = 'X' * (limite + 1)

        when:
        servico.cadastrarEmpresa(
                dadosEmpresa([(campo): valor])
        )

        then:
        CadastroInvalidoException excecao =
                thrown(CadastroInvalidoException)

        excecao.message == "${campo} excede o limite de ${limite} caracteres."

        0 * repositorio._

        where:
        campo   | limite
        'cnpj'  | 18
        'nome'  | 255
        'email' | 255
        'pais'  | 100
        'estado'| 100
        'cep'   | 20
    }


    def "deve rejeitar competencias quando o campo nao for uma colecao"() {

        when:
        servico.cadastrarCandidato(
                dadosCandidato([competencias: 'Java'])
        )

        then:
        CadastroInvalidoException excecao =
                thrown(CadastroInvalidoException)

        excecao.message ==
                'competencias deve ser uma lista de textos.'

        0 * repositorio._
    }


    def "deve rejeitar competencia vazia"() {

        when:
        servico.cadastrarCandidato(
                dadosCandidato([
                        competencias: ['Java', ' ']
                ])
        )

        then:
        CadastroInvalidoException excecao =
                thrown(CadastroInvalidoException)

        excecao.message ==
                'cada competencia deve ser um texto nao vazio.'

        0 * repositorio._
    }


    def "deve rejeitar competencia que nao seja texto"() {

        when:
        servico.cadastrarEmpresa(
                dadosEmpresa([
                        competencias: ['Java', 123]
                ])
        )

        then:
        CadastroInvalidoException excecao =
                thrown(CadastroInvalidoException)

        excecao.message ==
                'cada competencia deve ser um texto nao vazio.'

        0 * repositorio._
    }


    private static Map<String, Object> dadosCandidato(
            Map<String, Object> alteracoes = [:]
    ) {

        Map<String, Object> dados = [
                cpf        : '12345678900',
                nome       : 'Ana Silva',
                email      : 'ana@email.com',
                pais       : 'Brasil',
                estado     : 'Pernambuco',
                cep        : '50000-000',
                descricao  : 'Desenvolvedora Java',
                idade      : 25,
                competencias: ['Java', 'Groovy']
        ]

        dados.putAll(alteracoes)

        return dados
    }


    private static Map<String, Object> dadosEmpresa(
            Map<String, Object> alteracoes = [:]
    ) {

        Map<String, Object> dados = [
                cnpj        : '12345678000199',
                nome        : 'Empresa de Tecnologia',
                email       : 'contato@empresa.com',
                pais        : 'Brasil',
                estado      : 'Pernambuco',
                cep         : '50000-000',
                descricao   : 'Empresa de desenvolvimento',
                competencias: ['Java', 'PostgreSQL']
        ]

        dados.putAll(alteracoes)

        return dados
    }
}