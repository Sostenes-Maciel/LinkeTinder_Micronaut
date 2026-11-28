// Pipeline automatizado de testes unitarios dos microsservicos LinkeTinder
pipeline {
    agent any

    parameters {
        choice(
            name: 'MICROSSERVICO',
            choices: [
                'TODOS',
                'ms-cadastro',
                'ms-vagas',
                'ms-competencias',
                'ms-emails',
                'ms-curtidas-matches'
            ],
            description: 'Selecione o microsservico alvo ou TODOS para executar toda a suite'
        )
    }

    environment {
        SLACK_CHANNEL = '#testes_unitarios'
    }

    stages {
        stage('Preparar Ambiente') {
            steps {
                script {
                    def allServices = [
                        'ms-cadastro',
                        'ms-vagas',
                        'ms-competencias',
                        'ms-emails',
                        'ms-curtidas-matches'
                    ]

                    if (params.MICROSSERVICO == 'TODOS') {
                        env.TARGET_SERVICES = allServices.findAll { fileExists(it) }.join(',')
                    } else {
                        env.TARGET_SERVICES = params.MICROSSERVICO
                    }

                    echo "Microsservicos selecionados para teste: ${env.TARGET_SERVICES}"
                }
            }
        }

        stage('Executar Testes Unitarios nos Containers') {
            steps {
                script {
                    def services = env.TARGET_SERVICES.split(',')

                    for (String ms : services) {
                        ms = ms.trim()
                        if (!fileExists(ms)) {
                            echo "Diretorio ${ms} nao encontrado, pulando..."
                            continue
                        }

                        stage("Testes - ${ms}") {
                            def imageName = "linketinder-${ms}-test"
                            def containerName = "jenkins-test-${ms}-${env.BUILD_NUMBER}"
                            def dataHora = new Date().format('dd/MM/yyyy HH:mm:ss', TimeZone.getTimeZone('America/Sao_Paulo'))
                            def statusTexto = 'SUCESSO'

                            catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                                // 1. Construir imagem Docker referente ao microsservico
                                sh """
                                    mkdir -p ${ms}/build
                                    docker build -f Dockerfile.test --build-arg MS_DIR=${ms} -t ${imageName} .
                                """

                                // 2. Executar o container com o comando de testes unitarios
                                sh """
                                    docker run --rm --name ${containerName} \
                                      -v "\${WORKSPACE}/${ms}/build:/app/build" \
                                      ${imageName} ./gradlew test --rerun-tasks --no-daemon
                                """
                            }

                            if (currentBuild.currentResult == 'FAILURE') {
                                statusTexto = 'FALHA'
                            }

                            // 3. Coletar metricas dos relatorios JUnit XML gerados
                            def rawMetrics = sh(
                                script: """
                                    python3 -c '
import glob, xml.etree.ElementTree as ET
total = failures = errors = 0
duration = 0.0
for path in glob.glob("${ms}/build/test-results/test/TEST-*.xml"):
    root = ET.parse(path).getroot()
    total += int(root.attrib.get("tests", 0))
    failures += int(root.attrib.get("failures", 0))
    errors += int(root.attrib.get("errors", 0))
    duration += float(root.attrib.get("time", 0.0))
print(f"{total}|{failures + errors}|{duration:.2f}")
' 2>/dev/null || echo "0|0|0.00"
                                """,
                                returnStdout: true
                            ).trim()

                            def parts = rawMetrics.split('\\|')
                            def totalTestes = parts[0]
                            def totalFalhas = parts[1]
                            def tempoExecucao = parts[2]

                            if (totalFalhas.toInteger() > 0) {
                                statusTexto = 'FALHA'
                            }

                            // Publicar relatorio grafico no painel do Jenkins
                            junit allowEmptyResults: true, testResults: "${ms}/build/test-results/test/*.xml"

                            def relatorio = """
Relatorio de Testes Unitarios - LinkeTinder
-------------------------------------------
Status: ${statusTexto}
Microsservico: ${ms}
Data e Hora: ${dataHora}
Tempo de Execucao: ${tempoExecucao}s
Numero de Testes: ${totalTestes}
Numero de Falhas: ${totalFalhas}
Build Jenkins: #${env.BUILD_NUMBER}
                            """.stripIndent().trim()

                            echo relatorio

                            // 4. Enviar relatorio ao canal #testes_unitarios via plugin do Slack
                            def slackColor = (statusTexto == 'SUCESSO') ? 'good' : 'danger'
                            try {
                                slackSend(
                                    channel: env.SLACK_CHANNEL,
                                    color: slackColor,
                                    message: relatorio
                                )
                            } catch (Exception e) {
                                echo "Aviso: Slack nao configurado ou indisponivel (${e.message}). Relatorio registrado no log."
                            }
                        }
                    }
                }
            }
        }
    }
}