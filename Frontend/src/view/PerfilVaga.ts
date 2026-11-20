import type { Vaga } from "../model/Vaga.ts"

type VagaDaApi = Omit<Vaga, 'competencias'>

interface CompetenciaResposta {
    nome?: string
}

export async function renderPerfilVaga(vagaId: number, onVoltar?: () => void, temMatch: boolean = false): Promise<void> {
    const app = document.querySelector<HTMLDivElement>('#app')
    if (!app) return

    app.innerHTML = '<section class="perfil-vaga"><p>Carregando vaga...</p></section>'

    try {
        const resposta = await fetch(`http://localhost:8080/api/vagas?id=${encodeURIComponent(vagaId)}`)
        if (resposta.status === 404) {
            renderizarMensagem(app, 'Vaga não encontrada!', onVoltar)
            return
        }
        if (!resposta.ok) {
            throw new Error(`Falha ao buscar vaga (${resposta.status}).`)
        }

        const resultado: unknown = await resposta.json()
        const vagaDaApi = extrairVagaDaApi(resultado)
        if (!vagaDaApi) {
            renderizarMensagem(app, 'Vaga não encontrada!', onVoltar)
            return
        }

        const competencias = await buscarCompetenciasDaVaga(vagaId)
        const vaga: Vaga = {
            ...vagaDaApi,
            competencias: competencias ?? []
        }

        renderizarPerfilVagaHTML(app, vaga, temMatch, competencias !== null)
        configurarEventoVoltar(onVoltar)
    } catch (erro) {
        console.error('Erro ao buscar detalhes da vaga:', erro)
        renderizarMensagem(app, 'Não foi possível carregar os detalhes da vaga.', onVoltar)
    }
}

async function buscarCompetenciasDaVaga(idVaga: number): Promise<string[] | null> {
    try {
        const resposta = await fetch(
            `http://localhost:8086/api/competencias/vagas/${encodeURIComponent(idVaga)}`
        )
        if (!resposta.ok) {
            throw new Error(`Falha ao buscar competências da vaga (HTTP ${resposta.status}).`)
        }

        const dados: unknown = await resposta.json()
        const lista = Array.isArray(dados)
            ? dados
            : typeof dados === 'object' && dados !== null
            && 'dados' in dados && Array.isArray(dados.dados)
                ? dados.dados
                : null

        if (!lista) {
            throw new Error('Resposta inválida do ms-competencias ao consultar a vaga.')
        }

        return lista
            .map((item: string | CompetenciaResposta) =>
                typeof item === 'string' ? item.trim() : (item?.nome ?? '').trim()
            )
            .filter((nome: string) => nome.length > 0)
    } catch (erro) {
        console.error('Erro ao buscar competências da vaga no ms-competencias:', erro)
        return null
    }
}

function extrairVagaDaApi(resultado: unknown): VagaDaApi | undefined {
    if (typeof resultado !== 'object' || resultado === null) return undefined

    const dados = 'dados' in resultado ? resultado.dados : resultado
    return validarVagaDaApi(dados) ? dados : undefined
}

function validarVagaDaApi(valor: unknown): valor is VagaDaApi {
    return typeof valor === 'object'
        && valor !== null
        && 'id' in valor
        && typeof valor.id === 'number'
        && 'titulo' in valor
        && typeof valor.titulo === 'string'
        && 'pais' in valor
        && typeof valor.pais === 'string'
        && 'estado' in valor
        && typeof valor.estado === 'string'
        && 'descricao' in valor
        && typeof valor.descricao === 'string'
        && 'empresa' in valor
        && typeof valor.empresa === 'object'
        && valor.empresa !== null
}

function renderizarMensagem(app: HTMLDivElement, mensagem: string, onVoltar?: () => void): void {
    app.innerHTML = `
        <section class="perfil-vaga tela-com-transicao">
            <h2>${mensagem}</h2>
            <button id="btn-voltar-lista">Voltar para vagas</button>
        </section>
    `
    configurarEventoVoltar(onVoltar)
}

function renderizarPerfilVagaHTML(
    app: HTMLDivElement,
    vaga: Vaga,
    temMatch: boolean,
    competenciasCarregadas: boolean
): void {
    const competenciasTexto = !competenciasCarregadas
        ? 'Não foi possível carregar as competências.'
        : vaga.competencias?.length
            ? vaga.competencias.map(escaparHtml).join(', ')
            : 'Nenhuma competência especificada'
    const nomeEmpresa = temMatch && vaga.empresa?.nome
        ? escaparHtml(vaga.empresa.nome)
        : 'Match necessário para visualização'

    app.innerHTML = `
        <section class="perfil-vaga tela-com-transicao">
            <h1>Detalhes da Vaga</h1>
            
            <div class="card-vaga">
                <h2>${vaga.titulo}</h2>
                <hr>
                
                <h3>Informações da Vaga</h3>
                <p>
                    <strong>Descrição:</strong>
                    ${vaga.descricao}
                </p>
                
                <p>
                    <strong>Local:</strong>
                    ${vaga.pais} - ${vaga.estado}
                </p>
                
                <p>
                    <strong>Competências:</strong>
                    ${competenciasTexto}
                </p>
                
                <hr>
                
                <h3>Informações da Empresa</h3>
                <p>
                    <strong>Empresa:</strong>
                    ${nomeEmpresa}
                </p>
            </div>
            
            <button id="btn-voltar-lista">Voltar para Vagas</button>
        </section>
    `
}

function escaparHtml(valor: string): string {
    return valor.replace(/[&<>"']/g, caractere => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[caractere]!)
}

function configurarEventoVoltar(onVoltar?: () => void): void {
    document.querySelector('#btn-voltar-lista')?.addEventListener('click', () => {
        onVoltar?.()
    })
}