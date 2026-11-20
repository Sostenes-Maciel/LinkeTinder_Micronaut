import type { Candidato } from '../model/Candidato'
import { consultarEnderecoPorCep, formatarEndereco } from '../service/ConsultaCep.ts'

interface CompetenciaResposta {
    idCompetencia?: number
    nome?: string
}

export function renderPerfilCandidato(candidato: Candidato): void {
    const app = document.querySelector<HTMLDivElement>('#app')
    if (!app) return

    renderizarTemplatePerfil(app, candidato)
    void preencherEnderecoPerfil(candidato.cep)
    void carregarCompetenciasCandidato(candidato)
}

function renderizarTemplatePerfil(app: HTMLDivElement, candidato: Candidato, competenciasOverride?: string[]): void {
    const competencias = competenciasOverride ?? (Array.isArray(candidato.competencias) ? candidato.competencias : [])
    app.innerHTML = `
        <section class="perfil-candidato tela-com-transicao">
            <h1>Perfil do Candidato</h1>

            <div class="card-perfil">
                <header class="perfil-cabecalho">
                    <h2>${escaparHtml(candidato.nome)}</h2>
                    <p class="perfil-subtitulo">Candidato</p>
                </header>

                <div class="perfil-grade">
                    <section class="perfil-bloco">
                        <h3>Sobre</h3>
                        <p>${escaparHtml(candidato.descricao || 'Adicione uma descrição para apresentar sua trajetória.')}</p>
                    </section>
                    <section class="perfil-bloco">
                        <h3>Competências</h3>
                        <div class="perfil-tags" id="tags-competencias-candidato">
                            ${renderizarTagsCompetencias(competencias)}
                        </div>
                    </section>
                </div>

                <div class="perfil-detalhes">
                    <p><strong>E-mail:</strong> ${escaparHtml(candidato.email)}</p>
                    <p><strong>Idade:</strong> ${escaparHtml(String(candidato.idade))}</p>
                    <p><strong>Localização:</strong> ${escaparHtml([candidato.estado, candidato.pais].filter(Boolean).join(', '))}</p>
                    <p><strong>Endereço:</strong> <span id="endereco-perfil-candidato">Consultando CEP...</span></p>
                </div>
            </div>

        </section>
    `
}

function renderizarTagsCompetencias(competencias: string[]): string {
    if (!competencias.length) {
        return '<span>Sem competências informadas</span>'
    }
    return competencias
        .map(competencia => `<span class="perfil-tag">${escaparHtml(competencia)}</span>`)
        .join('')
}

async function carregarCompetenciasCandidato(candidato: Candidato): Promise<void> {
    const containerTags = document.querySelector<HTMLDivElement>('#tags-competencias-candidato')
    if (!containerTags) return

    const cpfLimpo = (candidato.cpf || '').replace(/\D/g, '')
    if (!cpfLimpo) return

    try {
        const resposta = await fetch(`http://localhost:8086/api/competencias/candidatos/${encodeURIComponent(cpfLimpo)}`)
        if (!resposta.ok) {
            return
        }

        const dados = await resposta.json() as Array<CompetenciaResposta | string>
        if (!Array.isArray(dados)) {
            return
        }

        const nomes = dados
            .map(item => typeof item === 'string' ? item.trim() : (item?.nome ?? '').trim())
            .filter(nome => nome.length > 0)

        if (nomes.length > 0) {
            containerTags.innerHTML = renderizarTagsCompetencias(nomes)
        }
    } catch (erro) {
        console.error('Não foi possível carregar as competências no ms-competencias:', erro)
    }
}

function escaparHtml(valor: string): string {
    return valor.replace(/[&<>"']/g, caractere => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    })[caractere]!)
}

async function preencherEnderecoPerfil(cep: string): Promise<void> {
    const enderecoPerfil = document.querySelector<HTMLSpanElement>('#endereco-perfil-candidato')
    if (!enderecoPerfil) return

    try {
        const endereco = await consultarEnderecoPorCep(cep)
        enderecoPerfil.textContent = formatarEndereco(endereco)
    } catch (erro) {
        console.error('Não foi possível carregar o endereço do candidato:', erro)
        enderecoPerfil.textContent = 'Endereço indisponível para este CEP.'
    }
}