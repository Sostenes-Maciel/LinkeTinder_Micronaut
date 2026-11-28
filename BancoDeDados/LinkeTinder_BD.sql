-- 1 CRIAÇÃO DAS TABELAS

CREATE TABLE candidato (
                           cpf VARCHAR(14) PRIMARY KEY,
                           nome VARCHAR(255) NOT NULL,
                           email VARCHAR(255) NOT NULL UNIQUE,
                           pais VARCHAR(100),
                           estado VARCHAR(100),
                           cep VARCHAR(20),
                           descricao TEXT,
                           idade INT
);

CREATE TABLE empresa (
                         cnpj VARCHAR(18) PRIMARY KEY,
                         nome VARCHAR(255) NOT NULL,
                         email VARCHAR(255) NOT NULL UNIQUE,
                         pais VARCHAR(100),
                         estado VARCHAR(100),
                         cep VARCHAR(20),
                         descricao TEXT
);

CREATE TABLE competencia (
                             id_competencia SERIAL PRIMARY KEY,
                             nome VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE vaga (
                      id_vaga SERIAL PRIMARY KEY,
                      titulo VARCHAR(255) NOT NULL,
                      pais VARCHAR(100),
                      estado VARCHAR(100),
                      descricao TEXT,
                      cnpj_empresa VARCHAR(18) NOT NULL,
                      UNIQUE (id_vaga, cnpj_empresa),
                      FOREIGN KEY (cnpj_empresa) REFERENCES empresa(cnpj) ON DELETE CASCADE
);

CREATE TABLE candidato_competencia (
                                       cpf_candidato VARCHAR(14),
                                       id_competencia INT,
                                       PRIMARY KEY (cpf_candidato, id_competencia),
                                       FOREIGN KEY (cpf_candidato) REFERENCES candidato(cpf) ON DELETE CASCADE,
                                       FOREIGN KEY (id_competencia) REFERENCES competencia(id_competencia) ON DELETE CASCADE
);

CREATE TABLE empresa_competencia (
                                     cnpj_empresa VARCHAR(18),
                                     id_competencia INT,
                                     PRIMARY KEY (cnpj_empresa, id_competencia),
                                     FOREIGN KEY (cnpj_empresa) REFERENCES empresa(cnpj) ON DELETE CASCADE,
                                     FOREIGN KEY (id_competencia) REFERENCES competencia(id_competencia) ON DELETE CASCADE
);

CREATE TABLE vaga_competencia (
                                  id_vaga INT,
                                  id_competencia INT,
                                  PRIMARY KEY (id_vaga, id_competencia),
                                  FOREIGN KEY (id_vaga) REFERENCES vaga(id_vaga) ON DELETE CASCADE,
                                  FOREIGN KEY (id_competencia) REFERENCES competencia(id_competencia) ON DELETE CASCADE
);

CREATE TABLE curtida (
                         id_curtida SERIAL PRIMARY KEY,
                         cpf_candidato VARCHAR(14) NOT NULL,
                         id_vaga INT,
                         cnpj_empresa VARCHAR(18),
                         CONSTRAINT curtida_valida CHECK (
                             (id_vaga IS NOT NULL AND cnpj_empresa IS NULL) OR
                             (id_vaga IS NULL AND cnpj_empresa IS NOT NULL) OR
                             (id_vaga IS NOT NULL AND cnpj_empresa IS NOT NULL)
                             ),
                         FOREIGN KEY (cpf_candidato) REFERENCES candidato(cpf) ON DELETE CASCADE,
                         FOREIGN KEY (id_vaga) REFERENCES vaga(id_vaga) ON DELETE CASCADE,
                         FOREIGN KEY (cnpj_empresa) REFERENCES empresa(cnpj) ON DELETE CASCADE,
                         CONSTRAINT curtida_vaga_empresa_fk
                             FOREIGN KEY (id_vaga, cnpj_empresa)
                                 REFERENCES vaga(id_vaga, cnpj_empresa) ON DELETE CASCADE
);

CREATE UNIQUE INDEX curtida_empresa_candidato_vaga_unica
    ON curtida (cpf_candidato, cnpj_empresa, id_vaga)
    WHERE cnpj_empresa IS NOT NULL AND id_vaga IS NOT NULL;

CREATE TABLE match_vaga (
                            id_match SERIAL PRIMARY KEY,
                            cpf_candidato VARCHAR(14) NOT NULL,
                            cnpj_empresa VARCHAR(18) NOT NULL,
                            id_vaga INT NOT NULL,
                            UNIQUE (cpf_candidato, cnpj_empresa, id_vaga),
                            FOREIGN KEY (cpf_candidato) REFERENCES candidato(cpf) ON DELETE CASCADE,
                            FOREIGN KEY (cnpj_empresa) REFERENCES empresa(cnpj) ON DELETE CASCADE,
                            FOREIGN KEY (id_vaga) REFERENCES vaga(id_vaga) ON DELETE CASCADE,
                            FOREIGN KEY (id_vaga, cnpj_empresa) REFERENCES vaga(id_vaga, cnpj_empresa) ON DELETE CASCADE
);


--- 2 INSERT

-- Inserindo os 5 Candidatos
INSERT INTO candidato (cpf, nome, email, pais, estado, cep, descricao, idade) VALUES
                                                                                  ('12345678900', 'Sóstenes Maciel', 'sosmarques@hotmail.com', 'Brasil', 'PE', '55299-300', 'Estagiário ZG.', 29),
                                                                                  ('11122233349', 'José da Silva', 'jose@outlook.com', 'Brasil', 'SP', '11222-333', 'Estudante de ciências da computação', 21),
                                                                                  ('12322243300', 'Paulo André', 'paulo@hotmail.com', 'Brasil', 'RJ', '55210-300', 'Estagiário de TI.', 33),
                                                                                  ('33344478900', 'Ana Julia', 'anajj@hotmail.com', 'Brasil', 'BA', '88855-000', 'Recém formada em redes.', 20),
                                                                                  ('33366676800', 'Vitor Pereira', 'vitor@hotmail.com', 'Brasil', 'GO', '65432-300', 'Tutor acelera.', 40);

-- Inserindo as 5 Empresas
INSERT INTO empresa (cnpj, nome, email, pais, estado, cep, descricao) VALUES
                                                                          ('98765432000111', 'Tech Global', 'vagastech@gmail.com', 'Brasil', 'RJ', '32133-77', 'Consultoria para TI internacional'),
                                                                          ('22344543000111', 'S.O.S enterprises', 'sosss@gmail.com', 'Estados Unidos', 'Los Angeles', '89076', 'Consultoria para TI internacional'),
                                                                          ('45678901000134', 'BlueSky Technologies', 'contato@blueskytech.com', 'Estados Unidos', 'California', '90210', 'Desenvolvimento de plataformas digitais e sistemas web'),
                                                                          ('56789012000145', 'GreenCode Solutions', 'vagas@greencode.co.uk', 'Reino Unido', 'England', 'SW1A 1AA', 'Consultoria em tecnologia e desenvolvimento de software'),
                                                                          ('67890123000156', 'Tokyo Digital Labs', 'jobs@tokyodigital.jp', 'Japão', 'Tokyo', '100-0001', 'Soluções de inteligência artificial e aplicações corporativas');

-- Inserindo as Competências (Unificadas)
INSERT INTO competencia (nome) VALUES
                                   ('Java'), ( 'Groovy'), ('Python'), ('Web design'), ('Banco de dados'),
                                   ('JavaScript'), ('Gradle'), ('Metodologias Ágeis'), ('Web'), ('Angular'),
                                   ('TypeScript'), ('Node.js'), ('Linux'), ('Spring Boot'), ('MySQL'),
                                   ('Django'), ('React'), ('AWS'), ('Git'), ('SQL'), ('English');

-- Inserindo as 6 Vagas
INSERT INTO vaga (titulo, pais, estado, descricao, cnpj_empresa) VALUES
                                                                     ('Desenvolvedor Full Stack', 'Brasil', 'PE', 'Atuação no desenvolvimento de aplicações web, integração com APIs e bancos de dados.', '98765432000111'),
                                                                     ('Desenvolvedor Java Júnior', 'Brasil', 'SP', 'Profissional em início de carreira focado em criar, corrigir e manter sistemas back-end utilizando a linguagem Java.', '22344543000111'),
                                                                     ('Desenvolvedor Backend Node.js', 'Brasil', 'PE', 'Atuação no desenvolvimento e manutenção de aplicações backend utilizando Node.js.', '45678901000134'),
                                                                     ('Desenvolvedor Java Spring Boot', 'Brasil', 'PE', 'Desenvolvimento e manutenção de aplicações backend utilizando Java e Spring Boot.', '56789012000145'),
                                                                     ('Desenvolvedor Python', 'Brasil', 'PE', 'Desenvolvimento de aplicações e soluções utilizando Python, trabalhando na criação de funcionalidades.', '67890123000156'),
                                                                     ('Desenvolvedor TypeScript', 'Brasil', 'PE', 'Desenvolvimento de aplicações utilizando TypeScript e JavaScript.', '67890123000156');

-- Vinculando Candidatos e Competências
INSERT INTO candidato_competencia (cpf_candidato, id_competencia) VALUES
                                                                      ('12345678900', 1), ('12345678900', 2), ('12345678900', 3),
                                                                      ('11122233349', 4), ('11122233349', 5), ('11122233349', 6),
                                                                      ('12322243300', 1), ('12322243300', 7), ('12322243300', 8),
                                                                      ('33344478900', 3), ('33344478900', 2), ('33344478900', 9),
                                                                      ('33366676800', 1), ('33366676800', 6), ('33366676800', 10);

-- Vinculando Empresas e Competências
INSERT INTO empresa_competencia (cnpj_empresa, id_competencia) VALUES
                                                                   ('98765432000111', 10), ('98765432000111', 6), ('98765432000111', 11),
                                                                   ('22344543000111', 12), ('22344543000111', 1), ('22344543000111', 13),
                                                                   ('45678901000134', 1), ('45678901000134', 14), ('45678901000134', 15),
                                                                   ('56789012000145', 3), ('56789012000145', 16), ('56789012000145', 17),
                                                                   ('67890123000156', 6), ('67890123000156', 11), ('67890123000156', 18);

-- Vinculando Vagas e Competências Exigidas
INSERT INTO vaga_competencia (id_vaga, id_competencia) VALUES
                                                           (1, 1), (1, 11), (1, 19), (1, 20),
                                                           (2, 1), (2, 10), (2, 17), (2, 21),
                                                           (3, 12), (3, 6), (3, 19),
                                                           (4, 1), (4, 14), (4, 19),
                                                           (5, 3), (5, 19), (5, 20),
                                                           (6, 11), (6, 6), (6, 19);

-- Testando interações: Algumas curtidas e matches para validação
-- 1. Sóstenes curte a vaga Full Stack (ID 1)
INSERT INTO curtida (cpf_candidato, id_vaga, cnpj_empresa)
VALUES ('12345678900', 1, NULL);

-- 2. A empresa dona da vaga (98765432000111) vê o perfil do Sóstenes e curte
INSERT INTO curtida (cpf_candidato, id_vaga, cnpj_empresa)
VALUES ('12345678900', 1, '98765432000111');

-- 3. MATCH! Como houve interesse mútuo, o match é consolidado
INSERT INTO match_vaga (cpf_candidato, cnpj_empresa, id_vaga)
VALUES ('12345678900', '98765432000111', 1);

-- 4. A empresa BlueSky (45678901000134) curtiu a candidata Ana ('33344478900')
INSERT INTO curtida (cpf_candidato, id_vaga, cnpj_empresa)
VALUES ('33344478900', 3, '45678901000134');

-- 5. A candidata Ana abre o app e curte a vaga da BlueSky (ID 3)
INSERT INTO curtida (cpf_candidato, id_vaga, cnpj_empresa)
VALUES ('33344478900', 3, NULL);

-- 6. MATCH! O sistema vê que a BlueSky já tinha curtido ela no Passo 4 e consolida o Match
INSERT INTO match_vaga (cpf_candidato, cnpj_empresa, id_vaga)
VALUES ('33344478900', '45678901000134', 3);