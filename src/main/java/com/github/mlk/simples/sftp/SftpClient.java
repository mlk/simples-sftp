package com.github.mlk.simples.sftp;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper;
import net.schmizz.sshj.xfer.FileSystemFile;

@Slf4j
@AllArgsConstructor
public class SftpClient {
  private final String host;
  private final int port;
  private final String username;
  private final String hostFingerPrint;
  private final KeyPair privateKeyPair;

  private void doSftp(ThrowingConsumer<SFTPClient> doThis) throws IOException {
    final SSHClient ssh = new SSHClient();

    if(hostFingerPrint.equals("OFF")) {
      log.warn("HOST VERIFICATION IS OFF - TURN ON IN LIVE!");
      ssh.addHostKeyVerifier(new PromiscuousVerifier());
    } else {
      ssh.addHostKeyVerifier(hostFingerPrint);
    }

    ssh.connect(host, port);
    try {
      ssh.authPublickey(username, new KeyPairWrapper(privateKeyPair));

      try (SFTPClient sftp = ssh.newSFTPClient()) {
        doThis.accept(sftp);
      }
    } finally {
      try {
        ssh.disconnect();
      } catch(IOException e) {
        log.warn("Exception while disconnecting", e);
      }
    }

  }

  public void write(File file, String target) throws IOException {
    doSftp(sftp -> sftp.put(new FileSystemFile(file), target));
  }

  public List<File> download(String folder, File localStorage,
      Function<Collection<RemoteResourceInfo>, Collection<RemoteResourceInfo>> fileFilter) throws IOException {
    List<File> files = new ArrayList<>();

    doSftp(sftp -> {
      Collection<RemoteResourceInfo> remoteFiles = fileFilter.apply(sftp.ls(folder).stream()
          .filter(RemoteResourceInfo::isRegularFile)
          .collect(Collectors.toList()));

      for (RemoteResourceInfo x : remoteFiles) {
        File file = new File(localStorage, x.getName());

        sftp.get(x.getPath(), new FileSystemFile(file));
        files.add(file);
      }
    });

    return files;
  }

}

