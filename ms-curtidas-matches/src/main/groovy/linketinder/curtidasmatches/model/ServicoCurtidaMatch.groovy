package linketinder.curtidasmatches.model

import jakarta.inject.Singleton

@Singleton
class ServicoCurtidaMatch {

    private final RepositorioCurtidaMatch repositorio

    ServicoCurtidaMatch(RepositorioCurtidaMatch repositorio) {
        this.repositorio = repositorio
    }

    List<Map<String, Object>> listarCurtidas() {
        return repositorio.listarCurtidas()
    }


    Map<String, Object> registrarCurtida(Map<String, Object> dados) {
        Map<String, Object> payload = dados ?: [:]

        String cpfCandidato = validarTextoObrigatorio(
                payload.cpfCandidato,
                'cpfCandidato',
                14
        ).replaceAll(/\D/, '')

        Integer idVaga = validarInteiroObrigatorio(
                payload.idVaga,
                'idVaga'
        )

        String cnpjEmpresa = validarTextoOpcional(
                payload.cnpjEmpresa,
                'cnpjEmpresa',
                18
        )

        if (cnpjEmpresa != null) {
            cnpjEmpresa = cnpjEmpresa.replaceAll(/\D/, '')
        }

        return repositorio.registrarCurtida([
                cpfCandidato: cpfCandidato,
                idVaga      : idVaga,
                cnpjEmpresa : cnpjEmpresa
        ] as Map<String, Object>)
    }


    boolean removerCurtidaCandidato(
            String cpfCandidato,
            int idVaga
    ) {
        String cpf = validarTextoObrigatorio(
                cpfCandidato,
                'cpfCandidato',
                14
        ).replaceAll(/\D/, '')

        validarInteiroObrigatorio(idVaga, 'idVaga')

        return repositorio.removerCurtidaCandidato(cpf, idVaga)
    }

    boolean removerCurtidaEmpresa(
            String cnpjEmpresa,
            String cpfCandidato,
            int idVaga
    ) {
        String cnpj = validarTextoObrigatorio(
                cnpjEmpresa,
                'cnpjEmpresa',
                18
        ).replaceAll(/\D/, '')

        String cpf = validarTextoObrigatorio(
                cpfCandidato,
                'cpfCandidato',
                14
        ).replaceAll(/\D/, '')

        validarInteiroObrigatorio(idVaga, 'idVaga')

        return repositorio.removerCurtidaEmpresa(
                cnpj,
                cpf,
                idVaga
        )
    }


    List<Map<String, Object>> listarMatches(
            String cpfCandidato = null,
            String cnpjEmpresa = null
    ) {
        if (cpfCandidato != null && !cpfCandidato.trim().isEmpty()) {
            String cpf = validarTextoObrigatorio(
                    cpfCandidato,
                    'cpfCandidato',
                    14
            ).replaceAll(/\D/, '')

            return repositorio.listarMatchesPorCandidato(cpf)
        }

        if (cnpjEmpresa != null && !cnpjEmpresa.trim().isEmpty()) {
            String cnpj = validarTextoObrigatorio(
                    cnpjEmpresa,
                    'cnpjEmpresa',
                    18
            ).replaceAll(/\D/, '')

            return repositorio.listarMatchesPorEmpresa(cnpj)
        }

        return repositorio.listarMatches()
    }


    List<Map<String, Object>> listarStatusCurtidasCandidato(
            String cpfCandidato
    ) {
        String cpf = validarTextoObrigatorio(
                cpfCandidato,
                'cpfCandidato',
                14
        ).replaceAll(/\D/, '')

        return repositorio.listarStatusCurtidasCandidato(cpf)
    }

    List<Map<String, Object>> listarStatusCurtidasEmpresa(
            String cnpjEmpresa
    ) {
        String cnpj = validarTextoObrigatorio(
                cnpjEmpresa,
                'cnpjEmpresa',
                18
        ).replaceAll(/\D/, '')

        return repositorio.listarStatusCurtidasEmpresa(cnpj)
    }


