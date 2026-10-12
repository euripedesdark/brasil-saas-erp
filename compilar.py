#!/usr/bin/env python3
"""
Brasil SaaS ERP - Script de compilação e deploy completo.

Este script faz:
1. Build do frontend React (se existir)
2. Build do monólito principal com Maven
3. Build de todos os módulos independentes
4. Instalação dos JARs no repositório Maven local
5. Opcional: Iniciar serviços como JARs ou via systemd

Uso:
  python compilar.py                    # Apenas compila
  python compilar.py --install         # Compila e instala JARs no Maven
  python compilar.py --run             # Compila, instala e inicia serviços como JARs
  python compilar.py --systemd          # Compila, instala e configura via systemd
  python compilar.py --monolith-only    # Apenas compila o monólito
  python compilar.py --modules-only    # Apenas compila os módulos
"""
import json
import os
import subprocess
import sys
import argparse
import time
import signal


# Cores para output
class Colors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'


# Lista de módulos na ordem correta de build
MODULES = [
    "shared",      # Library - deve ser buildado primeiro
    "core",        # Modulo base
    "cadastro",
    "financeiro",
    "vendas",
    "compras",
    "estoque",
    "fiscal",
    "rh",
    "ia",
    "servicos",
]

# Portas dos modulos
MODULE_PORTS = {
    "core": 8081,
    "cadastro": 8082,
    "financeiro": 8083,
    "vendas": 8084,
    "compras": 8085,
    "estoque": 8086,
    "fiscal": 8087,
    "rh": 8088,
    "ia": 8089,
    "servicos": 8080,
}

# Processos em execucao
running_processes = {}


def signal_handler(sig, frame):
    """Tratar Ctrl+C para matar processos filhas"""
    print(f"\n{Colors.WARNING}Recebido Ctrl+C. Parando servicos...{Colors.ENDC}")
    for module, proc in running_processes.items():
        if proc.poll() is None:
            print(f"  Parando {module} (PID: {proc.pid})...")
            proc.terminate()
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()
    sys.exit(0)


signal.signal(signal.SIGINT, signal_handler)


def print_header(text):
    print(f"\n{Colors.HEADER}{Colors.BOLD}{'=' * 60}{Colors.ENDC}")
    print(f"{Colors.HEADER}{Colors.BOLD}{text}{Colors.ENDC}")
    print(f"{Colors.HEADER}{Colors.BOLD}{'=' * 60}{Colors.ENDC}")


def print_success(text):
    print(f"{Colors.OKGREEN}[OK] {text}{Colors.ENDC}")


def print_error(text):
    print(f"{Colors.FAIL}[ERRO] {text}{Colors.ENDC}")


def print_warning(text):
    print(f"{Colors.WARNING}[AVISO] {text}{Colors.ENDC}")


def print_info(text):
    print(f"{Colors.OKBLUE}[INFO] {text}{Colors.ENDC}")


def run_command(cmd, description="", cwd=None, check=True):
    """Executa um comando e retorna True se sucesso"""
    print_info(description or cmd)
    
    try:
        result = subprocess.run(
            cmd,
            shell=True,
            cwd=cwd,
            check=check,
            capture_output=True,
            text=True
        )
        
        if result.returncode != 0:
            print_error(f"Comando falhou: {cmd}")
            if result.stderr:
                error_lines = result.stderr.split('\n')[-10:]
                for line in error_lines:
                    if line.strip():
                        print(f"  {line}")
            return False
        
        print_success(f"Concluido: {description or cmd}")
        return True
    except subprocess.CalledProcessError as e:
        print_error(f"Comando falhou: {cmd}")
        print_error(f"Erro: {e.stderr[-500:]}")
        return False
    except Exception as e:
        print_error(f"Excecao: {str(e)}")
        return False


def build_frontend():
    """
    Build do frontend React.

    O diretorio e' escolhido por TER o script "build" no package.json, e nao
    por existir.

    A escolha anterior era "o primeiro diretorio que existir", e o `frontend/`
    existe: contem apenas `.gitkeep`, sem package.json. O app React de verdade
    esta em src/main/resources/static/react. Com a escolha por existencia, o
    script entrava no diretorio vazio e o npm subia ate o package.json da RAIZ
    do repositorio, que tem duas dependencias e nenhum script:

        npm error Missing script: "build"

    O sintoma e' confuso porque parece que o projeto nao tem frontend: tem, em
    outro lugar. Por isso a verificacao e' pelo script, que e' o que diz se o
    diretorio e' buildavel.
    """
    candidatos = [
        os.path.join("src", "main", "resources", "static", "react"),
        "frontend",
    ]

    frontend_dir = None
    for d in candidatos:
        package = os.path.join(d, "package.json")
        if not os.path.isfile(package):
            continue
        try:
            with open(package, encoding="utf-8") as fh:
                scripts = json.load(fh).get("scripts", {})
        except (OSError, ValueError):
            continue
        if "build" in scripts:
            frontend_dir = d
            break

    if not frontend_dir:
        # Nenhum dos dois e' buildavel. Dizer QUAL foi descartado e o que ele
        # tem, e' o que evita a proxima tentativa no lugar errado.
        print_warning("Nenhum frontend com o script 'build' foi encontrado.")
        for d in candidatos:
            package = os.path.join(d, "package.json")
            if os.path.isfile(package):
                try:
                    with open(package, encoding="utf-8") as fh:
                        tem = ", ".join(sorted(json.load(fh).get("scripts", {}))) or "nenhum"
                except (OSError, ValueError):
                    tem = "(package.json ilegivel)"
                print_warning(f"  {d}/package.json existe, scripts: {tem}")
            elif os.path.isdir(d):
                print_warning(f"  {d}/ existe, mas sem package.json")
        print_warning("O app React fica em src/main/resources/static/react.")
        return True

    print_header("BUILD DO FRONTEND")
    print_info(f"Diretorio: {frontend_dir}")

    if not run_command(
        "npm install",
        "Instalando dependencias do frontend",
        cwd=frontend_dir
    ):
        return False

    if not run_command(
        "npm run build",
        "Compilando frontend React",
        cwd=frontend_dir
    ):
        return False

    return True


