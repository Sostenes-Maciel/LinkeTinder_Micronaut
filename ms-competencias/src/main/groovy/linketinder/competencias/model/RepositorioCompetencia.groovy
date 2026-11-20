package linketinder.competencias.model

interface RepositorioCompetencia {

    List<Map<String, Object>> listarCompetencias()

    Map<String, Object> inserirCompetencia(String nome)

    List<Map<String, Object>> listarPorCandidato(String cpfCandidato)

    Map<String, List<Map<String, Object>>> listarPorCandidatos(List<String> cpfs)

    void vincularCandidato(String cpfCandidato, String nomeCompetencia)

    List<Map<String, Object>> listarPorEmpresa(String cnpjEmpresa)

    void vincularEmpresa(String cnpjEmpresa, String nomeCompetencia)

    List<Map<String, Object>> listarPorVaga(int idVaga)

    void vincularVaga(int idVaga, String nomeCompetencia)
}