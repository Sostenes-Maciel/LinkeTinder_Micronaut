package linketinder.curtidasmatches.model

interface RepositorioCurtidaMatch {

    List<Map<String, Object>> listarCurtidas()

    Map<String, Object> registrarCurtida(Map<String, Object> dados)

    boolean removerCurtidaCandidato(String cpfCandidato, int idVaga)

    boolean removerCurtidaEmpresa(
            String cnpjEmpresa,
            String cpfCandidato,
            int idVaga
    )

    List<Map<String, Object>> listarMatches()

    List<Map<String, Object>> listarMatchesPorCandidato(String cpfCandidato)

    List<Map<String, Object>> listarMatchesPorEmpresa(String cnpjEmpresa)

    List<Map<String, Object>> listarStatusCurtidasCandidato(String cpfCandidato)

    List<Map<String, Object>> listarStatusCurtidasEmpresa(String cnpjEmpresa)

    Map<String, Object> registrarMatch(Map<String, Object> dados)
}