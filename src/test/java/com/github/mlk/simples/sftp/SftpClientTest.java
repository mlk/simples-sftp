package com.github.mlk.simples.sftp;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.mlk.junit.rules.SftpRule;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;
import net.schmizz.keepalive.KeepAlive;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.SecurityUtils;
import net.schmizz.sshj.connection.Connection;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.FileMode.Type;
import net.schmizz.sshj.sftp.PathComponents;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.xfer.FileSystemFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class SftpClientTest {
  private TemporaryFolder sftpHome = new TemporaryFolder();
  private SftpRule sftpRule = new SftpRule(sftpHome::getRoot);

  @Rule
  public RuleChain chain = RuleChain.outerRule(sftpHome).around(sftpRule);
  @Rule
  public TemporaryFolder local = new TemporaryFolder();


  @Test
  public void uploadShouldUploadFilesToSftpServer()
      throws IOException, NoSuchProviderException, NoSuchAlgorithmException {
    File file = local.newFile("transactions.csv");
    Files.write("this is a file", file, Charset.forName("UTF-8"));

    SftpClient underTest = new SftpClient("localhost", sftpRule.getPort(),
        "ignored", SecurityUtils.getFingerprint(sftpRule.getHostsPublicKey()),
        KeyPairGenerator.getInstance("DSA", "SUN").generateKeyPair());

    underTest.write(file, "/test.txt");

    File[] files = sftpHome.getRoot().listFiles();
    assertThat(files.length).isEqualTo(1);
    assertThat(Files.readFirstLine(files[0], Charset.forName("UTF-8"))).isEqualTo("this is a file");
  }

  @Test
  public void canConnectWhenHostVerificationIsOff()
      throws IOException, NoSuchProviderException, NoSuchAlgorithmException {
    File file = local.newFile("transactions.csv");
    Files.write("this is a file", file, Charset.forName("UTF-8"));

    SftpClient underTest = new SftpClient("localhost", sftpRule.getPort(),
        "ignored", "OFF", KeyPairGenerator.getInstance("DSA", "SUN").generateKeyPair());

    underTest.write(file, "/test.txt");

    assertThat(sftpHome.getRoot().listFiles().length).isEqualTo(1);
  }

  @Test
  public void givenFilesWhichMatchTheDownloadRulesThenDownloadTheFiles()
      throws IOException, NoSuchProviderException, NoSuchAlgorithmException {
    File file = sftpHome.newFile("BALANCE.DAT");
    Files.write("this is a file", file, Charset.forName("UTF-8"));

    SftpClient underTest = new SftpClient("localhost", sftpRule.getPort(),
        "ignored", SecurityUtils.getFingerprint(sftpRule.getHostsPublicKey()),
        KeyPairGenerator.getInstance("DSA", "SUN").generateKeyPair());

    List<File> files = underTest.download("/", local.getRoot(), (x) -> x);

    assertThat(files.size()).isEqualTo(1);
    assertThat(Files.readFirstLine(files.get(0), Charset.forName("UTF-8"))).isEqualTo("this is a file");
  }


  @Test
  public void givenFilesWhichDoNotMatchFileFormatThenSkip()
      throws IOException, NoSuchProviderException, NoSuchAlgorithmException {
    File file = sftpHome.newFile("BALANCE.DAT");
    Files.write("this is a file", file, Charset.forName("UTF-8"));

    SftpClient underTest = new SftpClient("localhost", sftpRule.getPort(),
        "ignored", SecurityUtils.getFingerprint(sftpRule.getHostsPublicKey()),
        KeyPairGenerator.getInstance("DSA", "SUN").generateKeyPair());

    List<File> files = underTest.download("/", local.getRoot(), (x) -> emptyList());

    assertThat(files.size()).isEqualTo(0);
  }

  @Test
  public void givenFoldersInSftpThenSkipTheFolders()
      throws IOException, NoSuchProviderException, NoSuchAlgorithmException {

    sftpHome.newFolder("FOLDER");

    SftpClient underTest = new SftpClient("localhost", sftpRule.getPort(),
        "ignored", SecurityUtils.getFingerprint(sftpRule.getHostsPublicKey()),
        KeyPairGenerator.getInstance("DSA", "SUN").generateKeyPair());

    List<File> files = underTest.download("/", local.getRoot(), (x) -> x);

    assertThat(files.size()).isEqualTo(0);
  }

  @Test
  public void givenAnExceptionThenDeleteLocalFiles()
      throws IOException, NoSuchProviderException, NoSuchAlgorithmException {

    /*
    int mask, long size, int uid, int gid, FileMode mode, long atime, long mtime,
                          Map<String, String> ext
     */

    FileAttributes regularFile = new FileAttributes(0, 0, 0, 0, new FileMode(Type.REGULAR.toMask()), 0, 0, emptyMap());

    RemoteResourceInfo passingRemoteFile = new RemoteResourceInfo(
        new PathComponents("/", "test.data", "/"), regularFile);
    RemoteResourceInfo failingRemoteFile = new RemoteResourceInfo(
        new PathComponents("/", "fail.data", "/"), regularFile);

    IOException expected = new IOException("FAKE EXCEPTION");

    File file = new File(local.getRoot(), "test.data");
    Files.write(new byte[] {0}, file);


    SSHClient sshClient = mock(SSHClient.class);
    SFTPClient sftpClient = mock(SFTPClient.class);
    Connection connection = mock(Connection.class);
    KeepAlive keepAlive = mock(KeepAlive.class);
    when(sshClient.newSFTPClient()).thenReturn(sftpClient);
    when(sshClient.getConnection()).thenReturn(connection);
    when(connection.getKeepAlive()).thenReturn(keepAlive);
    when(sftpClient.ls(any())).thenReturn(asList(
        passingRemoteFile, failingRemoteFile
        )
    );

    doThrow(expected).when(sftpClient)
        .get(eq(failingRemoteFile.getPath()), any(FileSystemFile.class));

    SftpClient underTest = new SftpClient("localhost", sftpRule.getPort(),
        "ignored", SecurityUtils.getFingerprint(sftpRule.getHostsPublicKey()),
        KeyPairGenerator.getInstance("DSA", "SUN").generateKeyPair(), () -> sshClient);
    IOException actual = null;
    try {
      underTest.downloadAll("/", local.getRoot());
    } catch(IOException e) {
      actual = e;
    }

    assertThat(actual).isEqualTo(expected);
    assertThat(file.exists()).isFalse();
  }


}