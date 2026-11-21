package linketinder.vagas.model

import jakarta.inject.Singleton

@Singleton
class ServicoVaga {

    private static final Map<String, Integer> LIMITES_COLUNAS = [
            titulo     : 255,
            pais       : 100,
            estado     : 100,
            cnpjEmpresa: 18,
            areaAtuacao: 150
    ]

    private final RepositorioVaga repositorio

    ServicoVaga(RepositorioVaga repositorio) {
        this.repositorio = repositorio
    }

    List<Map<String, Object>> listarVagas() {
        repositorio.listarVagas()
    }

    Map<String, Object> buscarPorId(int idVaga) {
        validarIdVaga(idVaga)
        Map<String, Object> vaga = repositorio.buscarPorId(idVaga)
        if (vaga == null) {
            throw new VagaInvalidoException("Vaga com id ${idVaga} nao encontrada.")
        }
        return vaga
    }


    List<Map<String, Object>> listarPorEmpresa(String cnpjEmpresa) {
        String cnpj = validarTexto(cnpjEmpresa, 'cnpjEmpresa', 18)
        repositorio.listarPorEmpresa(cnpj)
    }

    Map<String, Object> cadastrarVaga(Map<String, Object> dados) {
        Map<String, Object> payload = dados ?: [:]
        String cnpj = obterTextoObrigatorio(payload, 'cnpjEmpresa')
        String titulo = obterTextoObrigatorio(payload, 'titulo')
        String pais = obterTextoObrigatorio(payload, 'pais')
        String estado = obterTextoObrigatorio(payload, 'estado')
        String descricao = obterTextoObrigatorio(payload, 'descricao')
        String areaAtuacao = obterTextoObrigatorio(payload, 'areaAtuacao')
        List<String> competencias = obterCompetencias(payload)

        return repositorio.inserirVaga([
                cnpjEmpresa : cnpj,
                titulo      : titulo,
                pais        : pais,
                estado      : estado,
                descricao   : descricao,
                areaAtuacao : areaAtuacao,
                competencias: competencias
        ] as Map<String, Object>)
    }

    Map<String, Object> atualizarVaga(int idVaga, Map<String, Object> dados) {
        validarIdVaga(idVaga)
        Map<String, Object> payload = dados ?: [:]
        String titulo = obterTextoObrigatorio(payload, 'titulo')
        String pais = obterTextoObrigatorio(payload, 'pais')
        String estado = obterTextoObrigatorio(payload, 'estado')
        String descricao = obterTextoObrigatorio(payload, 'descricao')
        String areaAtuacao = obterTextoObrigatorio(payload, 'areaAtuacao')

        Map<String, Object> dadosAtualizacao = [
                titulo     : titulo,
                pais       : pais,
                estado     : estado,
                descricao  : descricao,
                areaAtuacao: areaAtuacao
        ] as Map<String, Object>

        if (payload.containsKey('competencias')) {
            dadosAtualizacao.competencias = obterCompetencias(payload)
        }

        Map<String, Object> atualizada = repositorio.atualizarVaga(idVaga, dadosAtualizacao)
        if (atualizada == null) {
            throw new VagaInvalidoException("Vaga com id ${idVaga} nao encontrada.")
        }
        return atualizada
    }

    void excluirVaga(int idVaga) {
        validarIdVaga(idVaga)
        boolean removida = repositorio.excluirVaga(idVaga)
        if (!removida) {
            throw new VagaInvalidoException("Vaga com id ${idVaga} nao encontrada.")
        }
    }

    private static void validarIdVaga(int idVaga) {
        if (idVaga <= 0) {
            throw new VagaInvalidoException('idVaga deve ser maior que zero.')
        }
    }

    private static List<String> obterCompetencias(Map<String, Object> dados) {
        Object valor = dados.competencias

        if (!(valor instanceof Collection)) {
            throw new VagaInvalidoException(
                    'competencias deve ser uma lista de textos.'
            )
        }

        Collection colecao = (Collection) valor

        if (colecao.isEmpty()) {
            throw new VagaInvalidoException(
                    'A vaga deve possuir pelo menos uma competencia.'
            )
        }

        List<String> competencias = []

        colecao.each { Object item ->

            if (!(item instanceof String)) {
                throw new VagaInvalidoException(
                        'cada competencia deve ser um texto nao vazio.'
                )
            }

            String texto = (String) item
            String nome = texto.trim()

            if (nome.isEmpty()) {
                throw new VagaInvalidoException(
                        'cada competencia deve ser um texto nao vazio.'
                )
            }

            String chave = nome.toLowerCase(Locale.ROOT)

            boolean duplicada = competencias.any { String existente ->
                existente.toLowerCase(Locale.ROOT) == chave
            }

            if (!duplicada) {
                competencias.add(nome)
            }
        }

        return competencias
    }

    private static String obterTextoObrigatorio(Map<String, Object> dados, String campo) {
        validarTexto(dados[campo], campo, LIMITES_COLUNAS[campo])
    }

    private static String validarTexto(Object valor, String campo, Integer limite) {
        if (!(valor instanceof String) || !valor.trim()) {
            throw new VagaInvalidoException("${campo} e obrigatorio e deve ser texto.")
        }
        String texto = valor.trim()
        if (limite != null && texto.length() > limite) {
            throw new VagaInvalidoException("${campo} excede o limite de ${limite} caracteres.")
        }
        return texto
    }
}