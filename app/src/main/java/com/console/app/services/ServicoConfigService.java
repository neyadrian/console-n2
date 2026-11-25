package com.console.app.services;

import com.console.app.models.ConfiguracaoVersao;
import com.console.app.repositories.ConfiguracaoVersaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ServicoConfigService {

    @Autowired
    private SshService sshService;

    @Autowired
    private DebianAgentService debianAgentService;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private ConfiguracaoVersaoRepository versaoRepository;

    @Value("${debian.server.ssh.password:123456}")
    private String sudoPass;

    public Map<String, String> lerConfiguracoesAtuais() {
        Map<String, String> configs = new HashMap<>();

        String ftpMax = sshService.executeCommand("grep -E '^max_clients=' /etc/vsftpd.conf | cut -d'=' -f2").trim();
        configs.put("vsftpd_max_clients", ftpMax.isEmpty() ? "50 (padrão)" : ftpMax);

        String postfixSize = sshService.executeCommand("postconf -h message_size_limit").trim();
        configs.put("postfix_size_limit", postfixSize.isEmpty() ? "10240000" : postfixSize);

        String filaPostfix = sshService.executeCommand("postqueue -p").trim();
        configs.put("postfix_queue", filaPostfix.isEmpty() ? "Fila vazia" : filaPostfix);

        String dovecotMax = sshService.executeCommand("doveconf -h mail_max_userip_connections 2>/dev/null || grep -E '^\\s*mail_max_userip_connections' /etc/dovecot/conf.d/20-imap.conf | awk '{print $3}'").trim();
        configs.put("dovecot_max_conn", dovecotMax.isEmpty() ? "10 (padrão)" : dovecotMax);

        String sshTries = sshService.executeCommand("echo '" + sudoPass + "' | sudo -S sshd -T | grep -i '^maxauthtries ' | awk '{print $2}'").trim();
        configs.put("ssh_max_tries", sshTries.isEmpty() ? "6" : sshTries);

        return configs;
    }

    public String alterarConfiguracao(String servico, String parametro, int novoValor) {
        String usuarioAtual = SecurityContextHolder.getContext().getAuthentication().getName();
        String valorAnterior = "";
        String cmdBackup = "";
        String cmdAplicar = "";
        String cmdValidar = "true";
        String cmdRollback = "";

        switch (servico + ":" + parametro) {
            case "vsftpd:max_clients":
                if (novoValor < 1 || novoValor > 500) {
                    auditoriaService.registrar("CONFIG:vsftpd", "ALTERAR_MAX_CLIENTS", "RECUSADO", "Valor fora da faixa (1-500): " + novoValor);
                    throw new IllegalArgumentException("max_clients deve estar entre 1 e 500.");
                }
                valorAnterior = sshService.executeCommand("grep -E '^max_clients=' /etc/vsftpd.conf | cut -d'=' -f2").trim();
                if (valorAnterior.isEmpty()) valorAnterior = "50";
                cmdBackup = "cp /etc/vsftpd.conf /etc/vsftpd.conf.bak_n2";
                cmdAplicar = "grep -q '^max_clients=' /etc/vsftpd.conf && sed -i 's/^max_clients=.*/max_clients=" + novoValor + "/' /etc/vsftpd.conf || echo 'max_clients=" + novoValor + "' >> /etc/vsftpd.conf";
                cmdRollback = "cp /etc/vsftpd.conf.bak_n2 /etc/vsftpd.conf";
                break;

            case "postfix:message_size_limit":
                if (novoValor < 1048576 || novoValor > 104857600) {
                    auditoriaService.registrar("CONFIG:postfix", "ALTERAR_SIZE_LIMIT", "RECUSADO", "Valor fora da faixa (1MB-100MB): " + novoValor);
                    throw new IllegalArgumentException("message_size_limit deve estar entre 1048576 (1MB) e 104857600 (100MB).");
                }
                valorAnterior = sshService.executeCommand("postconf -h message_size_limit").trim();
                cmdBackup = "cp /etc/postfix/main.cf /etc/postfix/main.cf.bak_n2";
                cmdAplicar = "postconf -e 'message_size_limit = " + novoValor + "'";
                cmdValidar = "postfix check";
                cmdRollback = "cp /etc/postfix/main.cf.bak_n2 /etc/postfix/main.cf";
                break;

            case "dovecot:mail_max_userip_connections":
                if (novoValor < 1 || novoValor > 100) {
                    auditoriaService.registrar("CONFIG:dovecot", "ALTERAR_MAX_CONN", "RECUSADO", "Valor fora da faixa (1-100): " + novoValor);
                    throw new IllegalArgumentException("mail_max_userip_connections deve estar entre 1 e 100.");
                }
                valorAnterior = sshService.executeCommand("doveconf -h mail_max_userip_connections").trim();
                cmdBackup = "cp /etc/dovecot/conf.d/20-imap.conf /etc/dovecot/conf.d/20-imap.conf.bak_n2";
                cmdAplicar = "grep -q '^\\s*mail_max_userip_connections' /etc/dovecot/conf.d/20-imap.conf && sed -i 's/^#*\\s*mail_max_userip_connections.*/  mail_max_userip_connections = " + novoValor + "/' /etc/dovecot/conf.d/20-imap.conf || sed -i '/protocol imap {/a \\  mail_max_userip_connections = " + novoValor + "' /etc/dovecot/conf.d/20-imap.conf";
                cmdValidar = "doveconf -n > /dev/null";
                cmdRollback = "cp /etc/dovecot/conf.d/20-imap.conf.bak_n2 /etc/dovecot/conf.d/20-imap.conf";
                break;

            case "ssh:MaxAuthTries":
                if (novoValor < 1 || novoValor > 20) {
                    auditoriaService.registrar("CONFIG:ssh", "ALTERAR_MAX_AUTH_TRIES", "RECUSADO", "Valor fora da faixa (1-20): " + novoValor);
                    throw new IllegalArgumentException("MaxAuthTries deve estar entre 1 e 20.");
                }
                valorAnterior = sshService.executeCommand("echo '" + sudoPass + "' | sudo -S sshd -T | grep -i '^maxauthtries ' | awk '{print $2}'").trim();
                cmdBackup = "cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak_n2";
                cmdAplicar = "grep -qE '^#*MaxAuthTries' /etc/ssh/sshd_config && sed -i 's/^#*MaxAuthTries.*/MaxAuthTries " + novoValor + "/' /etc/ssh/sshd_config || echo 'MaxAuthTries " + novoValor + "' >> /etc/ssh/sshd_config";
                cmdValidar = "sshd -t"; // Valida a sintaxe antes de reiniciar o SSH!
                cmdRollback = "cp /etc/ssh/sshd_config.bak_n2 /etc/ssh/sshd_config";
                break;

            default:
                throw new IllegalArgumentException("Parâmetro de configuração não suportado.");
        }

        String scriptExecucao = String.format(
                "echo '%s' | sudo -S bash -c \"%s && %s && (%s || (%s && exit 1))\"",
                sudoPass, cmdBackup, cmdAplicar, cmdValidar, cmdRollback
        );

        String saida = sshService.executeCommand(scriptExecucao);
        if (saida.contains("Erro SSH")) {
            auditoriaService.registrar("CONFIG:" + servico, "ALTERAR_" + parametro.toUpperCase(), "FALHA", "Falha na validação/aplicação (rollback acionado): " + saida);
            return "Falha ao aplicar configuração (arquivo original preservado): " + saida;
        }

        debianAgentService.controlarServico(servico, "restart");

        ConfiguracaoVersao versao = new ConfiguracaoVersao();
        versao.setServico(servico);
        versao.setParametro(parametro);
        versao.setValorAnterior(valorAnterior);
        versao.setValorNovo(String.valueOf(novoValor));
        versao.setAplicadoPor(usuarioAtual);
        versaoRepository.save(versao);

        auditoriaService.registrar(
                "CONFIG:" + servico,
                "ALTERAR_" + parametro.toUpperCase(),
                "SUCESSO",
                String.format("Diff: %s -> %s", valorAnterior, novoValor)
        );

        return String.format("Configuração '%s' de %s atualizada de %s para %d e serviço reiniciado com sucesso!", parametro, servico, valorAnterior, novoValor);
    }

    public String restaurarUltimaVersao(Long versaoId) {
        ConfiguracaoVersao v = versaoRepository.findById(versaoId)
                .orElseThrow(() -> new IllegalArgumentException("Versão não encontrada."));

        if (v.isRevertida()) {
            return "Esta alteração já foi revertida anteriormente.";
        }

        int valorAntigo = Integer.parseInt(v.getValorAnterior().replaceAll("[^0-9]", ""));
        String resultado = alterarConfiguracao(v.getServico(), v.getParametro(), valorAntigo);

        v.setRevertida(true);
        versaoRepository.save(v);

        auditoriaService.registrar("CONFIG:" + v.getServico(), "RESTAURAR_VERSAO", "SUCESSO", "Revertido para " + valorAntigo);
        return "Restauração concluída: " + resultado;
    }
}