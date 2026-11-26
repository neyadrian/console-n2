package com.console.app.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class DnsService {

    @Autowired
    private SshService sshService;

    @Autowired
    private AuditoriaService auditoriaService;

    @Value("${debian.server.ssh.password:123456}")
    private String sudoPass;

    private static final String ZONE_FILE = "/etc/bind/db.empresa.test";
    private static final String ZONE_BAK = "/etc/bind/db.empresa.test.bak";
    private static final String ZONE_NAME = "empresa.test";

    private static final Pattern HOST_PATTERN =
            Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");

    private static final Pattern IPV4_PATTERN =
            Pattern.compile("^192\\.168\\.100\\.(?:[1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-4])$");

    private static final Pattern FQDN_PATTERN =
            Pattern.compile("^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+$");

    private static final Set<String> TIPOS_PERMITIDOS =
            Set.of("A", "CNAME", "TXT");

    private static final Set<String> REGISTROS_PROTEGIDOS =
            Set.of("ns1", "mail", "site");

    public Map<String, Object> obterEstadoZona() {

        Map<String, Object> dados = new HashMap<>();

        String status = sshService
                .executeCommand("systemctl is-active bind9")
                .trim();

        String conteudo = sshService
                .executeCommand("cat " + ZONE_FILE);

        List<Map<String, String>> registros = new ArrayList<>();

        String serial = "Desconhecido";

        for (String linha : conteudo.split("\\R")) {

            String l = linha.trim();

            if (l.contains("; Serial")) {
                serial = l.split(";")[0].trim();
            }

            if (l.isEmpty()
                    || l.startsWith(";")
                    || l.startsWith("$")
                    || l.startsWith("@")
                    || l.contains("SOA")
                    || l.startsWith(")")) {
                continue;
            }

            String[] partes = l.split("\\s+");

            if (partes.length >= 4 && "IN".equals(partes[1])) {

                Map<String, String> reg = new HashMap<>();

                reg.put("nome", partes[0]);
                reg.put("tipo", partes[2]);
                reg.put("valor", partes[3]);

                registros.add(reg);
            }
        }

        dados.put("status", status);
        dados.put("zona", ZONE_NAME);
        dados.put("porta", "53 (UDP/TCP)");
        dados.put("serial", serial);
        dados.put("registros", registros);

        return dados;
    }

    public String salvarRegistro(String nome, String tipo, String valor) {

        String nomeLimpo = (nome != null)
                ? nome.trim().toLowerCase()
                : "";

        String tipoLimpo = (tipo != null)
                ? tipo.trim().toUpperCase()
                : "";

        String valorLimpo = (valor != null)
                ? valor.trim()
                : "";

        if (!HOST_PATTERN.matcher(nomeLimpo).matches()) {

            auditoriaService.registrar(
                    "DNS:" + ZONE_NAME,
                    "SALVAR_REGISTRO",
                    "RECUSADO",
                    "Nome inválido: " + nomeLimpo
            );

            throw new IllegalArgumentException(
                    "Nome de host inválido. Use letras minúsculas, números e hífen."
            );
        }

        if (!TIPOS_PERMITIDOS.contains(tipoLimpo)) {

            auditoriaService.registrar(
                    "DNS:" + ZONE_NAME,
                    "SALVAR_REGISTRO",
                    "RECUSADO",
                    "Tipo inválido: " + tipoLimpo
            );

            throw new IllegalArgumentException(
                    "Tipo de registro não autorizado. Permitidos: A, CNAME, TXT."
            );
        }

        if ("A".equals(tipoLimpo)
                && !IPV4_PATTERN.matcher(valorLimpo).matches()) {

            auditoriaService.registrar(
                    "DNS:" + ZONE_NAME,
                    "SALVAR_REGISTRO",
                    "RECUSADO",
                    "IP fora da sub-rede: " + valorLimpo
            );

            throw new IllegalArgumentException(
                    "Informe um IPv4 da rede 192.168.100.1 até 192.168.100.254."
            );
        }

        if ("CNAME".equals(tipoLimpo)
                && !FQDN_PATTERN.matcher(valorLimpo).matches()) {

            throw new IllegalArgumentException(
                    "Informe um FQDN válido terminado em ponto. Ex: site.empresa.test."
            );
        }

        if ("TXT".equals(tipoLimpo)) {

            if (valorLimpo.isEmpty()
                    || valorLimpo.length() > 255
                    || valorLimpo.contains("\n")
                    || valorLimpo.contains("\r")) {

                throw new IllegalArgumentException(
                        "Registro TXT inválido. O texto deve ter entre 1 e 255 caracteres."
                );
            }

            valorLimpo = valorLimpo
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");

            valorLimpo = "\"" + valorLimpo + "\"";
        }

        if (REGISTROS_PROTEGIDOS.contains(nomeLimpo)) {

            auditoriaService.registrar(
                    "DNS:" + ZONE_NAME,
                    "SALVAR_REGISTRO",
                    "RECUSADO",
                    "Tentativa de alterar registro protegido: " + nomeLimpo
            );

            throw new IllegalArgumentException(
                    "O registro '" + nomeLimpo + "' é protegido pela infraestrutura."
            );
        }

        String novaLinha = String.format(
                "%-12s IN  %-6s %s",
                nomeLimpo,
                tipoLimpo,
                valorLimpo
        );

        // Script executado remotamente
        String script = String.format("""
                set -e

                cp %s %s

                trap 'cp %s %s; named-checkzone %s %s >/dev/null 2>&1; systemctl reload bind9 >/dev/null 2>&1' ERR

                sed -i '/^%s[[:space:]]\\+IN[[:space:]]/d' %s

                printf '%%s\\\\n' '%s' >> %s

                perl -i -pe 's/(\\\\d+)(\\\\s*;\\\\s*Serial)/($1+1).$2/e' %s

                named-checkzone %s %s

                systemctl reload bind9

                trap - ERR

                echo OK
                """,
                ZONE_FILE,
                ZONE_BAK,
                ZONE_BAK,
                ZONE_FILE,
                ZONE_NAME,
                ZONE_FILE,
                nomeLimpo,
                ZONE_FILE,
                novaLinha,
                ZONE_FILE,
                ZONE_FILE,
                ZONE_NAME,
                ZONE_FILE
        );

        String res = executarScriptSudo(script);

        if (res.contains("Erro SSH") || !res.contains("OK")) {

            auditoriaService.registrar(
                    "DNS:" + ZONE_NAME,
                    "SALVAR_REGISTRO",
                    "FALHA",
                    "Falha na validação/reload: " + res
            );

            return "Erro ao aplicar registro DNS: " + res;
        }

        auditoriaService.registrar(
                "DNS:" + ZONE_NAME,
                "SALVAR_REGISTRO",
                "SUCESSO",
                nomeLimpo + " " + tipoLimpo + " -> " + valorLimpo
        );

        return "Registro DNS '" + nomeLimpo + "." + ZONE_NAME
                + "' (" + tipoLimpo + ") aplicado e zona recarregada!";
    }

    public String removerRegistro(String nome) {

        String nomeLimpo = (nome != null)
                ? nome.trim().toLowerCase()
                : "";

        if (!HOST_PATTERN.matcher(nomeLimpo).matches()
                || REGISTROS_PROTEGIDOS.contains(nomeLimpo)) {

            auditoriaService.registrar(
                    "DNS:" + ZONE_NAME,
                    "REMOVER_REGISTRO",
                    "RECUSADO",
                    "Registro protegido ou inválido: " + nomeLimpo
            );

            throw new IllegalArgumentException(
                    "Registro inválido ou protegido contra exclusão."
            );
        }

        String script = String.format("""
                set -e

                cp %s %s

                trap 'cp %s %s; named-checkzone %s %s >/dev/null 2>&1; systemctl reload bind9 >/dev/null 2>&1' ERR

                sed -i '/^%s[[:space:]]\\+IN[[:space:]]/d' %s

                perl -i -pe 's/(\\\\d+)(\\\\s*;\\\\s*Serial)/($1+1).$2/e' %s

                named-checkzone %s %s

                systemctl reload bind9

                trap - ERR

                echo OK
                """,
                ZONE_FILE,
                ZONE_BAK,
                ZONE_BAK,
                ZONE_FILE,
                ZONE_NAME,
                ZONE_FILE,
                nomeLimpo,
                ZONE_FILE,
                ZONE_FILE,
                ZONE_NAME,
                ZONE_FILE
        );

        String res = executarScriptSudo(script);

        if (res.contains("Erro SSH") || !res.contains("OK")) {

            auditoriaService.registrar(
                    "DNS:" + ZONE_NAME,
                    "REMOVER_REGISTRO",
                    "FALHA",
                    res
            );

            return "Falha ao remover registro DNS: " + res;
        }

        auditoriaService.registrar(
                "DNS:" + ZONE_NAME,
                "REMOVER_REGISTRO",
                "SUCESSO",
                "Removido: " + nomeLimpo
        );

        return "Registro '" + nomeLimpo + "' removido com sucesso!";
    }

    public String restaurarBackupZona() {

        String script = String.format("""
                set -e

                cp %s %s

                trap 'cp %s %s; named-checkzone %s %s >/dev/null 2>&1; systemctl reload bind9 >/dev/null 2>&1' ERR

                test -f %s

                cp %s %s

                perl -i -pe 's/(\\\\d+)(\\\\s*;\\\\s*Serial)/($1+1).$2/e' %s

                named-checkzone %s %s

                systemctl reload bind9

                trap - ERR

                echo OK
                """,
                ZONE_FILE,
                ZONE_FILE + ".rollback",
                ZONE_FILE + ".rollback",
                ZONE_FILE,
                ZONE_NAME,
                ZONE_FILE,
                ZONE_BAK,
                ZONE_BAK,
                ZONE_FILE,
                ZONE_FILE,
                ZONE_NAME,
                ZONE_FILE
        );

        String res = executarScriptSudo(script);

        if (res.contains("Erro SSH") || !res.contains("OK")) {

            auditoriaService.registrar(
                    "DNS:" + ZONE_NAME,
                    "RESTAURAR_ZONA",
                    "FALHA",
                    res
            );

            return "Não foi possível restaurar o backup da zona: " + res;
        }

        auditoriaService.registrar(
                "DNS:" + ZONE_NAME,
                "RESTAURAR_ZONA",
                "SUCESSO",
                "Zona restaurada a partir de " + ZONE_BAK
        );

        return "Versão anterior da zona DNS restaurada e recarregada com sucesso!";
    }

    public String testarConsultaDig(String fqdn, String tipo) {

        String hostConsulta = (fqdn != null
                && FQDN_PATTERN.matcher(fqdn).matches())
                ? fqdn
                : "site.empresa.test.";

        String tipoConsulta = (tipo != null
                && TIPOS_PERMITIDOS.contains(tipo.toUpperCase()))
                ? tipo.toUpperCase()
                : "A";

        return sshService.executeCommand(
                "dig @192.168.100.10 "
                        + hostConsulta
                        + " "
                        + tipoConsulta
                        + " +noall +answer +comments"
        );
    }

    private String executarScriptSudo(String script) {

        String base64 = Base64.getEncoder().encodeToString(
                script.getBytes(StandardCharsets.UTF_8)
        );

        String comando = "printf '%s\\n' "
                + shellQuote(sudoPass)
                + " | sudo -S bash -c "
                + shellQuote("printf '%s' '" + base64
                + "' | base64 -d | bash");

        return sshService.executeCommand(comando);
    }

    private String shellQuote(String texto) {

        if (texto == null) {
            return "''";
        }

        return "'" + texto.replace("'", "'\\''") + "'";
    }
}