def build_monolith():
    """Build do monolito principal"""
    print_header("BUILD DO MONOLITO PRINCIPAL")
    
    maven_cmd = "./mvnw" if os.path.exists("mvnw") else "mvn"
    
    if not run_command(
        f"{maven_cmd} clean package -DskipTests",
        "Compilando Brasil SaaS ERP (Monolito)"
    ):
        return False
    
    target_dir = "target"
    if os.path.isdir(target_dir):
        jars = [
            f for f in os.listdir(target_dir)
            if f.endswith(".jar") and 
            not f.endswith(("-sources.jar", "-javadoc.jar", ".original"))
        ]
        if jars:
            jar_file = sorted(jars)[-1]
            print_success(f"JAR do monolito gerado: target/{jar_file}")
        else:
            print_warning("Nenhum JAR encontrado na pasta target/")
    
    return True


def build_module(module_name):
    """Build de um modulo especifico"""
    module_path = f"modules/{module_name}"
    
    if not os.path.isdir(module_path):
        print_warning(f"Modulo '{module_name}' nao encontrado em {module_path}")
        return False
    
    pom_file = os.path.join(module_path, "pom.xml")
    if not os.path.exists(pom_file):
        print_warning(f"pom.xml nao encontrado para modulo {module_name}")
        return False
    
    print_header(f"BUILD DO MODULO: {module_name.upper()}")
    
    if not run_command(
        "mvn clean compile -DskipTests",
        f"Compilando modulo {module_name}",
        cwd=module_path
    ):
        return False
    
    if not run_command(
        "mvn install -DskipTests",
        f"Instalando modulo {module_name} no Maven",
        cwd=module_path
    ):
        return False
    
    return True


def build_all_modules():
    """Build de todos os modulos na ordem correta"""
    print_header("BUILD DE TODOS OS MODULOS")
    print_info(f"Ordem: {' -> '.join(MODULES)}")
    
    for module in MODULES:
        if not build_module(module):
            print_error(f"Falha ao compilar modulo {module}")
            return False
    
    return True


