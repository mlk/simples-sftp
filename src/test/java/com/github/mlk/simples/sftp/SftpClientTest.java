package com.github.mlk.simples.sftp;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.mlk.junit.rules.SftpRule;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;
import net.schmizz.sshj.common.SecurityUtils;
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
}