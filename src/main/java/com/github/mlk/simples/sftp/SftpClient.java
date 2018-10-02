package com.github.mlk.simples.sftp;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper;
import net.schmizz.sshj.xfer.FileSystemFile;

/**
 * A really basic SFTP client.
 */
@Slf4j
public class SftpClient {

  private final String host;
  private final int port;
  private final String username;
  private final String hostFingerPrint;
  private final KeyPair privateKeyPair;
  private final Supplier<SSHClient> clientSupplier;

  public SftpClient(String host, int port, String username, String hostFingerPrint,
      final KeyPair privateKeyPair) {
    this(host, port, username, hostFingerPrint, privateKeyPair, SSHClient::new);
  }

  SftpClient(String host, int port, String username, String hostFingerPrint,
      final KeyPair privateKeyPair, Supplier<SSHClient> clientSupplier) {
    this.host = host;
    this.port = port;
    this.username = username;
    this.hostFingerPrint = hostFingerPrint;
    this.privateKeyPair = privateKeyPair;
    this.clientSupplier = clientSupplier;
  }

  /**
   * Does SFTP stuff then closes the connection
   *
   * NOTE: The calling application is responsible for clean up!
   * Close method in {@link SSHClient} automatically call disconnect in case of finally block from try-catch
   *
   * @param doThis Stuff to do
   * @throws IOException When stuff goes wrong
   */
  public void doSftp(ThrowingConsumer<SFTPClient> doThis) throws IOException {
    try (final SSHClient ssh = clientSupplier.get()) {

      if (hostFingerPrint.equals("OFF")) {
        log.warn("HOST VERIFICATION IS OFF - TURN ON IN LIVE!");
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
      } else {
        ssh.addHostKeyVerifier(hostFingerPrint);
      }

      ssh.connect(host, port);

      ssh.authPublickey(username, new KeyPairWrapper(privateKeyPair));

      try (SFTPClient sftp = ssh.newSFTPClient()) {
        doThis.accept(sftp);
      }
    }

  }

  /**
   * Uploads the file
   *
   * @param file The file to upload
   * @param target The file name of the target.
   * @throws IOException Any SFTP issue
   * @deprecated Use upload.
   */
  @Deprecated
  public void write(File file, String target) throws IOException {
    upload(file, target);
  }

  /**
   * Uploads the file
   *
   * @param file The file to upload
   * @param target The file name of the target.
   * @throws IOException Any SFTP issue
   */
  public void upload(File file, String target) throws IOException {
    doSftp(sftp -> {
      sftp.getFileTransfer().setPreserveAttributes(false);
      sftp.put(new FileSystemFile(file), target);
    });
  }

  /**
   * Lists the a directory and then downloads all the files not filtered out.
   *
   * @param folder The folder to list
   * @param localStorage The location of the local storage (a folder)
   * @param fileFilter A function that takes a list of files and returns a list of files you
   * actually want.
   * @return A list of local files after they have been downloaded
   */
  public List<File> download(String folder, File localStorage,
      Function<Collection<RemoteResourceInfo>, Collection<RemoteResourceInfo>> fileFilter)
      throws IOException {
    List<File> files = new ArrayList<>();

    try {
      doSftp(sftp -> {
        Collection<RemoteResourceInfo> remoteFiles = fileFilter.apply(sftp.ls(folder).stream()
            .filter(RemoteResourceInfo::isRegularFile)
            .collect(Collectors.toList()));
        for (RemoteResourceInfo x : remoteFiles) {
          File file = new File(localStorage, x.getName());
          System.out.println(file);

          sftp.get(x.getPath(), new FileSystemFile(file));
          files.add(file);
        }

      });

    } catch (IOException e) {
      files.forEach(File::delete);
      throw e;
    }
    return files;
  }

  /**
   * Download all files from the SFTP server
   *
   * @param folder The folder to download
   * @param localStorage The location of the local storage (a folder)
   * @return A list of local files after they have been downloaded
   */
  public List<File> downloadAll(String folder, File localStorage) throws IOException {
    return download(folder, localStorage, x -> x);
  }
}