def run_jar(module_name):
    """Executa um modulo como JAR"""
    module_path = f"modules/{module_name}"
    target_dir = os.path.join(module_path, "target")
    
    if not os.path.isdir(target_dir):
        print_warning(f"Pasta target nao encontrada para {module_name}")
        return None
    
    jars = [
        f for f in os.listdir(target_dir)
        if f.startswith(f"brasil-saas-erp-{module_name}") and 
        f.endswith(".jar") and 
        not f.endswith(("-sources.jar", "-javadoc.jar", ".original"))
    ]
    
    if not jars:
        print_warning(f"JAR nao encontrado para {module_name}")
        return None
    
    jar_file = os.path.join(target_dir, sorted(jars)[-1])
    port = MODULE_PORTS.get(module_name, 8080)
    
    cmd = f"java -jar {jar_file} --server.port={port}"
    print_info(f"Iniciando {module_name} na porta {port}")
    
    try:
        proc = subprocess.Popen(
            cmd,
            shell=True,
            cwd=module_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        running_processes[module_name] = proc
        
        time.sleep(3)
        if proc.poll() is not None:
            print_error(f"Falha ao iniciar {module_name}")
            return None
        
        print_success(f"{module_name} iniciado na porta {port} (PID: {proc.pid})")
        return proc
    except Exception as e:
        print_error(f"Erro ao iniciar {module_name}: {str(e)}")
        return None


def install_systemd_service(module_name):
    """Instala um modulo como servico do systemd"""
    service_file = f"systemd_units/brasil-saas-erp-{module_name}.service"
    
    if not os.path.exists(service_file):
        print_warning(f"Arquivo de servico nao encontrado: {service_file}")
        return False
    
    print_header(f"INSTALANDO SERVICO: {module_name}")
    
    commands = [
        f"sudo cp {service_file} /etc/systemd/system/",
        "sudo systemctl daemon-reload",
        f"sudo systemctl enable brasil-saas-erp-{module_name}",
        f"sudo systemctl start brasil-saas-erp-{module_name}",
    ]
    
    for cmd in commands:
        if not run_command(cmd, f"Executando: {cmd}", check=False):
            print_warning(f"Comando falhou (pode ser falta de sudo): {cmd}")
    
    return True


def main():
    parser = argparse.ArgumentParser(
        description="Compilacao e deploy do Brasil SaaS ERP"
    )
    parser.add_argument(
        "--install",
        action="store_true",
        help="Instala JARs no repositorio Maven local"
    )
    parser.add_argument(
        "--run",
        action="store_true",
        help="Inicia os modulos como JARs apois compilacao"
    )
    parser.add_argument(
        "--systemd",
        action="store_true",
        help="Instala servicos via systemd (requer sudo)"
    )
    parser.add_argument(
        "--monolith-only",
        action="store_true",
        help="Compila apenas o monolito principal"
    )
    parser.add_argument(
        "--modules-only",
        action="store_true",
        help="Compila apenas os modulos (nao o monolito)"
    )
    parser.add_argument(
        "--no-frontend",
        action="store_true",
        help="Pula o build do frontend"
    )
    
    parser.add_argument('--prepare-upgrade', metavar='DESTINO',
                        help='Prepara Java 25/Spring Boot 4 em worktree isolada; nao faz deploy')
    parser.add_argument('--verify-upgrade', action='store_true',
                        help='Executa clean verify no candidato com JDK 25/Maven')
    args = parser.parse_args()
    if args.verify_upgrade and not args.prepare_upgrade:
        parser.error('--verify-upgrade exige --prepare-upgrade')
    if args.prepare_upgrade:
        command = [sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)),
                    'scripts', 'prepare-runtime-upgrade.py'), '--destination', args.prepare_upgrade]
        if args.verify_upgrade:
            command.append('--verify')
        return subprocess.call(command)

    
    script_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(script_dir)
    print_info(f"Diretorio de trabalho: {script_dir}")
    
    start_time = time.time()
    
    # ETAPA 1: Build do Frontend
    if not args.no_frontend and not args.modules_only:
        if not build_frontend():
            print_error("Falha no build do frontend")
            if not args.modules_only:
                sys.exit(1)
    
    # ETAPA 2: Build do Monolito
    if not args.modules_only:
        if not build_monolith():
            print_error("Falha no build do monolito")
            if not args.install and not args.run and not args.systemd:
                sys.exit(1)
    
    # ETAPA 3: Build dos Modulos
    if args.modules_only or args.install or args.run or args.systemd:
        if not build_all_modules():
            print_error("Falha no build dos modulos")
            sys.exit(1)
    
    # ETAPA 4: Iniciar servicos
    if args.run:
        print_header("INICIANDO MODULOS COMO JARs")
        
        service_modules = [m for m in MODULES if m != "shared"]
        
        for module in service_modules:
            proc = run_jar(module)
            if proc is None:
                print_warning(f"Nao foi possivel iniciar {module} como JAR")
        
        if running_processes:
            print_header("SERVICOS EM EXECUCAO")
            for module, proc in running_processes.items():
                print_info(f"  {module}: PID {proc.pid}, Porta {MODULE_PORTS.get(module, 'N/A')}")
            print_warning("Pressione Ctrl+C para parar todos os servicos")
            
            try:
                while True:
                    time.sleep(1)
            except KeyboardInterrupt:
                signal_handler(None, None)
    
    elif args.systemd:
        print_header("INSTALANDO SERVICOS VIA SYSTEMd")
        print_warning("Este processo requer privilegio de sudo")
        
        service_modules = [m for m in MODULES if m != "shared"]
        
        for module in service_modules:
            install_systemd_service(module)
        
        print_success("Instalacao de servicos concluida")
        print_info("Para iniciar todos os servicos:")
        print_info("  ./scripts/manage_services.sh start-all")
    
    # RESUMO FINAL
    elapsed = time.time() - start_time
    minutes = int(elapsed // 60)
    seconds = int(elapsed % 60)
    
    print_header("RESUMO DA EXECUCAO")
    print_success(f"Tempo total: {minutes}m {seconds}s")
    
    if not args.modules_only:
        print_success("[OK] Monolito: Compilado")
    if args.modules_only or args.install or args.run:
        print_success("[OK] Modulos: Compilados e instalados")
    if args.run:
        print_success(f"[OK] {len(running_processes)} servicos iniciados")
    if args.systemd:
        print_success("[OK] Servicos systemd configurados")
    
    print_info("\nPara gerenciar servicos manualmente:")
    print_info("  ./scripts/manage_services.sh [start|stop|restart|status] [modulo]")
    print_info("  ./scripts/manage_services.sh start-all")
    print_info("  ./scripts/manage_services.sh stop-all")


if __name__ == "__main__":
    sys.exit(main())
