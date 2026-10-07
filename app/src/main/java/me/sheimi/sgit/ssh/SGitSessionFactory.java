package me.sheimi.sgit.ssh;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig.Host;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import me.sheimi.android.utils.SecurePrefsHelper;
import me.sheimi.sgit.MGitApplication;
import timber.log.Timber;

/**
 * Custom config for Jsch, including using user-provided private keys
 */
public class SGitSessionFactory extends JschConfigSessionFactory {

    /**
     * Asset holding the only host keys we trust (GitHub's published keys).
     * Connections to any other host, or to a host presenting a different key, are rejected.
     */
    static final String KNOWN_HOSTS_ASSET = "github_known_hosts";

    @Override
    protected void configure(Host host, Session session) {
        session.setConfig("StrictHostKeyChecking", "yes");
        session.setConfig("PreferredAuthentications", "publickey");
        // passphrases are supplied up front in createDefaultJSch, so never prompt
        session.setUserInfo(NonInteractiveUserInfo.INSTANCE);
    }

    @Override
    protected JSch createDefaultJSch(FS fs) throws JSchException {
        JSch jsch = new JSch();
        try (InputStream knownHosts = MGitApplication.getContext().getAssets().open(KNOWN_HOSTS_ASSET)) {
            jsch.setKnownHosts(knownHosts);
        } catch (IOException e) {
            throw new JSchException("Could not load pinned host keys", e);
        }
        PrivateKeyUtils.migratePrivateKeys();
        SecurePrefsHelper secrets = MGitApplication.getContext().getSecurePrefsHelper();
        File[] keys = PrivateKeyUtils.getPrivateKeyFolder().listFiles();
        if (keys == null) {
            return jsch;
        }
        for (File key : keys) {
            String passphrase = secrets == null ? null : secrets.get(key.getName());
            try {
                jsch.addIdentity(key.getAbsolutePath(),
                    passphrase == null ? null : passphrase.getBytes(StandardCharsets.UTF_8));
            } catch (JSchException e) {
                // e.g. wrong stored passphrase: skip this key rather than failing every connection
                Timber.w(e, "skipping unusable private key %s", key.getName());
            }
        }
        return jsch;
    }

    /**
     * Declines every prompt: unknown host keys are rejected and encrypted keys without a stored
     * passphrase are skipped.
     */
    private static class NonInteractiveUserInfo implements UserInfo {
        static final NonInteractiveUserInfo INSTANCE = new NonInteractiveUserInfo();

        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            return false;
        }

        @Override
        public void showMessage(String message) {
            Timber.i("ssh: %s", message);
        }
    }
}
