package com.console.app.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

@Service
public class DhcpService {

    @Autowired
    private SshService sshService;

    @Autowired
    private AuditoriaService auditoriaService;

    @Value("${debian.server.ssh.password:123456}")
    private String sudoPass;

    private static final String KEA_CONF = "/etc/kea/kea-dhcp4.conf";
    private static final String KEA_BAK = "/etc/kea/kea-dhcp4.conf.bak";
    private static final String KEA_LEASES = "/var/lib/kea/kea-leases4.csv";

    private static final Pattern MAC_PATTERN =
            Pattern.compile("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$");

    private static final Pattern IP_SUBNET_PATTERN =
            Pattern.compile("^192\\.168\\.100\\.(?:[2-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-4])$");

    private static final Pattern HOSTNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,30}[a-zA-Z0-9])?$");

    public Map<String, Object> obterEstadoDhcp() {

        Map<String, Object> dados = new HashMap<>();

        String status = sshService.executeCommand(
                "systemctl is-active kea-dhcp4-server"
        ).trim();

        dados.put("status", status);
        dados.put("subrede", "192.168.100.0/24");
        dados.put("faixaDinamica", "192.168.100.100 - 192.168.100.150");
        dados.put("dnsAnunciado", "192.168.100.10 (empresa.test)");

        List<Map<String, String>> concessoes = new ArrayList<>();

        String csvOut = sshService.executeCommand(
                "sudo cat " + KEA_LEASES + " 2>/dev/null"
        ).trim();

        if (!csvOut.isEmpty() && !csvOut.contains("Erro SSH")) {

            String[] linhas = csvOut.split("\\R");

            for (int i = 1; i < linhas.length; i++) {

                String linha = linhas[i].trim();

                if (linha.isEmpty()) {
                    continue;
                }

                String[] cols = linha.split(",", -1);

                try {
                    if (cols.length >= 9) {

                        long expireEpoch = Long.parseLong(cols[4].trim());

                        boolean ativa = expireEpoch > Instant.now().getEpochSecond();

                        String validade = DateTimeFormatter
                                .ofPattern("dd/MM/yyyy HH:mm:ss")
                                .withZone(ZoneId.systemDefault())
                                .format(Instant.ofEpochSecond(expireEpoch));

                        Map<String, String> c = new HashMap<>();

                        c.put("ip", cols[0].trim());
                        c.put("mac", cols[1].trim());
                        c.put("validade", validade);
                        c.put("hostname",
                                cols[8].trim().isEmpty()
                                        ? "-"
                                        : cols[8].trim());

                        c.put("estado", ativa ? "Ativa" : "Expirada");

                        concessoes.add(c);
                    }

                } catch (Exception e) {
                    // Ignora linhas inválidas do CSV
                }
            }
        }

        dados.put("concessoes", concessoes);

        String pythonReservas = """
                import json

                cfg = json.load(open('%s'))
                reservas = cfg['Dhcp4']['subnet4'][0].get('reservations', [])

                for r in reservas:
                    print(
                        r.get('hw-address', '') + '|' +
                        r.get('ip-address', '') + '|' +
                        r.get('hostname', '')
                    )
                """.formatted(KEA_CONF);

        String resOut = executarPythonSudo(pythonReservas).trim();

        List<Map<String, String>> reservas = new ArrayList<>();

        if (!resOut.isEmpty()
                && !resOut.contains("Erro SSH")
                && !resOut.contains("Traceback")) {

            for (String linha : resOut.split("\\R")) {

                String[] p = linha.split("\\|", -1);

                if (p.length >= 2) {

                    Map<String, String> r = new HashMap<>();

                    r.put("mac", p[0].trim());
                    r.put("ip", p[1].trim());
                    r.put("hostname",
                            p.length >= 3 && !p[2].trim().isEmpty()
                                    ? p[2].trim()
                                    : "-");

                    reservas.add(r);
                }
            }
        }

        dados.put("reservas", reservas);

        return dados;
    }

    public String adicionarReserva(String mac, String ip, String hostname) {

        String macLimpo = mac != null
                ? mac.trim().toLowerCase()
                : "";

        String ipLimpo = ip != null
                ? ip.trim()
                : "";

        String hostLimpo = hostname != null
                ? hostname.trim()
                : "";

        // Validar MAC
        if (!MAC_PATTERN.matcher(macLimpo).matches()) {

            auditoriaService.registrar(
                    "DHCP:kea",
                    "ADICIONAR_RESERVA",
                    "RECUSADO",
                    "MAC inválido: " + macLimpo
            );

            throw new IllegalArgumentException(
                    "Endereço MAC inválido. Use aa:bb:cc:dd:ee:ff."
            );
        }

        // Validar IP
        if (!IP_SUBNET_PATTERN.matcher(ipLimpo).matches()
                || "192.168.100.10".equals(ipLimpo)) {

            auditoriaService.registrar(
                    "DHCP:kea",
                    "ADICIONAR_RESERVA",
                    "RECUSADO",
                    "IP inválido ou reservado: " + ipLimpo
            );

            throw new IllegalArgumentException(
                    "IP inválido ou reservado à infraestrutura."
            );
        }

        // Validar hostname
        if (!HOSTNAME_PATTERN.matcher(hostLimpo).matches()) {

            throw new IllegalArgumentException(
                    "Hostname inválido. Use 2 a 32 caracteres, letras, números ou hífen."
            );
        }

        String pythonScript = """
                import json
                import os
                import sys
                import tempfile

                config_path = '%s'

                with open(config_path, 'r') as f:
                    cfg = json.load(f)

                subnet = cfg['Dhcp4']['subnet4'][0]
                reservas = subnet.setdefault('reservations', [])

                mac = '%s'
                ip = '%s'
                hostname = '%s'

                conflito = any(
                    r.get('hw-address', '').lower() == mac
                    or r.get('ip-address') == ip
                    for r in reservas
                )

                if conflito:
                    print('CONFLITO')
                    sys.exit(2)

                reservas.append({
                    'hw-address': mac,
                    'ip-address': ip,
                    'hostname': hostname
                })

                fd, temp_path = tempfile.mkstemp(
                    dir=os.path.dirname(config_path),
                    prefix='.kea-'
                )

                with os.fdopen(fd, 'w') as f:
                    json.dump(cfg, f, indent=2)
                    f.write('\\n')

                os.replace(temp_path, config_path)

                print('OK')
                """.formatted(
                KEA_CONF,
                macLimpo,
                ipLimpo,
                hostLimpo
        );

        String script = """
                set -e

                cp %s %s

                rollback() {
                    cp %s %s
                }

                trap rollback ERR

                %s

                kea-dhcp4 -t %s

                systemctl reload kea-dhcp4-server

                trap - ERR

                echo OK
                """.formatted(
                KEA_CONF,
                KEA_BAK,
                KEA_BAK,
                KEA_CONF,
                comandoPython(pythonScript),
                KEA_CONF
        );

        String saida = executarScriptSudo(script);

        if (saida.contains("Erro SSH")
                || saida.contains("CONFLITO")
                || !saida.contains("OK")) {

            auditoriaService.registrar(
                    "DHCP:kea",
                    "ADICIONAR_RESERVA",
                    "FALHA",
                    "Falha ao adicionar reserva: " + saida
            );

            return "Reserva recusada ou falhou. Configuração anterior preservada: "
                    + saida;
        }

        auditoriaService.registrar(
                "DHCP:kea",
                "ADICIONAR_RESERVA",
                "SUCESSO",
                String.format(
                        "Reserva %s -> %s (%s)",
                        macLimpo,
                        ipLimpo,
                        hostLimpo
                )
        );

        return "Reserva DHCP criada com sucesso ("
                + hostLimpo + ": "
                + macLimpo + " -> "
                + ipLimpo
                + ") e serviço recarregado!";
    }

    public String removerReserva(String mac) {

        String macLimpo = mac != null
                ? mac.trim().toLowerCase()
                : "";

        if (!MAC_PATTERN.matcher(macLimpo).matches()) {

            throw new IllegalArgumentException(
                    "MAC inválido."
            );
        }

        String pythonScript = """
                import json
                import os
                import tempfile

                config_path = '%s'
                mac = '%s'

                with open(config_path, 'r') as f:
                    cfg = json.load(f)

                subnet = cfg['Dhcp4']['subnet4'][0]

                reservas = subnet.get('reservations', [])

                subnet['reservations'] = [
                    r for r in reservas
                    if r.get('hw-address', '').lower() != mac
                ]

                fd, temp_path = tempfile.mkstemp(
                    dir=os.path.dirname(config_path),
                    prefix='.kea-'
                )

                with os.fdopen(fd, 'w') as f:
                    json.dump(cfg, f, indent=2)
                    f.write('\\n')

                os.replace(temp_path, config_path)

                print('OK')
                """.formatted(
                KEA_CONF,
                macLimpo
        );

        String script = """
                set -e

                cp %s %s

                rollback() {
                    cp %s %s
                }

                trap rollback ERR

                %s

                kea-dhcp4 -t %s

                systemctl reload kea-dhcp4-server

                trap - ERR

                echo OK
                """.formatted(
                KEA_CONF,
                KEA_BAK,
                KEA_BAK,
                KEA_CONF,
                comandoPython(pythonScript),
                KEA_CONF
        );

        String saida = executarScriptSudo(script);

        if (saida.contains("Erro SSH") || !saida.contains("OK")) {

            auditoriaService.registrar(
                    "DHCP:kea",
                    "REMOVER_RESERVA",
                    "FALHA",
                    saida
            );

            return "Falha ao remover reserva DHCP: " + saida;
        }

        auditoriaService.registrar(
                "DHCP:kea",
                "REMOVER_RESERVA",
                "SUCESSO",
                "MAC removido: " + macLimpo
        );

        return "Reserva removida com sucesso! "
                + "(Concessões ativas permanecem válidas até expiração/renovação).";
    }

    public String restaurarBackupDhcp() {

        String script = """
                set -e

                test -f %s

                cp %s %s

                rollback() {
                    cp %s %s
                }

                trap rollback ERR

                kea-dhcp4 -t %s

                systemctl reload kea-dhcp4-server

                trap - ERR

                echo OK
                """.formatted(
                KEA_BAK,
                KEA_BAK,
                KEA_CONF,
                KEA_CONF,
                KEA_BAK,
                KEA_CONF
        );

        String saida = executarScriptSudo(script);

        if (saida.contains("Erro SSH") || !saida.contains("OK")) {

            auditoriaService.registrar(
                    "DHCP:kea",
                    "RESTAURAR_CONFIG",
                    "FALHA",
                    saida
            );

            return "Falha ao restaurar backup do DHCP: " + saida;
        }

        auditoriaService.registrar(
                "DHCP:kea",
                "RESTAURAR_CONFIG",
                "SUCESSO",
                "Restaurado " + KEA_BAK
        );

        return "Configuração anterior do Kea DHCPv4 restaurada com sucesso!";
    }

    private String executarPythonSudo(String pythonScript) {

        return executarScriptSudo(
                comandoPython(pythonScript) + "\necho OK"
        );
    }

    private String comandoPython(String pythonScript) {

        String base64 = Base64.getEncoder().encodeToString(
                pythonScript.getBytes(StandardCharsets.UTF_8)
        );

        return "python3 -c "
                + shellQuote(
                "import base64; exec(base64.b64decode('"
                        + base64
                        + "'))"
        );
    }

    private String executarScriptSudo(String script) {

        String base64 = Base64.getEncoder().encodeToString(
                script.getBytes(StandardCharsets.UTF_8)
        );

        String comando = "printf '%s\\n' "
                + shellQuote(sudoPass)
                + " | sudo -S bash -c "
                + shellQuote(
                "printf '%s' '"
                        + base64
                        + "' | base64 -d | bash"
        );

        return sshService.executeCommand(comando);
    }

    private String shellQuote(String texto) {

        if (texto == null) {
            return "''";
        }

        return "'" + texto.replace("'", "'\\''") + "'";
    }
}