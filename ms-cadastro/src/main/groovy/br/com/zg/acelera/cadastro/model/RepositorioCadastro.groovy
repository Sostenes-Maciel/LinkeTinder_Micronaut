package br.com.zg.acelera.cadastro.model

interface RepositorioCadastro {
    void inserirCandidato(Map<String, Object> candidato)

    void inserirEmpresa(Map<String, Object> empresa)
}
