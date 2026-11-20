const URL_BASE = 'http://localhost:8086/api/competencias'

type CampoIdentificador = 'cpfCandidato' | 'cnpjEmpresa' | 'idVaga'

async function vincularCompetencias(
    endpoint: string,
    campoIdentificador: CampoIdentificador,
    identificador: string | number,
    competencias: string[],
    entidade: string
): Promise<void> {

    // Remove espaços, valores vazios e duplicidades.
    const nomesUnicos = new Map<string, string>()

    competencias.forEach(competencia => {
        const nome = competencia.trim()

        if (nome) {
            nomesUnicos.set(nome.toLowerCase(), nome)
        }
    })

    const nomes = Array.from(nomesUnicos.values())

    // Não realiza chamadas desnecessárias.
    if (nomes.length === 0) return

    const resultados = await Promise.all(
        nomes.map(async nome => {
            try {
                const payload: Record<string, string | number> = {
                    [campoIdentificador]: identificador,
                    nome
                }

                const resposta = await fetch(`${URL_BASE}/${endpoint}`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(payload)
                })

                if (!resposta.ok) {
                    const texto = await resposta.text()
                    let detalhe = texto

                    try {
                        const corpo = JSON.parse(texto)
                        detalhe = corpo.mensagem || corpo.message || texto
                    } catch {
                        // Mantém o texto original da resposta.
                    }

                    return `${nome}: ${detalhe || `HTTP ${resposta.status}`}`
                }

                return null

            } catch (erro) {
                const detalhe = erro instanceof Error
                    ? erro.message
                    : 'Falha de comunicação'

                return `${nome}: ${detalhe}`
            }
        })
    )

    const falhas = resultados.filter(
        (resultado): resultado is string => resultado !== null
    )

    if (falhas.length > 0) {
        throw new Error(
            `${entidade} foi cadastrado(a), mas algumas competências não foram vinculadas ao ms-competencias: ${falhas.join('; ')}. O cadastro principal já foi salvo. Não envie o formulário novamente.`
        )
    }
}

export async function vincularCompetenciasCandidato(
    cpf: string,
    competencias: string[]
): Promise<void> {
    return vincularCompetencias(
        'candidatos',
        'cpfCandidato',
        cpf.replace(/\D/g, ''),
        competencias,
        'O candidato'
    )
}

export async function vincularCompetenciasEmpresa(
    cnpj: string,
    competencias: string[]
): Promise<void> {
    return vincularCompetencias(
        'empresas',
        'cnpjEmpresa',
        cnpj.replace(/\D/g, ''),
        competencias,
        'A empresa'
    )
}

export async function vincularCompetenciasVaga(
    idVaga: number,
    competencias: string[]
): Promise<void> {
    return vincularCompetencias(
        'vagas',
        'idVaga',
        idVaga,
        competencias,
        'A vaga'
    )
}