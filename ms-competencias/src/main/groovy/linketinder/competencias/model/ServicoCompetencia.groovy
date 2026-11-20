package linketinder.competencias.model

import jakarta.inject.Singleton

@Singleton
class ServicoCompetencia {

    private static final int LIMITE_NOME = 255
    private static final int LIMITE_CPFS_LOTE = 500

    private final RepositorioCompetencia repositorio

    ServicoCompetencia(RepositorioCompetencia repositorio) {
        this.repositorio = repositorio
    }

    List<Map<String, Object>> listarCompetencias() {
        repositorio.listarCompetencias()
    }

    Map<String, Object> cadastrarCompetencia(Map<String, Object> dados) {
        String nome = obterTextoObrigatorio(
                dados ?: [:],
                'nome',
                LIMITE_NOME
        )

        repositorio.inserirCompetencia(nome)
    }

    List<Map<String, Object>> listarPorCandidato(String cpfCandidato) {
        String cpf = validarTexto(cpfCandidato, 'cpfCandidato', 14)

        repositorio.listarPorCandidato(cpf)
    }

    /**
     * Consulta as competências de vários candidatos.
     *
     * Payload esperado:
     * [cpfs: ['12345678901', '98765432100']]
     */
    Map<String, List<Map<String, Object>>> listarPorCandidatos(
            Map<String, Object> dados
    ) {
        Map<String, Object> payload = dados ?: [:]
        Object valorCpfs = payload.cpfs

        if (!(valorCpfs instanceof List)) {
            throw new CompetenciaInvalidoException(
                    'cpfs e obrigatorio e deve ser uma lista.'
            )
        }

        List valoresRecebidos = (List) valorCpfs

        if (valoresRecebidos.size() > LIMITE_CPFS_LOTE) {
            throw new CompetenciaInvalidoException(
                    "A consulta em lote aceita no maximo ${LIMITE_CPFS_LOTE} CPFs."
            )
        }

        List<String> cpfs = []

        valoresRecebidos.eachWithIndex { Object valor, int indice ->
            String cpfInformado = validarTexto(
                    valor,
                    "cpfs[${indice}]",
                    14
            )

            String cpf = cpfInformado.replaceAll(/\D/, '')

            if (cpf.length() != 11) {
                throw new CompetenciaInvalidoException(
                        "cpfs[${indice}] deve conter exatamente 11 digitos."
                )
            }

            // Remove duplicados preservando a ordem.
            if (!cpfs.contains(cpf)) {
                cpfs << cpf
            }
        }

        if (cpfs.isEmpty()) {
            return [:] as Map<String, List<Map<String, Object>>>
        }

        repositorio.listarPorCandidatos(cpfs)
    }

    Map<String, Object> vincularCandidato(Map<String, Object> dados) {
        Map<String, Object> payload = dados ?: [:]

        String cpf = obterTextoObrigatorio(
                payload,
                'cpfCandidato',
                14
        )

        String nome = obterTextoObrigatorio(
                payload,
                'nome',
                LIMITE_NOME
        )

        repositorio.vincularCandidato(cpf, nome)

        return [
                cpfCandidato: cpf,
                competencia : nome
        ] as Map<String, Object>
    }

    List<Map<String, Object>> listarPorEmpresa(String cnpjEmpresa) {
        String cnpj = validarTexto(cnpjEmpresa, 'cnpjEmpresa', 18)

        repositorio.listarPorEmpresa(cnpj)
    }

    Map<String, Object> vincularEmpresa(Map<String, Object> dados) {
        Map<String, Object> payload = dados ?: [:]

        String cnpj = obterTextoObrigatorio(
                payload,
                'cnpjEmpresa',
                18
        )

        String nome = obterTextoObrigatorio(
                payload,
                'nome',
                LIMITE_NOME
        )

        repositorio.vincularEmpresa(cnpj, nome)

        return [
                cnpjEmpresa: cnpj,
                competencia: nome
        ] as Map<String, Object>
    }

    List<Map<String, Object>> listarPorVaga(int idVaga) {
        if (idVaga <= 0) {
            throw new CompetenciaInvalidoException(
                    'idVaga deve ser maior que zero.'
            )
        }

        repositorio.listarPorVaga(idVaga)
    }

    Map<String, Object> vincularVaga(Map<String, Object> dados) {
        Map<String, Object> payload = dados ?: [:]

        int idVaga = obterIdVagaObrigatorio(payload)

        String nome = obterTextoObrigatorio(
                payload,
                'nome',
                LIMITE_NOME
        )

        repositorio.vincularVaga(idVaga, nome)

        return [
                idVaga     : idVaga,
                competencia: nome
        ] as Map<String, Object>
    }

    private static int obterIdVagaObrigatorio(Map<String, Object> dados) {
        Object valor = dados.idVaga

        if (!(valor instanceof Number)) {
            throw new CompetenciaInvalidoException(
                    'idVaga e obrigatorio e deve ser um numero inteiro.'
            )
        }

        BigDecimal numero = new BigDecimal(valor.toString())

        if (numero.stripTrailingZeros().scale() > 0) {
            throw new CompetenciaInvalidoException(
                    'idVaga deve ser um numero inteiro.'
            )
        }

        if (numero <= BigDecimal.ZERO) {
            throw new CompetenciaInvalidoException(
                    'idVaga deve ser um numero inteiro positivo.'
            )
        }

        numero.intValue()
    }

    private static String obterTextoObrigatorio(
            Map<String, Object> dados,
            String campo,
            int limite
    ) {
        validarTexto(dados[campo], campo, limite)
    }

    private static String validarTexto(
            Object valor,
            String campo,
            int limite
    ) {
        if (!(valor instanceof String) || !valor.trim()) {
            throw new CompetenciaInvalidoException(
                    "${campo} e obrigatorio e deve ser texto."
            )
        }

        String texto = valor.trim()

        if (texto.length() > limite) {
            throw new CompetenciaInvalidoException(
                    "${campo} excede o limite de ${limite} caracteres."
            )
        }

        texto
    }
}