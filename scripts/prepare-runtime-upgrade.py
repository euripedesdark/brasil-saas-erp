#!/usr/bin/env python3
"""Prepara Java 25/Boot 4 em worktree; nao instala/deploya nem toca servicos."""
import argparse
import pathlib
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}


def candidate_pom(content):
    # Edicoes localizadas preservam comentarios, exclusoes fiscais e formatacao.
    content, count = re.subn(
        r'(<artifactId>spring-boot-starter-parent</artifactId>\s*<version>)[^<]+',
        r'\g<1>4.1.1', content, count=1)
    if count != 1:
        raise ValueError('Parent Spring Boot nao encontrado')
    content = re.sub(r'<java.version>[^<]+</java.version>', '<java.version>25</java.version>', content)
    content = re.sub(r'\s*<maven.compiler.(?:source|target)>[^<]+</maven.compiler.(?:source|target)>', '', content)
    content = content.replace('<source>21</source>\n                    <target>21</target>',
                              '<release>${java.version}</release>')
    content = re.sub(r'(<artifactId>spring-cloud-dependencies</artifactId>\s*<version>)[^<]+',
                     r'\g<1>2025.1.2', content)
    content = re.sub(r'<springdoc.version>[^<]+</springdoc.version>',
                     '<springdoc.version>3.1.1</springdoc.version>', content)
    content = content.replace('spring-boot-starter-web</artifactId>', 'spring-boot-starter-webmvc</artifactId>')
    content = content.replace('<artifactId>spring-security-test</artifactId>',
                              '<artifactId>spring-boot-starter-security-test</artifactId>')
    content = content.replace('<groupId>org.springframework.security</groupId>\n            <artifactId>spring-boot-starter-security-test',
                              '<groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-starter-security-test')
    content = re.sub(r'<artifactId>spring-ai-openai-spring-boot-starter</artifactId>.*?<version>1.0.0-M3</version>',
                     '<artifactId>spring-ai-starter-model-openai</artifactId>', content, flags=re.S)
    bom = '''
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>2.0.1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>'''
    content = content.replace('<dependencyManagement>\n        <dependencies>',
                              '<dependencyManagement>\n        <dependencies>' + bom, 1)
    # Keep Jackson 2 temporarily for existing integrations; Boot 4 uses Jackson 3.
    starters = '''
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-flyway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-jackson2</artifactId>
        </dependency>'''
    content = content.replace('    <dependencies>\n\n', '    <dependencies>\n' + starters + '\n', 1)
    content = content.replace('<flyway.version>10.17.0</flyway.version>', '')
    content = content.replace('            <version>${flyway.version}</version>\n', '')
    # Plugin has a separate literal pin; delegate its version to Boot as well.
    content = content.replace('<artifactId>flyway-maven-plugin</artifactId>\n                <version>10.17.0</version>',
                              '<artifactId>flyway-maven-plugin</artifactId>\n                <version>${flyway.version}</version>')
    ET.fromstring(content)
    return content


def prepare(destination):
    if destination.exists():
        raise ValueError('Destino ja existe; nao sera sobrescrito')
    # HEAD, not a dirty working tree. No historic migration is rewritten.
    subprocess.run(['git', '-C', str(ROOT), 'worktree', 'add', '--detach', str(destination), 'HEAD'],
                   check=True, env={**__import__('os').environ, 'GIT_LFS_SKIP_SMUDGE': '1'})
    # Inclui os provisionadores desta entrega mesmo antes do commit/PR local.
    for relative in ('installbase.sh', 'compilar.py', 'scripts/prepare-runtime-upgrade.py'):
        shutil.copy2(ROOT / relative, destination / relative)
    pom = destination / 'pom.xml'
    pom.write_text(candidate_pom(pom.read_text()))
    docker = destination / 'Dockerfile'
    docker.write_text(docker.read_text().replace('eclipse-temurin:21-', 'eclipse-temurin:25-'))
    for workflow in (destination / '.github/workflows').glob('*.yml'):
        if 'graylog' in workflow.name:
            continue  # O plugin deve usar o Java exigido pelo host Graylog.
        content = workflow.read_text()
        content = re.sub(r'(java-version:\s*[\'"]?)21([\'"]?\s*(?:#.*)?$)', r'\g<1>25\2', content, flags=re.M)
        workflow.write_text(content)
    print('Candidato preparado em', destination)
    print('Ainda exige adaptar APIs/imports, Security, Hibernate e Spring AI e validar runtime.')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', type=pathlib.Path)
    parser.add_argument('--verify', action='store_true', help='Exige JDK 25 e Maven; executa clean verify')
    args = parser.parse_args(argv)
    if not args.destination:
        print('Alvo: Java 25, Boot 4.1.1, Cloud 2025.1.2, AI 2.0.1, Springdoc 3.1.1.')
        print('Use --destination /caminho/novo para preparar worktree isolada; --verify valida o candidato.')
        return 0
    if args.verify:
        if not shutil.which('mvn') or not shutil.which('javac'):
            raise ValueError('Validacao exige Maven e JDK 25 instalados')
        version = subprocess.check_output(['javac', '-version'], stderr=subprocess.STDOUT, text=True)
        if not re.search(r'javac 25(?:\.|\s)', version):
            raise ValueError('Selecione JDK 25 em JAVA_HOME/PATH antes de verificar')
    destination = args.destination.expanduser().resolve()
    prepare(destination)
    if args.verify:
        subprocess.run(['mvn', '-B', 'clean', 'verify'], cwd=destination, check=True)
        print('Maven clean verify aprovado; homologacao operacional ainda necessaria.')
    else:
        print('NAO VALIDADO: compilacao e testes nao executados. Nenhum servico foi alterado.')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, subprocess.CalledProcessError) as error:
        print('[ERRO]', error, file=sys.stderr)
        sys.exit(1)