    Map<String, Object> registrarMatch(Map<String, Object> dados) {
        Map<String, Object> payload = dados ?: [:]

        String cpfCandidato = validarTextoObrigatorio(
                payload.cpfCandidato,
                'cpfCandidato',
                14
        ).replaceAll(/\D/, '')

        String cnpjEmpresa = validarTextoObrigatorio(
                payload.cnpjEmpresa,
                'cnpjEmpresa',
                18
        ).replaceAll(/\D/, '')

        Integer idVaga = validarInteiroObrigatorio(
                payload.idVaga,
                'idVaga'
        )

        return repositorio.registrarMatch([
                cpfCandidato: cpfCandidato,
                cnpjEmpresa : cnpjEmpresa,
                idVaga      : idVaga
        ] as Map<String, Object>)
    }


    private static String validarTextoObrigatorio(
            Object valor,
            String campo,
            int limite
    ) {
        if (!(valor instanceof String) || !valor.trim()) {
            throw new CurtidaMatchInvalidoException(
                    "${campo} e obrigatorio e deve ser texto."
            )
        }

        String texto = valor.trim()

        if (texto.length() > limite) {
            throw new CurtidaMatchInvalidoException(
                    "${campo} excede o limite de ${limite} caracteres."
            )
        }

        return texto
    }

    private static String validarTextoOpcional(
            Object valor,
            String campo,
            int limite
    ) {
        if (valor == null) {
            return null
        }

        if (!(valor instanceof String)) {
            throw new CurtidaMatchInvalidoException(
                    "${campo} deve ser texto."
            )
        }

        String texto = valor.trim()

        if (texto.isEmpty()) {
            return null
        }

        if (texto.length() > limite) {
            throw new CurtidaMatchInvalidoException(
                    "${campo} excede o limite de ${limite} caracteres."
            )
        }

        return texto
    }

    private static Integer validarInteiroObrigatorio(
            Object valor,
            String campo
    ) {
        Integer numero = validarInteiroOpcional(valor, campo)

        if (numero == null) {
            throw new CurtidaMatchInvalidoException(
                    "${campo} e obrigatorio."
            )
        }

        return numero
    }

    private static Integer validarInteiroOpcional(
            Object valor,
            String campo
    ) {
        if (valor == null) {
            return null
        }

        if (valor instanceof Number) {
            int inteiro = (valor as Number).intValue()

            if (inteiro <= 0) {
                throw new CurtidaMatchInvalidoException(
                        "${campo} deve ser maior que zero."
                )
            }

            return inteiro
        }

        if (valor instanceof String && valor.trim()) {
            try {
                int inteiro = Integer.parseInt(valor.trim())

                if (inteiro <= 0) {
                    throw new CurtidaMatchInvalidoException(
                            "${campo} deve ser maior que zero."
                    )
                }

                return inteiro

            } catch (NumberFormatException ignored) {
                throw new CurtidaMatchInvalidoException(
                        "${campo} deve ser um numero inteiro valido."
                )
            }
        }

        throw new CurtidaMatchInvalidoException(
                "${campo} deve ser um numero inteiro valido."
        )
    }

    Map<String, Object> listarMatchesCompletosCandidato(String cpfCandidato) {
        String cpf = cpfCandidato?.replaceAll(/\D/, '')

        if (!cpf || cpf.length() != 11) {
            throw new CurtidaMatchInvalidoException(
                    'CPF inválido para consultar os matches.'
            )
        }

        List<Map<String, Object>> matches =
                repositorio.listarMatchesCompletosPorCandidato(cpf)

        List<Map<String, Object>> curtidasPendentes =
                repositorio.listarCurtidasPendentesPorCandidato(cpf)

        return [
                matches: matches,
                curtidasPendentes: curtidasPendentes
        ] as Map<String, Object>
    }

    Map<String, Object> listarMatchesCompletosEmpresa(String cnpjEmpresa) {
        String cnpj = cnpjEmpresa?.replaceAll(/\D/, '')

        if (!cnpj || cnpj.length() != 14) {
            throw new CurtidaMatchInvalidoException(
                    'CNPJ inválido para consultar os matches.'
            )
        }

        List<Map<String, Object>> matches =
                repositorio.listarMatchesCompletosPorEmpresa(cnpj)

        List<Map<String, Object>> curtidasPendentes =
                repositorio.listarCurtidasPendentesPorEmpresa(cnpj)

        return [
                matches: matches,
                curtidasPendentes: curtidasPendentes
        ] as Map<String, Object>
    }
}