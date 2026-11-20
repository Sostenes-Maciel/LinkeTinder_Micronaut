package linketinder.vagas.model

interface RepositorioVaga {
    List<Map<String, Object>> listarVagas()

    Map<String, Object> buscarPorId(int idVaga)

    List<Map<String, Object>> listarPorEmpresa(String cnpjEmpresa)

    Map<String, Object> inserirVaga(Map<String, Object> vaga)

    Map<String, Object> atualizarVaga(int idVaga, Map<String, Object> vaga)

    boolean excluirVaga(int idVaga)
}