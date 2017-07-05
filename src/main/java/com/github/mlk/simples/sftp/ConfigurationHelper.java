package com.github.mlk.simples.sftp;

import com.github.fommil.ssh.SshRsaCrypto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import net.schmizz.sshj.common.SecurityUtils;

public class ConfigurationHelper {

  private ConfigurationHelper() {}

  /** This takes a host fingerprint in either as a public RSA key (ssh-rsa ABC...) or as a
   * fingerprint (xx:xx:..) and returns it in the format the library can understand (the fingerprint)
   *
   * @param hostFingerPrint A string of either an RSA or fingerprint (or OFF)
   * @return A fingerprint or OFF
   */
  public static String loadHostFingerPrint(String hostFingerPrint) {
    SshRsaCrypto rsa = new SshRsaCrypto();

    try {
      if (hostFingerPrint.contains(":")) {
        return hostFingerPrint;
      }
      if (hostFingerPrint.startsWith("ssh-rsa")) {
        return SecurityUtils
            .getFingerprint(rsa.readPublicKey(rsa.slurpPublicKey(hostFingerPrint)));
      }
    } catch (IOException | GeneralSecurityException e) {
      throw new RuntimeException(e);
    }

    return "OFF";
  }

  /** Loads a public/private key pair from an RSA public/private key pair set of files.
   * Pass in the PRIVATE key and have the public key named the same as the private key plus ".pub"
   *
   * @param privateKeyFile A private key on the local computer.
   * @return A key pair.
   */
  public static KeyPair loadPrivateKey(String privateKeyFile) {
    SshRsaCrypto rsa = new SshRsaCrypto();

    try {
      return new KeyPair(
          rsa.readPublicKey(rsa.slurpPublicKey(new String(
              Files.readAllBytes(Paths.get(privateKeyFile + ".pub")), "UTF-8"))),
          rsa.readPrivateKey(rsa.slurpPrivateKey(new String(Files.readAllBytes(Paths.get(privateKeyFile)), "UTF-8"))));
    } catch (IOException | GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }
}
